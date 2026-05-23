package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.dreamai.chat.DreamAiChatService;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnResponse;
import com.dreamhomes.haven.dreamai.moderation.DreamAiModerationBlockedException;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.DreamAiTurnKind;
import com.dreamhomes.haven.dreamai.turn.TurnBlock;
import com.dreamhomes.haven.dreamai.turn.TurnMeta;
import com.dreamhomes.haven.support.JwtCookieTestStubConfiguration;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DreamAiTurnStreamController.class)
@Import({SecurityConfig.class, JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "haven.dream-ai.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience",
        "haven.errors.type-base=https://errors.test/"
})
class DreamAiTurnStreamControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    DreamAiChatService dreamAiChatService;

    @MockBean
    JwtService jwtService;

    @MockBean
    JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    UserCredentialsService userCredentialsService;

    @Test
    void stream_emitsTraceDeltaAndFinalInOrder() throws Exception {
        String longMd = "x".repeat(200);
        AssistantTurnV1 turn = new AssistantTurnV1(
                DreamAiTurnKind.no_results,
                longMd,
                List.of(),
                TurnMeta.empty());
        DreamAiRunTurnResponse body = new DreamAiRunTurnResponse(9L, "trace-sse-1", turn, List.of());
        when(dreamAiChatService.runTurn(eq((Long) 44L), any())).thenReturn(body);

        MvcResult started = mockMvc.perform(post("/api/dream-ai/turns/stream")
                        .with(asPrincipal(44L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"anything long enough\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult done = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
                .andReturn();

        List<DreamAiSseTestSupport.SseEvent> events = DreamAiSseTestSupport.parse(done.getResponse().getContentAsString());
        assertThat(events.stream().map(DreamAiSseTestSupport.SseEvent::name).toList())
                .containsExactly("trace", "delta", "delta", "delta", "final");

        JsonNode trace = objectMapper.readTree(events.getFirst().dataJson());
        assertThat(trace.get("traceId").asText()).isEqualTo("trace-sse-1");

        JsonNode fin = objectMapper.readTree(events.getLast().dataJson());
        assertThat(fin.get("chatId").asLong()).isEqualTo(9L);
        assertThat(fin.get("traceId").asText()).isEqualTo("trace-sse-1");
        assertThat(fin.get("turn").get("kind").asText()).isEqualTo("no_results");
    }

    @Test
    void stream_moderation_emitsProblemEventWith422InBody() throws Exception {
        when(dreamAiChatService.runTurn(eq((Long) 2L), any()))
                .thenThrow(new DreamAiModerationBlockedException("blocked for test"));

        MvcResult started = mockMvc.perform(post("/api/dream-ai/turns/stream")
                        .with(asPrincipal(2L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"bad\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult done = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        List<DreamAiSseTestSupport.SseEvent> events = DreamAiSseTestSupport.parse(done.getResponse().getContentAsString());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().name()).isEqualTo("problem");
        JsonNode prob = objectMapper.readTree(events.getFirst().dataJson());
        assertThat(prob.get("status").asInt()).isEqualTo(422);
        assertThat(prob.get("type").asText()).isEqualTo("https://errors.test/moderation-blocked");
    }

    @Test
    void stream_replyWithoutMarkdown_hasNoDeltaEvents() throws Exception {
        AssistantTurnV1 turn = new AssistantTurnV1(
                DreamAiTurnKind.reply,
                null,
                List.of(TurnBlock.listings(List.of(1L, 2L))),
                new TurnMeta(null, null, true, "stub", "t2", null, null, null));
        DreamAiRunTurnResponse body = new DreamAiRunTurnResponse(3L, "t2", turn, List.of(1L, 2L));
        when(dreamAiChatService.runTurn(eq((Long) 1L), any())).thenReturn(body);

        MvcResult started = mockMvc.perform(post("/api/dream-ai/turns/stream")
                        .with(asPrincipal(1L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"two bedroom yaba\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult done = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn();
        List<DreamAiSseTestSupport.SseEvent> events = DreamAiSseTestSupport.parse(done.getResponse().getContentAsString());
        assertThat(events.stream().map(DreamAiSseTestSupport.SseEvent::name).toList())
                .containsExactly("trace", "final");
    }

    @Test
    void chunkText_splitsByMaxLen() {
        assertThat(DreamAiTurnStreamController.chunkText("abcdef", 2)).containsExactly("ab", "cd", "ef");
        assertThat(DreamAiTurnStreamController.chunkText("", 10)).isEmpty();
    }

    private static RequestPostProcessor asPrincipal(long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "u@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
