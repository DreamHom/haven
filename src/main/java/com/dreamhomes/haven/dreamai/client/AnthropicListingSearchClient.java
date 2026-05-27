package com.dreamhomes.haven.dreamai.client;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.exception.DreamAiUpstreamException;
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
import java.util.stream.Collectors;

/**
 * Calls Anthropic's Messages API with a fixed system prompt and a user payload that embeds
 * the listing catalogue as JSON. Expects the model to answer with a single JSON object
 * {@code {"listingIds":[...]}} which we validate against the known id set.
 */
@Slf4j
@RequiredArgsConstructor
public class AnthropicListingSearchClient {

    private static final String SYSTEM = """
            You are a real-estate search assistant for DreamHomes (Nigeria, catalogue prices in NGN unless noted).

            You receive a JSON object with two keys:
            - "userQuery": the renter's or buyer's natural-language wish list.
            - "listings": an array of listing objects. Each listing has:
              id (number), type (RENT or SALE), price (number), currency (string),
              title, headline, description (may be truncated), address, bedrooms, bathrooms,
              geo (optional "lat,lng" string or null), priceNegotiable (boolean),
              petsAllowed (string or null), utilitiesNote (string or null),
              ownerVerified (boolean), propertyDocumentsVerified (boolean).

            Respond with exactly one JSON object and no other characters before or after it:
            {"listingIds":[<numbers>]}

            Rules:
            - Order ids from best match to weakest match.
            - Include at most 20 ids.
            - Only use ids that appear in the provided listings array. Never invent ids.
            - "ownerVerified" / "propertyDocumentsVerified" — when the user asks for "verified"
              owners or properties, prefer rows where the relevant field is true.
            - Location preference: if the user names a specific state, city, or
              neighbourhood (e.g. "in Lekki", "Surulere", "Ikeja"), rank exact address
              matches first. If no listing matches that exact neighbourhood, fall back
              to listings in the broader area (same city / same state) ranked by how
              close they appear to the requested place. Only return {"listingIds":[]}
              for location reasons if every listing is in a completely different region
              (e.g. user asked for Lagos, the catalogue is entirely Abuja). Sparse
              inventory in a single neighbourhood must not produce an empty response —
              expand outward and return the closest alternatives.
            - HARD price ceiling: if the user names an explicit upper price ("under ₦4m",
              "less than 2 million"), drop listings whose price exceeds it. Return empty
              before returning over-budget listings.
            - HARD bedroom count: if the user names a specific bedroom count ("3 bedroom",
              "2-bed"), drop listings whose bedrooms field doesn't match. Don't return a
              4-bed when the user asked for 3.
            - If nothing in the catalogue plausibly matches AFTER applying the hard
              constraints (price + bedroom) above, return {"listingIds":[]}.
            - Do not wrap the JSON in markdown fences. Do not add commentary outside the JSON.
            """;

    private final RestClient restClient;
    private final DreamAiAnthropicProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * @param userQuery   natural language query (already trimmed / length-capped by caller)
     * @param catalogJson JSON string for the "listings" array payload (caller-built)
     * @param validIds    ids the model is allowed to return
     */
    public List<Long> rankListingIds(String userQuery, String catalogJson, Set<Long> validIds) {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("AnthropicListingSearchClient must not be called without an API key");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("max_tokens", properties.getMaxOutputTokens());
        root.put("system", SYSTEM);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("userQuery", userQuery);
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
                                "Listing search assistant returned HTTP " + res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (RestClientException ex) {
            log.warn("Anthropic Messages request failed: {}", ex.toString());
            throw new DreamAiUpstreamException("Listing search assistant is temporarily unavailable");
        }

        String text = extractAssistantText(body);
        text = stripOptionalMarkdownFence(text);
        List<Long> raw = parseListingIdsJson(text);
        return sanitizeOrder(raw, validIds);
    }

    private String extractAssistantText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                throw new DreamAiUpstreamException("Listing search assistant returned an unexpected response shape");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText(""));
                }
            }
            String joined = sb.toString().trim();
            if (joined.isEmpty()) {
                throw new DreamAiUpstreamException("Listing search assistant returned no text content");
            }
            return joined;
        } catch (DreamAiUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse Anthropic response envelope", ex);
            throw new DreamAiUpstreamException("Listing search assistant returned an unreadable response");
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

    private List<Long> parseListingIdsJson(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode ids = root.path("listingIds");
            if (!ids.isArray()) {
                throw new DreamAiUpstreamException("Listing search assistant did not return a listingIds array");
            }
            List<Long> out = new ArrayList<>();
            for (JsonNode n : ids) {
                if (n.isIntegralNumber() || n.isLong()) {
                    out.add(n.longValue());
                } else if (n.isNumber()) {
                    out.add(n.longValue());
                }
            }
            return out;
        } catch (DreamAiUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse model JSON: {}", text.length() > 500 ? text.substring(0, 500) + "…" : text, ex);
            throw new DreamAiUpstreamException("Listing search assistant did not return valid JSON");
        }
    }

    /**
     * Drops unknown ids, dedupes while preserving first occurrence, caps at 20.
     */
    private static List<Long> sanitizeOrder(List<Long> raw, Set<Long> validIds) {
        return raw.stream()
                .filter(validIds::contains)
                .distinct()
                .limit(20)
                .collect(Collectors.toList());
    }
}
