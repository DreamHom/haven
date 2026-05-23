package com.dreamhomes.haven.agentmarketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "agent_marketing_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMarketingMedia {

    private static final int MAX_CAPTION = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = MAX_CAPTION)
    private String caption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
