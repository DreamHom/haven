package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;


class PromotionRepositoryIT extends AbstractPostgresIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionRepository promotionRepository;

    @Test
    void ownerLookupReturnsNewestPromotionsFirst() {
        TestRows rows = seedListingRows();
        long older = insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.PENDING,
                Instant.parse("2026-05-01T00:00:00Z"));
        long newer = insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.LISTING_SEARCH_TOP, PromotionStatus.PENDING,
                Instant.parse("2026-05-02T00:00:00Z"));
        insertPromotion(rows.otherOwnerId(), rows.adminId(), rows.otherListingId(),
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.PENDING,
                Instant.parse("2026-05-03T00:00:00Z"));

        var page = promotionRepository.findByCreatedByUserIdOrderByCreatedAtDesc(
                rows.ownerId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting("id").containsExactly(newer, older);
    }

    @Test
    void adminSearchFiltersByStatusTargetTypePlacementAndCreator() {
        TestRows rows = seedListingRows();
        long match = insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.ACTIVE,
                Instant.parse("2026-05-01T00:00:00Z"));
        insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.LISTING_SEARCH_TOP, PromotionStatus.ACTIVE,
                Instant.parse("2026-05-02T00:00:00Z"));
        insertPromotion(rows.otherOwnerId(), rows.adminId(), rows.otherListingId(),
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.ACTIVE,
                Instant.parse("2026-05-03T00:00:00Z"));
        insertAgentPromotion(rows.agentId(), rows.adminId(),
                PromotionPlacement.AGENT_DIRECTORY_TOP, PromotionStatus.ACTIVE,
                Instant.parse("2026-05-04T00:00:00Z"));

        var page = promotionRepository.adminSearch(
                PromotionStatus.ACTIVE,
                PromotionTargetType.LISTING,
                PromotionPlacement.HOMEPAGE_FEATURED,
                rows.ownerId(),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting("id").containsExactly(match);
    }

    @Test
    void countByStatusCountsOnlyRequestedStatus() {
        TestRows rows = seedListingRows();
        insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.ACTIVE,
                Instant.parse("2026-05-01T00:00:00Z"));
        insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.LISTING_SEARCH_TOP, PromotionStatus.ACTIVE,
                Instant.parse("2026-05-02T00:00:00Z"));
        insertPromotion(rows.ownerId(), rows.adminId(), rows.listingId(),
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.PAUSED,
                Instant.parse("2026-05-03T00:00:00Z"));

        assertThat(promotionRepository.countByStatus(PromotionStatus.ACTIVE)).isEqualTo(2);
        assertThat(promotionRepository.countByStatus(PromotionStatus.PAUSED)).isEqualTo(1);
    }

    private TestRows seedListingRows() {
        long ownerId = insertUser("repo-owner@example.com", "OWNER");
        long otherOwnerId = insertUser("repo-other-owner@example.com", "OWNER");
        long agentId = insertUser("repo-agent@example.com", "AGENT");
        long adminId = insertUser("repo-admin@example.com", "ADMIN");
        long listingId = insertListing(ownerId, "1 Repo Road");
        long otherListingId = insertListing(otherOwnerId, "2 Repo Road");
        return new TestRows(ownerId, otherOwnerId, agentId, adminId, listingId, otherListingId);
    }

    private long insertUser(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, role, full_name, display_name)
                VALUES (?, 'hash', ?, ?, ?)
                RETURNING id
                """, Long.class, email, role, role + " User", role);
    }

    private long insertListing(long ownerId, String address) {
        long propertyId = jdbc.queryForObject("""
                INSERT INTO properties (owner_id, type, address)
                VALUES (?, 'APARTMENT', ?)
                RETURNING id
                """, Long.class, ownerId, address);
        return jdbc.queryForObject("""
                INSERT INTO listings (property_id, owner_id, listing_type, asking_price, status)
                VALUES (?, ?, 'RENT', 1500000, 'LIVE')
                RETURNING id
                """, Long.class, propertyId, ownerId);
    }

    private long insertPromotion(long ownerId, long adminId, long listingId,
                                 PromotionPlacement placement, PromotionStatus status,
                                 Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO promotions (
                    target_type, listing_id, placement, status, starts_at, ends_at,
                    priority, created_by_user_id, approved_by_admin_id, approved_at,
                    created_at, updated_at
                )
                VALUES ('LISTING', ?, ?, ?, '2026-06-01T00:00:00Z', '2026-06-15T00:00:00Z',
                        0, ?, ?, '2026-05-24T10:00:00Z', ?, ?)
                RETURNING id
                """, Long.class, listingId, placement.name(), status.name(), ownerId, adminId,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private long insertAgentPromotion(long agentId, long adminId, PromotionPlacement placement,
                                      PromotionStatus status, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO promotions (
                    target_type, agent_user_id, placement, status, starts_at, ends_at,
                    priority, created_by_user_id, approved_by_admin_id, approved_at,
                    created_at, updated_at
                )
                VALUES ('AGENT', ?, ?, ?, '2026-06-01T00:00:00Z', '2026-06-15T00:00:00Z',
                        0, ?, ?, '2026-05-24T10:00:00Z', ?, ?)
                RETURNING id
                """, Long.class, agentId, placement.name(), status.name(), agentId, adminId,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private record TestRows(long ownerId, long otherOwnerId, long agentId, long adminId,
                            long listingId, long otherListingId) {
    }
}