package com.dreamhomes.haven.user.dto;

import com.dreamhomes.haven.user.model.Role;

import java.time.Instant;

/**
 * Private account-settings projection for the authenticated user. Includes fields that
 * must never appear on public profile endpoints, such as email, phone, and agent
 * license metadata.
 */
public record MyAccountProfile(
        Long id,
        String email,
        String fullName,
        String displayName,
        String phone,
        Role role,
        Instant identityVerifiedAt,
        Instant agentCredentialVerifiedAt,
        String licenseNumber,
        String agency,
        boolean suspended,
        Instant joinedAt
) {
}
