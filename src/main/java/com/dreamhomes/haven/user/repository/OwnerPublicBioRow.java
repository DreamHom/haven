package com.dreamhomes.haven.user.repository;

/**
 * Spring Data JPA projection for {@link UserRepository#findPublicBiosByUserIds}.
 */
public interface OwnerPublicBioRow {

    Long getOwnerId();

    String getPublicBio();
}
