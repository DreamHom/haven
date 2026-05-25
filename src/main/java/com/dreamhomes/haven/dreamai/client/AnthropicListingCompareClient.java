package com.dreamhomes.haven.dreamai.client;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.exception.DreamAiUpstreamException;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import com.dreamhomes.haven.dreamai.turn.PerListingNote;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Calls Anthropic's Messages API to compare 2–5 LIVE listings against a user's intent and
 * return structured pros/cons + a recommendation. Sister of
 * {@link AnthropicListingSearchClient} — same auth + transport plumbing, different system
 * prompt and a richer response schema.
 *
 * <p>The model returns one JSON object:</p>
 *
 * <pre>{@code
 * {
 *   "recommendedListingId": 12 | null,
 *   "summary": "<markdown explanation>",
 *   "perListing": [
 *     { "id": 9, "headline": "...", "pros": [...], "cons": [...], "bestFor": "..." },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <p>Validated downstream — unknown ids are dropped, the recommendation is forced to null
 * if it isn't in the validated set, and any per-listing entry whose id isn't valid is
 * filtered out.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class AnthropicListingCompareClient {

    private static final String SYSTEM = """
            You are a real-estate advisor for DreamHomes (Nigeria, prices in NGN unless noted).

            You receive a JSON object with two keys:
            - "userIntent": the user's stated need + the question they asked (e.g. "find me a 3-bed
              in Lekki under 2 million; which of these would suit a single mum with two kids?").
            - "listings": an array of listing objects to compare. Each listing has:
              id (number), type (RENT or SALE), price (number), currency (string),
              title, headline, description (may be truncated), address, bedrooms, bathrooms,
              geo (optional "lat,lng" string or null), priceNegotiable (boolean),
              petsAllowed (string or null), utilitiesNote (string or null),
              ownerVerified (boolean), propertyDocumentsVerified (boolean).

            Respond with EXACTLY ONE JSON object and no other characters before or after it:

            {
              "recommendedListingId": <id from input list, or null when no clear winner>,
              "summary": "<2-4 sentence markdown explanation of your recommendation OR why it's a tie>",
              "perListing": [
                {
                  "id": <listing id>,
                  "headline": "<one short sentence describing the listing's character>",
                  "pros": ["<concrete advantage>", "<another>"],
                  "cons": ["<honest drawback>", "<another>"],
                  "bestFor": "<one sentence describing the persona this listing fits best>"
                },
                ...
              ]
            }

            Rules:
            - Only use ids that appear in the provided listings array. Never invent ids.
            - Include exactly one perListing entry per input listing.
            - Pros and cons should be 1-4 items each, concrete and short (under ~80 chars).
            - bestFor is a one-line persona statement (e.g. "A young couple commuting to VI" or
              "A retiree who wants quiet streets and minimal stairs").
            - If the user mentions a constraint (budget, family situation, commute, pets), weight
              it heavily in your reasoning.
            - "ownerVerified" / "propertyDocumentsVerified" — when the user asks for "verified"
              owners or properties, prefer rows where the relevant field is true.
            - If listings are too similar to call, set recommendedListingId to null and explain
              the tradeoffs in summary so the user can decide.
            - Use Nigerian / Lagos context when relevant (mention specific neighbourhoods, schools,
              traffic, generators, light situation, security, etc.) where the listing data supports it.
            - Do not wrap the JSON in markdown fences. Do not add commentary outside the JSON.
            """;

    private final RestClient restClient;
    private final DreamAiAnthropicProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * @param userIntent  natural-language intent (caller-built; usually prior prompt + comparison question)
     * @param catalogJson JSON string for the "listings" array payload
     * @param validIds    ids the model is allowed to return (LIVE-checked)
     * @return structured reasoning; per-listing notes filtered to {@code validIds};
     *         {@code recommendedListingId} forced to null if model picked outside the valid set
     */
    public CompareReasoning compareListings(String userIntent, String catalogJson, Set<Long> validIds) {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("AnthropicListingCompareClient must not be called without an API key");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        // Compare needs more output room than rank — pros/cons/bestFor per listing × ~3 listings.
        root.put("max_tokens", Math.max(properties.getMaxOutputTokens(), 1500));
        root.put("system", SYSTEM);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("userIntent", userIntent);
        try {
            payload.set("listings", objectMapper.readTree(catalogJson));
        } catch (JsonProcessingException ex) {
            throw new DreamAiUpstreamException("Internal listing catalogue could not be encoded");
        }
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
                                "Listing compare assistant returned HTTP " + res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (RestClientException ex) {
            log.warn("Anthropic compare request failed: {}", ex.toString());
            throw new DreamAiUpstreamException("Listing compare assistant is temporarily unavailable");
        }

        String text = stripOptionalMarkdownFence(extractAssistantText(body));
        return parseAndValidate(text, validIds);
    }

    private String extractAssistantText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                throw new DreamAiUpstreamException("Listing compare assistant returned an unexpected response shape");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText(""));
                }
            }
            String joined = sb.toString().trim();
            if (joined.isEmpty()) {
                throw new DreamAiUpstreamException("Listing compare assistant returned no text content");
            }
            return joined;
        } catch (DreamAiUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse Anthropic compare response envelope", ex);
            throw new DreamAiUpstreamException("Listing compare assistant returned an unreadable response");
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

    /**
     * Parse the model's JSON object + validate id references. Drops any per-listing entry
     * whose id isn't in {@code validIds}; forces {@code recommendedListingId} to null when
     * the model picked an unknown id (defence in depth — the system prompt already says
     * "only use ids from input").
     */
    private CompareReasoning parseAndValidate(String text, Set<Long> validIds) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception ex) {
            log.debug("Failed to parse model JSON: {}", text.length() > 500 ? text.substring(0, 500) + "…" : text, ex);
            throw new DreamAiUpstreamException("Listing compare assistant did not return valid JSON");
        }
        if (!root.isObject()) {
            throw new DreamAiUpstreamException("Listing compare assistant did not return a JSON object");
        }

        Long recommended = null;
        JsonNode rec = root.path("recommendedListingId");
        if (rec.isIntegralNumber()) {
            long v = rec.longValue();
            if (validIds.contains(v)) {
                recommended = v;
            }
        }

        String summary = root.path("summary").asText("").trim();
        if (summary.isEmpty()) {
            summary = "Compared the listings — see per-listing notes below.";
        }

        List<PerListingNote> notes = new ArrayList<>();
        JsonNode arr = root.path("perListing");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                JsonNode idNode = n.path("id");
                if (!idNode.isIntegralNumber()) {
                    continue;
                }
                long id = idNode.longValue();
                if (!validIds.contains(id)) {
                    continue;
                }
                notes.add(new PerListingNote(
                        id,
                        truncate(n.path("headline").asText("").trim(), 120),
                        readStringArray(n.path("pros"), 80, 6),
                        readStringArray(n.path("cons"), 80, 6),
                        truncate(n.path("bestFor").asText("").trim(), 240)));
            }
        }
        return new CompareReasoning(recommended, summary, notes);
    }

    private static List<String> readStringArray(JsonNode node, int maxItemLength, int maxItems) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                continue;
            }
            String s = item.asText("").trim();
            if (s.isEmpty()) {
                continue;
            }
            out.add(truncate(s, maxItemLength));
            if (out.size() >= maxItems) {
                break;
            }
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
