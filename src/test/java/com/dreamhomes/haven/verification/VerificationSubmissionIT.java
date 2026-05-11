package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;

/**
 * End-to-end submission across HTTP, Spring Security, JPA, and Postgres. Covers the
 * happy path for an owner self-submitting OWNER_IDENTITY plus the
 * partial-unique-index guard rejecting a duplicate pending submission.
 */
@AutoConfigureMockMvc
class VerificationSubmissionIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired VerificationRepository verificationRepository;


    @Test
    void ownerPostsIdentityVerificationLandsAsPendingRowOwnedBySubmitter() throws Exception {
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
                .andExpect(jsonPath("$.type").value("OWNER_IDENTITY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.submitterUserId").value(owner.getId()))
                .andExpect(jsonPath("$.targetUserId").value(owner.getId()))
                .andExpect(jsonPath("$.targetPropertyId").doesNotExist());

        List<Verification> rows = verificationRepository.findAll();
        assertThat(rows).hasSize(1);
        Verification row = rows.get(0);
        assertThat(row.getType()).isEqualTo(VerificationType.OWNER_IDENTITY);
        assertThat(row.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(row.getSubmitterUserId()).isEqualTo(owner.getId());
        assertThat(row.getTargetUserId()).isEqualTo(owner.getId());
        assertThat(row.getDocumentRefs()).contains("\"AB1234567\"");
    }

    @Test
    void duplicatePendingSubmissionByTheSameOwnerReturns409() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        String body = """
                {
                  "type": "OWNER_IDENTITY",
                  "documentRefs": { "kind": "NIN", "ref": "AB1234567" }
                }
                """;

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerPostsPropertyDocsForOwnPropertyTargetsThePropertyRow() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Property property = propertyRepository.save(Property.builder()
                .ownerId(owner.getId()).type(PropertyType.HOUSE)
                .address("Plot 5").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PROPERTY_DOCUMENTS",
                                  "propertyId": %d,
                                  "documentRefs": { "kind": "C_OF_O", "ref": "DOC-9182" }
                                }
                                """.formatted(property.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPropertyId").value(property.getId()))
                .andExpect(jsonPath("$.targetUserId").doesNotExist());
    }

    @Test
    void ownerCannotSubmitPropertyDocsForSomeoneElsesProperty() throws Exception {
        User ownerA = jwtTestSupport.persistUser(Role.OWNER);
        User ownerB = jwtTestSupport.persistUser(Role.OWNER);
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerA.getId()).type(PropertyType.HOUSE)
                .address("Plot 5").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PROPERTY_DOCUMENTS",
                                  "propertyId": %d,
                                  "documentRefs": { "kind": "C_OF_O", "ref": "X" }
                                }
                                """.formatted(property.getId())))
                .andExpect(status().isForbidden());

        assertThat(verificationRepository.findAll()).isEmpty();
    }
}
