package com.dreamhomes.haven.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PhotoUploadIntentRepository extends JpaRepository<PhotoUploadIntent, Long> {

    /** Confirm path looks the row up by the caller-supplied fileKey. */
    Optional<PhotoUploadIntent> findByFileKey(String fileKey);

    /**
     * Item 2 cleanup — delete any intent older than the supplied threshold.
     * Confirmed rows have done their job; unconfirmed rows past expiry are orphans.
     */
    @Modifying
    @Query("DELETE FROM PhotoUploadIntent p WHERE p.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") Instant threshold);
}
