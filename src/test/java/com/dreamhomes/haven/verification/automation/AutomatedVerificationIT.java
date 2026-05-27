package com.dreamhomes.haven.verification.automation;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.model.Verification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that Item 20's automated provider runs and persists a row during
 * the verification submit flow, and that the response surfaces the result so Vista
 * can render the "MOCKED" framing.
 */
@AutoConfigureMockMvc
class AutomatedVerificationIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired VerificationRepository verificationRepository;
    @Autowired VerificationAutomationResultRepository automationResultRepository;

    @Test
    void submittingOwnerIdentityRunsMockProviderPersistsRowAndSurfacesItOnResponse() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "AB1234567" }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.automatedChecks").exists())
                .andExpect(jsonPath("$.automatedChecks[0].providerName").value("MOCK"))
                .andExpect(jsonPath("$.automatedChecks[0].status").value("PASSED"))
                .andExpect(jsonPath("$.automatedChecks[0].score").value(0.95))
                .andExpect(jsonPath("$.automatedChecks[0].checkType").value("OWNER_IDENTITY"));

        List<Verification> verifications = verificationRepository.findAll();
        assertThat(verifications).hasSize(1);
        Verification saved = verifications.get(0);

        List<VerificationAutomationResult> automated = automationResultRepository
                .findByVerificationIdOrderByRunAtAsc(saved.getId());
        assertThat(automated).hasSize(1);
        VerificationAutomationResult row = automated.get(0);
        assertThat(row.getProviderName()).isEqualTo("MOCK");
        assertThat(row.getCheckType()).isEqualTo("OWNER_IDENTITY");
        assertThat(row.getStatus()).isEqualTo("PASSED");
        assertThat(row.getScore()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(row.getExtractedFields()).contains("\"nin\"").contains("\"nameMatch\"");
        // JSONB stores with whitespace-tolerant formatting; just assert provider name appears.
        assertThat(row.getRawResponse()).contains("MOCK");
    }
}
