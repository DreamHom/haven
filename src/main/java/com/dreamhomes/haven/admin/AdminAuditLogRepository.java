package com.dreamhomes.haven.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AdminAuditLog;
import com.dreamhomes.haven.admin.model.AuditTargetType;

import java.time.Instant;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * Backs {@code GET /api/admin/audit-logs}. Every filter is optional — passing
     * null treats it as a wildcard. Persona audit (Dayo) flagged the missing
     * read-side as the platform's most critical T&S gap: "every other moderation
     * guarantee on this platform is unfalsifiable without this."
     */
    @Query("""
            SELECT a FROM AdminAuditLog a
             WHERE (:actorId IS NULL OR a.adminId = :actorId)
               AND (:action IS NULL OR a.action = :action)
               AND (:targetType IS NULL OR a.targetType = :targetType)
               AND (:targetId IS NULL OR a.targetId = :targetId)
               AND (:from IS NULL OR a.createdAt >= :from)
               AND (:to IS NULL OR a.createdAt <= :to)
             ORDER BY a.createdAt DESC
            """)
    Page<AdminAuditLog> search(@Param("actorId") Long actorId,
                               @Param("action") AdminAction action,
                               @Param("targetType") AuditTargetType targetType,
                               @Param("targetId") Long targetId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);
}
