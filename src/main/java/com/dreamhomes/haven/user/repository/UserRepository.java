package com.dreamhomes.haven.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.dreamhomes.haven.user.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
