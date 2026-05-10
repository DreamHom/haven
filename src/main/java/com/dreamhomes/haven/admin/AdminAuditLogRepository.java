package com.dreamhomes.haven.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.admin.model.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
