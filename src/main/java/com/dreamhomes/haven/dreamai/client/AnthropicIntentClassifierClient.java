package com.dreamhomes.haven.dreamai.client;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.exception.DreamAiUpstreamException;
import com.dreamhomes.haven.dreamai.intent.Intent;
import com.dreamhomes.haven.dreamai.intent.IntentClassification;
import com.dreamhomes.haven.dreamai.intent.IntentClassifierContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Item 26 sub-task D — tiny Claude Haiku call that classifies a user prompt into one of
 * {@link Intent} buckets so {@code DreamAiTurnOrchestrator} can route smartly without
 * the regex-soup heuristics.
 *
 * <p>Sister to {@link AnthropicListingSearchClient} / {@link AnthropicListingCompareClient}
 * — same auth + transport, much smaller payload (~50 tokens in, ~20 tokens out → about
 * $0.001 per call on Haiku). The orchestrator catches every exception this client can
 * throw and falls back to legacy regex routing, so an upstream outage degrades the UX
 * but never blocks the turn.</p>
 *
 * <p>Strict output contract: {@code {"intent":"SEARCH|COMPARE_RECENT|CLARIFY|EMPTY",
 * "confidence":<0..1>}} — anything else surfaces as a {@link DreamAiUpstreamException}
 * that the orchestrator treats as "try the fallback".</p>
 */
@Slf4j
@RequiredArgsConstructor
public class AnthropicIntentClassifierClient {

    private static final String SYSTEM = """
            You are an intent router for a Nigerian real-estate search assistant. Classify
            the user's prompt into exactly one of these intents:

            - "SEARCH" — they want to find listings (e.g. "3-bed in Lekki under 5m",
              "show me apartments near a school", "any rentals with parking?").
            - "COMPARE_RECENT" — they want to compare or pick between listings that were
              already shown in the conversation (e.g. "which is best for me?",
              "should I go with the first one?"). Only valid when "hasPriorListings"
              in the context is true. NEVER pick COMPARE_RECENT when hasPriorListings is false.
            - "CLARIFY" — the prompt is too vague to act on (e.g. "hi", "help", "houses",
              two-character noise, gibberish). The chat needs more constraints to search.
            - "EMPTY" — the prompt has no actionable content at all (whitespace, single
              character, pure punctuation).

            Respond with EXACTLY ONE JSON object and no other characters before or after it:

            {"intent": "<one of SEARCH|COMPARE_RECENT|CLARIFY|EMPTY>", "confidence": <0.0-1.0>}

            Rules:
            - The "confidence" field is your own estimate of how sure you are (0.5 = coin flip).
            - Do NOT add any commentary, markdown fences, or extra fields.
            - If the prompt looks like a search question with a constraint (number, location,
              price), prefer SEARCH over CLARIFY even if it is short.
            - When in doubt, pick CLARIFY rather than guessing SEARCH.
            """;

    private final RestClient restClient;
    private final DreamAiAnthropicProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Calls Anthropic Messages with a tight max_tokens budget. The caller (the
     * orchestrator) is expected to swallow exceptions and fall back to regex routing
     * — we surface a typed exception rather than returning a sentinel so the calling
     * code's fallback intent is unambiguous in the source.
     */
    public IntentClassification classifyIntent(String prompt, IntentClassifierContext context) {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException(
                    "AnthropicIntentClassifierClient must not be called without an API key");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        // Tight cap — the response is a single JSON object with two fields, so 64
        // tokens leaves plenty of room without paying for a Haiku-sized completion.
        root.put("max_tokens", 64);
        root.put("system", SYSTEM);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", prompt);
        payload.put("hasCompareIds", context.hasCompareIds());
        payload.put("hasPriorListings", context.hasPriorListings());
        userMsg.put("content", payload.toString());

        String body;
        try {
            body = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", properties.getApiKey())
                    .body(root.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new DreamAiUpstreamException(
                                "Intent classifier returned HTTP " + res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (RestClientException ex) {
            log.warn("Anthropic intent-classifier request failed: {}", ex.toString());
            throw new DreamAiUpstreamException("Intent classifier is temporarily unavailable");
        }

        String text = stripOptionalMarkdownFence(extractAssistantText(body));
        return parseAndValidate(text);
    }

    private String extractAssistantText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                throw new DreamAiUpstreamException(
                        "Intent classifier returned an unexpected response shape");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText(""));
                }
            }
            String joined = sb.toString().trim();
            if (joined.isEmpty()) {
                throw new DreamAiUpstreamException("Intent classifier returned no text content");
            }
            return joined;
        } catch (DreamAiUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse Anthropic intent-classifier envelope", ex);
            throw new DreamAiUpstreamException("Intent classifier returned an unreadable response");
        }
    }

    private static String stripOptionalMarkdownFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            int fence = t.lastIndexOf("```");
            if (fence >= 0) {
                t = t.substring(0, fence);
            }
        }
        return t.trim();
    }

    private IntentClassification parseAndValidate(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            String intentText = root.path("intent").asText("").trim();
            if (intentText.isEmpty()) {
                throw new DreamAiUpstreamException(
                        "Intent classifier omitted the intent field");
            }
            Intent intent;
            try {
                intent = Intent.valueOf(intentText.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new DreamAiUpstreamException(
                        "Intent classifier returned an unknown intent: " + intentText);
            }
            double confidence = root.path("confidence").asDouble(0.5);
            return new IntentClassification(intent, confidence);
        } catch (DreamAiUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse intent classifier JSON: {}",
                    text.length() > 200 ? text.substring(0, 200) + "…" : text, ex);
            throw new DreamAiUpstreamException("Intent classifier did not return valid JSON");
        }
    }
}
