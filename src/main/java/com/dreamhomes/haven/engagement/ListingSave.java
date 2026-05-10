package com.dreamhomes.haven.engagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's saved-for-later pin on a listing. Composite PK on (user_id, listing_id) so
 * a re-save is a no-op at the data layer — the service catches the duplicate-key path
 * and returns success rather than 409.
 */
@Entity
@Table(name = "listing_saves")
@IdClass(ListingSaveId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingSave {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;
}
