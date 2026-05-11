package com.dreamhomes.haven.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.dreamhomes.haven.user.model.AgentProfile;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, Long> {

    Optional<AgentProfile> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);
}
