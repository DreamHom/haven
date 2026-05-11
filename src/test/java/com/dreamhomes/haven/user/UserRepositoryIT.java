package com.dreamhomes.haven.user;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;

@Transactional
class UserRepositoryIT extends AbstractPostgresIT {

    @Autowired
    UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        User saved = userRepository.save(newApplicant("ada@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<User> found = userRepository.findByEmail("ada@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.APPLICANT);
        assertThat(found.get().getFullName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(newApplicant("dup@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newApplicant("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailReturnsTrueWhenPresent() {
        userRepository.save(newApplicant("present@example.com"));

        assertThat(userRepository.existsByEmail("present@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("absent@example.com")).isFalse();
    }

    private static User newApplicant(String email) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhashfortestingonly")
                .role(Role.APPLICANT)
                .fullName("Ada Lovelace")
                .displayName("Ada Lovelace")
                .phone("+2348012345678")
                .createdAt(Instant.now())
                .build();
    }
}
