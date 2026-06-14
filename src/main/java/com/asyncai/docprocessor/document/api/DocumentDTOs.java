package com.asyncai.docprocessor.document.api;

import com.asyncai.docprocessor.document.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * WHY separate DTOs from domain entities?
 *
 * 1. SECURITY: Your entity might have sensitive fields (internal IDs,
 *    audit fields, internal flags). DTOs control exactly what you expose.
 *
 * 2. VERSIONING: Your API contract can evolve independently of your DB schema.
 *    You can add a column without changing the API, or add an API field
 *    by computing it in the service layer.
 *
 * 3. VALIDATION: Validation annotations belong on DTOs (input boundary),
 *    not on entities (persistence layer). These are different concerns.
 *
 * WHY @Value (Lombok)? Creates immutable DTOs — once created, can't be modified.
 * Immutable objects are thread-safe and easier to reason about.
 * Use @Data for mutable DTOs, @Value for immutable ones.
 */
public class DocumentDTOs {

    // ============================================================
    // REQUEST OBJECTS (inbound from client)
    // ============================================================

    /**
     * Used when client provides metadata alongside the file upload.
     * The file itself comes as MultipartFile in the controller.
     */
    @Schema(description = "Metadata for document upload")
    @Value
    @Builder
    public static class UploadRequest {

        @NotBlank(message = "Description cannot be blank")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        @Schema(description = "Human-readable description", example = "Q3 2024 Financial Report")
        String description;

        @Schema(description = "Optional tags for categorization", example = "finance,quarterly")
        String tags;
    }

    // ============================================================
    // RESPONSE OBJECTS (outbound to client)
    // ============================================================

    /**
     * Returned immediately after upload.
     * Note: no file contents, no storage path — those are internal details.
     */
    @Schema(description = "Document upload confirmation")
    @Value
    @Builder
    public static class UploadResponse {

        @Schema(description = "Unique document ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID documentId;

        @Schema(description = "Current processing status")
        DocumentStatus status;

        @Schema(description = "Upload timestamp")
        Instant uploadedAt;

        @Schema(description = "Message describing next steps")
        String message;
    }

    /**
     * Full document details — returned by GET /documents/{id}
     */
    @Schema(description = "Document details")
    @Value
    @Builder
    public static class DocumentResponse {

        UUID id;
        String fileName;
        Long fileSize;
        String contentType;
        DocumentStatus status;
        Integer totalChunks;
        Integer processedChunks;
        String errorMessage;
        Instant createdAt;
        Instant updatedAt;
        Instant completedAt;

        /**
         * Computed field — not stored in DB.
         * This is a benefit of DTOs: add derived fields without changing schema.
         */
        public Double getProcessingProgress() {
            if (totalChunks == null || totalChunks == 0) return 0.0;
            if (processedChunks == null) return 0.0;
            return (double) processedChunks / totalChunks * 100;
        }
    }

    /**
     * Paginated list response — standard pagination envelope.
     * WHY a wrapper? So you can add metadata (totalPages, totalElements)
     * without breaking API clients when you add more fields later.
     */
    @Schema(description = "Paginated list of documents")
    @Value
    @Builder
    public static class DocumentListResponse {

        java.util.List<DocumentResponse> documents;
        int page;
        int size;
        long totalElements;
        int totalPages;
        boolean last;
    }
}
