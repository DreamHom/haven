package com.dreamhomes.haven.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /** All listings tied to a property (any status) — used to refresh Dream AI embeddings after address/geo changes. */
    List<Listing> findByPropertyId(Long propertyId);

    /**
     * Service-level pre-check for "at most one LIVE listing per (property, listing_type)"
     * (Item 12). Throws {@code ListingDuplicateOpenForTypeException} on positive hits
     * before the DB partial-UQ has to refuse the insert. The DB constraint (V47) remains
     * the race-safety net for two concurrent creates that both pass this check.
     */
    boolean existsByPropertyIdAndListingTypeAndStatus(
            Long propertyId,
            com.dreamhomes.haven.listing.model.ListingType listingType,
            ListingStatus status);

    /** Backs the public browse endpoint — only LIVE listings are visible to anonymous callers. */
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    /**
     * Filtered browse — public-facing search with optional predicates. Every filter
     * is optional; null = wildcard. Restricted to LIVE listings only (admin/owner
     * views use other methods).
     *
     * <p>Joins to {@code Property} so we can filter by address fragment and bedrooms
     * without loading the full property entity into memory.</p>
     *
     * <p>Persona audit (Temi, Ngozi, Emeka): the catalogue was unfilterable; query
     * params were silently ignored. This closes that gap.</p>
     */
    @Query(value = """
            SELECT l FROM Listing l
              JOIN com.dreamhomes.haven.property.model.Property p ON p.id = l.propertyId
             WHERE l.status = com.dreamhomes.haven.listing.model.ListingStatus.LIVE
               AND (:listingType IS NULL OR l.listingType = :listingType)
               AND (:priceMin IS NULL OR l.askingPrice >= :priceMin)
               AND (:priceMax IS NULL OR l.askingPrice <= :priceMax)
               AND (:bedrooms IS NULL OR p.bedrooms = :bedrooms)
               AND (:propertyType IS NULL OR p.type = :propertyType)
               AND (:location IS NULL OR LOWER(p.address) LIKE LOWER(CONCAT('%', :location, '%')))
            """,
            countQuery = """
            SELECT COUNT(l) FROM Listing l
              JOIN com.dreamhomes.haven.property.model.Property p ON p.id = l.propertyId
             WHERE l.status = com.dreamhomes.haven.listing.model.ListingStatus.LIVE
               AND (:listingType IS NULL OR l.listingType = :listingType)
               AND (:priceMin IS NULL OR l.askingPrice >= :priceMin)
               AND (:priceMax IS NULL OR l.askingPrice <= :priceMax)
               AND (:bedrooms IS NULL OR p.bedrooms = :bedrooms)
               AND (:propertyType IS NULL OR p.type = :propertyType)
               AND (:location IS NULL OR LOWER(p.address) LIKE LOWER(CONCAT('%', :location, '%')))
            """)
    Page<Listing> searchLive(@Param("listingType") com.dreamhomes.haven.listing.model.ListingType listingType,
                             @Param("priceMin") java.math.BigDecimal priceMin,
                             @Param("priceMax") java.math.BigDecimal priceMax,
                             @Param("bedrooms") Integer bedrooms,
                             @Param("propertyType") com.dreamhomes.haven.property.model.PropertyType propertyType,
                             @Param("location") String location,
                             Pageable pageable);

    /** Backs {@code GET /api/listings/mine}: owner's portfolio across all statuses, newest first. */
    Page<Listing> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    /** Aggregate count by status — backs the admin analytics summary. */
    long countByStatus(ListingStatus status);

    /**
     * Closed-deal count for a user — backs the {@code closedDealCount} trust signal on
     * {@code GET /api/users/{id}/profile}. Ngozi (skeptic) flagged this as the single
     * proof-of-track-record number missing from agent and owner profiles.
     */
    long countByOwnerIdAndStatus(Long ownerId, ListingStatus status);

    /**
     * Lock-free atomic increment of {@code view_count}. Bypasses Hibernate's first-level
     * cache + version check on purpose: a popular listing's @Version shouldn't churn on
     * every anonymous page view. Returns the number of rows updated (0 = listing missing).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Listing l SET l.viewCount = l.viewCount + 1 WHERE l.id = :id")
    int incrementViewCount(@Param("id") Long id);

    /** Admin catalogue — optional status filter, newest first. */
    @Query(value = "SELECT l FROM Listing l WHERE (:status IS NULL OR l.status = :status) ORDER BY l.createdAt DESC",
            countQuery = "SELECT COUNT(l) FROM Listing l WHERE (:status IS NULL OR l.status = :status)")
    Page<Listing> adminCatalog(@Param("status") ListingStatus status, Pageable pageable);

    @Query("SELECT l.id FROM Listing l WHERE l.id IN :ids AND l.status = com.dreamhomes.haven.listing.model.ListingStatus.LIVE")
    List<Long> findLiveIdsAmongIds(@Param("ids") List<Long> ids);
}
