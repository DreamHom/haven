package com.dreamhomes.haven.comment;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Public comment on a listing (PRD §4.9). Soft-delete via {@code deletedAt}: the row
 * stays for forensic and appeal purposes; partial indexes hide it from public reads.
 *
 * <p>{@code deletedAt + deletedByUserId + deletionReason} are wired together by the
 * {@code comments_delete_complete} CHECK constraint — services must populate them
 * atomically.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * Threading link (Item 8): when non-null this comment is a reply to the referenced
     * top-level comment. Validated at the service layer — parent must exist, be
     * non-deleted, and belong to the same listing. Vista builds the tree client-side
     * from the flat list returned by the list endpoint.
     */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    @Column(name = "deletion_reason", columnDefinition = "TEXT")
    private String deletionReason;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
