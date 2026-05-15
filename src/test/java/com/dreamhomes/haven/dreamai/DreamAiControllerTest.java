package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.dreamai.chat.DreamAiChatService;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnResponse;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.DreamAiTurnKind;
import com.dreamhomes.haven.dreamai.turn.TurnBlock;
import com.dreamhomes.haven.dreamai.turn.TurnMeta;
import com.dreamhomes.haven.support.JwtCookieTestStubConfiguration;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DreamAiController.class)
@Import({SecurityConfig.class, JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "haven.dream-ai.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class DreamAiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DreamAiChatService dreamAiChatService;

    @MockBean
    JwtService jwtService;

    @MockBean
    JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    UserCredentialsService userCredentialsService;

    @Test
    void suggestions_returnsRunTurnEnvelope() throws Exception {
        AssistantTurnV1 turn = new AssistantTurnV1(
                DreamAiTurnKind.reply,
                null,
                List.of(TurnBlock.listings(List.of(10L))),
                new TurnMeta(null, null, false, "anthropic", "trace-json", null, null, null));
        when(dreamAiChatService.runTurn(eq(7L), any()))
                .thenReturn(new DreamAiRunTurnResponse(100L, "trace-json", turn, List.of(10L)));

        mockMvc.perform(post("/api/dream-ai/suggestions")
                        .with(asPrincipal(7L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"3 bed lekki\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").value(100))
                .andExpect(jsonPath("$.traceId").value("trace-json"))
                .andExpect(jsonPath("$.turn.kind").value("reply"))
                .andExpect(jsonPath("$.listingIds[0]").value(10));
    }

    @Test
    void suggestions_acceptsUserChoiceInsteadOfPrompt() throws Exception {
        AssistantTurnV1 turn = new AssistantTurnV1(DreamAiTurnKind.clarify, "Pick one", List.of(), TurnMeta.empty());
        when(dreamAiChatService.runTurn(eq(8L), any()))
                .thenReturn(new DreamAiRunTurnResponse(101L, "t", turn, List.of()));

        mockMvc.perform(post("/api/dream-ai/suggestions")
                        .with(asPrincipal(8L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userChoice\":{\"chipId\":\"budget\",\"sendText\":\"under 5m\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn.kind").value("clarify"));
    }

    private static RequestPostProcessor asPrincipal(long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "u@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
