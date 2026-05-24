package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



class PromotionDatabaseIT extends AbstractPostgresIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionRepository promotionRepository;

    @Test
    void activePlacementLookupExcludesPromotionsOutsideTheirWindow() {
        TestRows rows = seedListingRows();
        Instant now = Instant.parse("2026-06-05T12:00:00Z");
        long active = insertListingPromotion(rows.ownerId(), rows.adminId(),
                "HOMEPAGE_FEATURED", "ACTIVE",
                now.minusSeconds(60), now.plusSeconds(60), 10);
        insertListingPromotion(rows.ownerId(), rows.adminId(),
                "HOMEPAGE_FEATURED", "ACTIVE",
                now.plusSeconds(60), now.plusSeconds(3600), 20);
        insertListingPromotion(rows.ownerId(), rows.adminId(),
                "HOMEPAGE_FEATURED", "ACTIVE",
                now.minusSeconds(3600), now.minusSeconds(60), 30);

        var page = promotionRepository.findActiveForPlacement(
                PromotionPlacement.HOMEPAGE_FEATURED, now, Pageable.unpaged());

        assertThat(page.getContent()).extracting("id").containsExactly(active);
    }

    @Test
    void migrationRejectsInvalidStatusTargetAndPlacementValues() {
        TestRows rows = seedListingRows();
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-15T00:00:00Z");

        assertThatThrownBy(() -> insertRawPromotion(rows.ownerId(), rows.adminId(),
                "LISTING", rows.listingId(), null, "HOMEPAGE_FEATURED", "BOGUS", start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPromotion(rows.ownerId(), rows.adminId(),
                "PROJECT", rows.listingId(), null, "HOMEPAGE_FEATURED", "ACTIVE", start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPromotion(rows.ownerId(), rows.adminId(),
                "LISTING", rows.listingId(), null, "SIDEBAR", "ACTIVE", start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationRequiresTheCorrectTargetForeignKeyForTheTargetType() {
        TestRows rows = seedListingRows();
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-15T00:00:00Z");

        assertThatThrownBy(() -> insertRawPromotion(rows.ownerId(), rows.adminId(),
                "LISTING", null, rows.agentId(), "HOMEPAGE_FEATURED", "ACTIVE", start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPromotion(rows.agentId(), rows.adminId(),
                "AGENT", rows.listingId(), null, "AGENT_DIRECTORY_TOP", "ACTIVE", start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPromotion(rows.ownerId(), rows.adminId(),
                "LISTING", 999_999L, null, "HOMEPAGE_FEATURED", "ACTIVE", start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingPromotionCascadesMetrics() {
        TestRows rows = seedListingRows();
        long promotionId = insertListingPromotion(rows.ownerId(), rows.adminId(),
                "HOMEPAGE_FEATURED", "ACTIVE",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"),
                0);
        jdbc.update("""
                INSERT INTO promotion_impressions (promotion_id, viewer_user_id, placement)
                VALUES (?, ?, 'HOMEPAGE_FEATURED')
                """, promotionId, rows.agentId());
        jdbc.update("""
                INSERT INTO promotion_clicks (promotion_id, viewer_user_id, placement)
                VALUES (?, ?, 'HOMEPAGE_FEATURED')
                """, promotionId, rows.agentId());

        jdbc.update("DELETE FROM promotions WHERE id = ?", promotionId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_impressions", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_clicks", Long.class)).isZero();
    }

    private TestRows seedListingRows() {
        long ownerId = insertUser("owner-promotions@example.com", "OWNER");
        long agentId = insertUser("agent-promotions@example.com", "AGENT");
        long adminId = insertUser("admin-promotions@example.com", "ADMIN");
        long propertyId = jdbc.queryForObject("""
                INSERT INTO properties (owner_id, type, address)
                VALUES (?, 'APARTMENT', '1 Promotion Road')
                RETURNING id
                """, Long.class, ownerId);
        long listingId = jdbc.queryForObject("""
                INSERT INTO listings (property_id, owner_id, listing_type, asking_price, status)
                VALUES (?, ?, 'RENT', 1500000, 'LIVE')
                RETURNING id
                """, Long.class, propertyId, ownerId);
        return new TestRows(ownerId, agentId, adminId, listingId);
    }

    private long insertUser(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, role, full_name, display_name)
                VALUES (?, 'hash', ?, ?, ?)
                RETURNING id
                """, Long.class, email, role, role + " User", role);
    }

    private long insertListingPromotion(long ownerId, long adminId, String placement, String status,
                                        Instant startsAt, Instant endsAt, int priority) {
        Long listingId = jdbc.queryForObject("SELECT id FROM listings LIMIT 1", Long.class);
        return insertRawPromotion(ownerId, adminId, "LISTING", listingId, null,
                placement, status, startsAt, endsAt, priority);
    }

    private long insertRawPromotion(long createdByUserId, long adminId, String targetType,
                                    Long listingId, Long agentUserId, String placement,
                                    String status, Instant startsAt, Instant endsAt) {
        return insertRawPromotion(createdByUserId, adminId, targetType, listingId, agentUserId,
                placement, status, startsAt, endsAt, 0);
    }

    private long insertRawPromotion(long createdByUserId, long adminId, String targetType,
                                    Long listingId, Long agentUserId, String placement,
                                    String status, Instant startsAt, Instant endsAt,
                                    int priority) {
        return jdbc.queryForObject("""
                INSERT INTO promotions (
                    target_type, listing_id, agent_user_id, placement, status,
                    starts_at, ends_at, priority, created_by_user_id,
                    approved_by_admin_id, approved_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                targetType,
                listingId,
                agentUserId,
                placement,
                status,
                Timestamp.from(startsAt),
                Timestamp.from(endsAt),
                priority,
                createdByUserId,
                adminId,
                Timestamp.from(Instant.parse("2026-05-24T10:00:00Z")));
    }

    private record TestRows(long ownerId, long agentId, long adminId, long listingId) {
    }
}