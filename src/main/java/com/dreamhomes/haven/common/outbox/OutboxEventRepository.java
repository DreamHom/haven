package com.dreamhomes.haven.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims up to {@code limit} unpublished rows, locked with {@code FOR UPDATE SKIP
     * LOCKED} so multiple relay instances (or workers within one) can poll in parallel
     * without contention. Native because JPA's portable lock hints don't reliably emit
     * {@code SKIP LOCKED} across versions.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<OutboxEvent> claimBatchForPublishing(@Param("limit") int limit);

    /** Backs the {@code haven.outbox.unpublished} Micrometer gauge for ops alerting. */
    long countByPublishedAtIsNull();
}
