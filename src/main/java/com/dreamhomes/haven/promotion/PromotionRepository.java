package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.promotion.model.Promotion;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;


public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Page<Promotion> findByCreatedByUserIdOrderByCreatedAtDesc(Long createdByUserId, Pageable pageable);

    @Query(value = """
            SELECT p FROM Promotion p
             WHERE p.status = com.dreamhomes.haven.promotion.model.PromotionStatus.ACTIVE
               AND p.placement = :placement
               AND p.startsAt <= :now
               AND p.endsAt > :now
             ORDER BY p.priority DESC, p.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(p) FROM Promotion p
             WHERE p.status = com.dreamhomes.haven.promotion.model.PromotionStatus.ACTIVE
               AND p.placement = :placement
               AND p.startsAt <= :now
               AND p.endsAt > :now
            """)
    Page<Promotion> findActiveForPlacement(@Param("placement") PromotionPlacement placement,
                                           @Param("now") Instant now,
                                           Pageable pageable);

    @Query(value = """
            SELECT p FROM Promotion p
             WHERE (:status IS NULL OR p.status = :status)
               AND (:targetType IS NULL OR p.targetType = :targetType)
               AND (:placement IS NULL OR p.placement = :placement)
               AND (:createdByUserId IS NULL OR p.createdByUserId = :createdByUserId)
             ORDER BY p.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(p) FROM Promotion p
             WHERE (:status IS NULL OR p.status = :status)
               AND (:targetType IS NULL OR p.targetType = :targetType)
               AND (:placement IS NULL OR p.placement = :placement)
               AND (:createdByUserId IS NULL OR p.createdByUserId = :createdByUserId)
            """)
    Page<Promotion> adminSearch(@Param("status") PromotionStatus status,
                                @Param("targetType") PromotionTargetType targetType,
                                @Param("placement") PromotionPlacement placement,
                                @Param("createdByUserId") Long createdByUserId,
                                Pageable pageable);

    long countByStatus(PromotionStatus status);
}