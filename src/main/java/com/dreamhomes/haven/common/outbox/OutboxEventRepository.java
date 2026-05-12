package com.dreamhomes.haven.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<OutboxEvent> claimBatchForPublishing(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
