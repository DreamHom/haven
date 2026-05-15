package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack Dream AI persistence + thread read + idempotency (JSON POST).
 * <p>SSE contract and event ordering are covered by {@link DreamAiTurnStreamControllerTest} — MockMvc
 * {@code asyncDispatch} does not replay {@code Authorization} reliably against the security filter chain
 * in this setup, so end-to-end stream tests use WebClient/RestClient in a future profile if needed.</p>
 */
@AutoConfigureMockMvc
class DreamAiChatFlowIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void suggestionsPersistChatAndFollowUpUsesSameThread() throws Exception {
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        String bearer = jwtTestSupport.bearerFor(applicant);

        MvcResult first = mockMvc.perform(post("/api/dream-ai/suggestions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"two bedroom in Yaba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.turn.kind").exists())
                .andExpect(jsonPath("$.listingIds").isArray())
                .andReturn();

        String body = first.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        long chatId = node.get("chatId").asLong();
        assertThat(chatId).isPositive();

        mockMvc.perform(post("/api/dream-ai/suggestions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"under 800k\",\"chatId\":" + chatId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").value(chatId));

        mockMvc.perform(get("/api/dream-ai/chats").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(chatId));

        mockMvc.perform(get("/api/dream-ai/chats/" + chatId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(4)));

        mockMvc.perform(get("/api/dream-ai/chats/999999").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientMessageId_replaySkipsDuplicatePersistence() throws Exception {
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        String bearer = jwtTestSupport.bearerFor(applicant);
        String idem = "idem-it-" + System.nanoTime();

        MvcResult first = mockMvc.perform(post("/api/dream-ai/suggestions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"long enough prompt yaba\",\"clientMessageId\":\"" + idem + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        long chatId = firstJson.get("chatId").asLong();
        String traceId = firstJson.get("traceId").asText();

        mockMvc.perform(post("/api/dream-ai/suggestions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"ignored\",\"chatId\":" + chatId + ",\"clientMessageId\":\"" + idem + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").value(chatId))
                .andExpect(jsonPath("$.traceId").value(traceId));

        mockMvc.perform(get("/api/dream-ai/chats/" + chatId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)));
    }
}
