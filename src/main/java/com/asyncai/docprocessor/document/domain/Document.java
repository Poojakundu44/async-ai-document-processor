package com.asyncai.docprocessor.document.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * WHY use UUID as primary key instead of Long?
 *
 * Long (auto-increment): predictable, guessable (user can iterate IDs),
 * requires DB round-trip to get the generated ID, causes merge conflicts
 * in distributed systems.
 *
 * UUID: globally unique, client can generate it before insert (important
 * for idempotent APIs), not guessable, works across microservices.
 *
 * TRADE-OFF: UUID indexes are slightly less efficient than integer indexes
 * because UUIDs are random, causing B-tree page splits. For this service
 * the benefit of distribution-safety outweighs the minor index cost.
 *
 * WHY @Builder with @NoArgsConstructor + @AllArgsConstructor?
 * JPA requires a no-args constructor. Lombok's @Builder generates
 * an all-args constructor. We need both.
 */
@Entity
@Table(
    name = "documents",
    indexes = {
        // WHY these indexes?
        // status: we frequently query "give me all PROCESSING documents"
        // userId: every query is scoped to a user
        // createdAt: for sorting/pagination by date
        @Index(name = "idx_documents_status", columnList = "status"),
        @Index(name = "idx_documents_user_id", columnList = "user_id"),
        @Index(name = "idx_documents_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    /**
     * WHY @Enumerated(EnumType.STRING)?
     * ORDINAL stores the enum's integer position (0, 1, 2...).
     * If you reorder the enum, ORDINAL breaks silently — corrupt data.
     * STRING stores "UPLOADED", "PROCESSING" etc. — safe to reorder,
     * readable in DB queries, no silent corruption.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "processed_chunks")
    private Integer processedChunks;

    /**
     * WHY @CreationTimestamp / @UpdateTimestamp?
     * Hibernate fills these automatically. Never rely on application
     * code to set timestamps — the app server clock can drift.
     * Even better: use database DEFAULT now() via Flyway migration
     * as a final backstop.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // ============================================================
    // Domain methods — business logic belongs on the entity, not
    // in a service class. This is the "rich domain model" pattern.
    // A pure data-holder entity (anemic domain model) is an anti-pattern.
    // ============================================================

    public void markAsProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markAsCompleted(int totalChunks) {
        this.status = DocumentStatus.COMPLETED;
        this.totalChunks = totalChunks;
        this.completedAt = Instant.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public boolean isProcessable() {
        return this.status == DocumentStatus.UPLOADED;
    }
}
