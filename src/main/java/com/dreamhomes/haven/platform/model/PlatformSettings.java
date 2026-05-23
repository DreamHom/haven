package com.dreamhomes.haven.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    public static final Short SINGLETON_ID = 1;

    @Id
    @Column(nullable = false)
    private Short id = SINGLETON_ID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> settings = new HashMap<>();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
