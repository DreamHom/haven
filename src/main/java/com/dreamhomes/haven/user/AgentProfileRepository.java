package com.dreamhomes.haven.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, Long> {

    Optional<AgentProfile> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);
}
