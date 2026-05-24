package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.promotion.model.PromotionClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


public interface PromotionClickRepository extends JpaRepository<PromotionClick, Long> {
    long countByPromotionId(Long promotionId);

    @Query("select count(c) from PromotionClick c")
    long countAllClicks();
}
