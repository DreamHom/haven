package com.dreamhomes.haven.promotion;

import com.dreamhomes.haven.promotion.model.PromotionImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromotionImpressionRepository extends JpaRepository<PromotionImpression, Long> {
    long countByPromotionId(Long promotionId);

    @Query("select count(i) from PromotionImpression i")
    long countAllImpressions();
}
