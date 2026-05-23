package com.dreamhomes.haven.dreamai.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.databind.JsonNode;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "dream_ai_chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DreamAiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private DreamAiChat chat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DreamAiChatMessageRole role;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    private JsonNode content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
