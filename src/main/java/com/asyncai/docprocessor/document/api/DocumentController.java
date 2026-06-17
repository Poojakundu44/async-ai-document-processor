package com.asyncai.docprocessor.document.api;

import com.asyncai.docprocessor.document.service.DocumentService;
import com.asyncai.docprocessor.processing.DocumentProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * WHY @RequestMapping("/api/v1/documents")?
 * Version your API from day one. When you need breaking changes in v2,
 * you can run /v1 and /v2 side-by-side with zero downtime migration.
 * This is industry standard — Google, Stripe, GitHub all do this.
 *
 * WHY extract userId from a header?
 * In a real system this comes from a JWT token validated by a filter/interceptor.
 * We use X-User-Id header here as a placeholder — in Phase 16 we'll add JWT auth.
 * NEVER trust client-provided user IDs without validation.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Document upload and management APIs")
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessingService documentProcessingService;    /**
     * File upload uses multipart/form-data (not JSON body) because:
     * 1. Binary files can't be cleanly embedded in JSON without base64 encoding
     * 2. Base64 increases payload size by ~33%
     * 3. Multipart is the HTTP standard for file uploads
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Upload a document for AI processing",
            description = "Accepts PDF, DOCX, or TXT files. Processing happens asynchronously via Kafka/RAG pipeline.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Document accepted for processing"),
                    @ApiResponse(responseCode = "400", description = "Invalid file or request"),
                    @ApiResponse(responseCode = "413", description = "File too large")
            }
    )
    public ResponseEntity<DocumentDTOs.UploadResponse> uploadDocument(
            @RequestHeader("X-User-Id") String userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description
    ) {
        log.info("Upload request: user={}, file={}, size={}",
                userId, file.getOriginalFilename(), file.getSize());

        DocumentDTOs.UploadRequest metadata = DocumentDTOs.UploadRequest.builder()
                .description(
                        description != null && !description.isBlank()
                                ? description
                                : file.getOriginalFilename()
                )
                .build();

        DocumentDTOs.UploadResponse response =
                documentService.uploadDocument(userId, file, metadata);

        return ResponseEntity.accepted().body(response);
    }
    @GetMapping("/{documentId}")
    @Operation(summary = "Get document details and processing status")
    public ResponseEntity<DocumentDTOs.DocumentResponse> getDocument(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID documentId
    ) {
        return ResponseEntity.ok(documentService.getDocument(userId, documentId));
    }

    /**
     * WHY pagination parameters as @RequestParam with defaults?
     * Explicit defaults in the controller signature are self-documenting
     * and show up correctly in Swagger UI.
     */
    @GetMapping
    @Operation(summary = "List all documents for the authenticated user")
    public ResponseEntity<DocumentDTOs.DocumentListResponse> listDocuments(
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        // Cap page size to prevent abuse
        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);
        return ResponseEntity.ok(documentService.getDocuments(userId, pageable));
    }


    @PostMapping("/{documentId}/process")
    public ResponseEntity<String> processDocument(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID documentId) {

        documentProcessingService.processDocument(documentId, userId);
        return ResponseEntity.ok("Document processed successfully");
    }
}
