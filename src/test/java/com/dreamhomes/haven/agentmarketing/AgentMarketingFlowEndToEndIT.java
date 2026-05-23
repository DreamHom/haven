package com.dreamhomes.haven.agentmarketing;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent marketing gallery: multipart upload, authenticated list, public profile exposure,
 * and delete clearing the public gallery.
 */
@AutoConfigureMockMvc
class AgentMarketingFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void agentUploadsMarketingImageAndPublicProfileListsItThenDeleteClears() throws Exception {
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        String bearer = jwtTestSupport.bearerFor(agent);

        mockMvc.perform(multipart("/api/me/agent-marketing")
                        .file(new MockMultipartFile("file", "showcase.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03, 0x04}))
                        .param("caption", "Waterfront lobby")
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", containsString("https://media.dreamhomes.com/agents/" + agent.getId() + "/gallery/")))
                .andExpect(jsonPath("$.caption").value("Waterfront lobby"));

        MvcResult listResult = mockMvc.perform(get("/api/me/agent-marketing").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].caption").value("Waterfront lobby"))
                .andReturn();

        JsonNode arr = objectMapper.readTree(listResult.getResponse().getContentAsString());
        long mediaId = arr.get(0).get("id").asLong();

        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentMarketingGallery.length()").value(1))
                .andExpect(jsonPath("$.agentMarketingGallery[0].caption").value("Waterfront lobby"))
                .andExpect(jsonPath("$.agentMarketingGallery[0].url", containsString("/agents/" + agent.getId() + "/gallery/")));

        mockMvc.perform(delete("/api/me/agent-marketing/" + mediaId).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentMarketingGallery.length()").value(0));
    }

    @Test
    void reorderGalleryUpdatesDisplayOrder() throws Exception {
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        String bearer = jwtTestSupport.bearerFor(agent);

        MvcResult first = mockMvc.perform(multipart("/api/me/agent-marketing")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{0x01}))
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(multipart("/api/me/agent-marketing")
                        .file(new MockMultipartFile("file", "b.png", "image/png", new byte[]{0x02}))
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn();
        long idA = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        long idB = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/me/agent-marketing/order")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\": [" + idB + ", " + idA + "]}"))
                .andExpect(status().isNoContent());

        MvcResult list = mockMvc.perform(get("/api/me/agent-marketing").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(list.getResponse().getContentAsString());
        assertThat(arr.get(0).get("id").asLong()).isEqualTo(idB);
        assertThat(arr.get(1).get("id").asLong()).isEqualTo(idA);
    }

    @Test
    void rejectsNonImageContentType() throws Exception {
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        String bearer = jwtTestSupport.bearerFor(agent);

        mockMvc.perform(multipart("/api/me/agent-marketing")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{0x25, 0x50}))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }
}
