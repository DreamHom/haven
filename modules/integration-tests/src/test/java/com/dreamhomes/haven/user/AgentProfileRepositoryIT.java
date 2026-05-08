package com.dreamhomes.haven.user;

import com.dreamhomes.haven.common.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AgentProfileRepositoryIT extends AbstractPostgresIT {

    @Autowired
    UserRepository userRepository;

    @Autowired
    AgentProfileRepository agentProfileRepository;

    @Test
    void persistsAgentProfileLinkedToUserAndLooksUpByLicenseNumber() {
        User agent = userRepository.save(User.builder()
                .email("agent-repo-1@example.com")
                .passwordHash("hash")
                .role(Role.AGENT)
                .fullName("Agent One")
                .tokenVersion(1)
                .createdAt(Instant.now())
                .build());

        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-AAA-001")
                .bio("Specialises in nothing yet")
                .createdAt(Instant.now())
                .build());

        Optional<AgentProfile> found = agentProfileRepository.findByLicenseNumber("LIC-AAA-001");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(agent.getId());
    }

    @Test
    void rejectsDuplicateLicenseNumber() {
        User a = userRepository.save(User.builder()
                .email("agent-repo-2@example.com")
                .passwordHash("hash").role(Role.AGENT).fullName("A")
                .tokenVersion(1).createdAt(Instant.now()).build());
        User b = userRepository.save(User.builder()
                .email("agent-repo-3@example.com")
                .passwordHash("hash").role(Role.AGENT).fullName("B")
                .tokenVersion(1).createdAt(Instant.now()).build());

        agentProfileRepository.saveAndFlush(AgentProfile.builder()
                .userId(a.getId()).licenseNumber("LIC-DUP").createdAt(Instant.now()).build());

        assertThatThrownBy(() -> agentProfileRepository.saveAndFlush(AgentProfile.builder()
                .userId(b.getId()).licenseNumber("LIC-DUP").createdAt(Instant.now()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
