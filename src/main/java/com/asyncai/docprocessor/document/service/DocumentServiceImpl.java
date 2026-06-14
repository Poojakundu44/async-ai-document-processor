package com.asyncai.docprocessor.document.service;

import com.asyncai.docprocessor.document.api.DocumentDTOs;
import com.asyncai.docprocessor.document.domain.Document;
import com.asyncai.docprocessor.document.domain.DocumentStatus;
import com.asyncai.docprocessor.document.repository.DocumentRepository;
import com.asyncai.docprocessor.common.exception.DocumentNotFoundException;
import com.asyncai.docprocessor.messaging.DocumentEventProducer;
import com.asyncai.docprocessor.messaging.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

/**
 * WHY @Slf4j?
 * Injects a 'log' field using the class name as the logger name.
 * Always use SLF4J (the API), never Log4j or JUL directly — it
 * lets you swap implementations (Logback, Log4j2) without code changes.
 *
 * WHY @RequiredArgsConstructor?
 * Constructor injection (not @Autowired field injection).
 * Constructor injection is preferred because:
 * 1. Dependencies are explicit and testable (you can instantiate with mocks)
 * 2. The object is always in a valid state after construction
 * 3. Circular dependency detection at startup, not at runtime
 *
 * WHY @Transactional on the class?
 * All public methods in this service need to run in a transaction.
 * Class-level annotation applies to all methods.
 * You can override with @Transactional(readOnly = true) on queries
 * for a performance boost (Hibernate won't track dirty state).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentEventProducer eventProducer;

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Override
    public DocumentDTOs.UploadResponse uploadDocument(
            String userId,
            MultipartFile file,
            DocumentDTOs.UploadRequest request) {

        // Step 1: Validate file
        validateFile(file);

        // Step 2: Store file to disk (in prod, swap for S3/Azure Blob)
        String storagePath = storeFile(file, userId);

        // Step 3: Persist metadata to DB
        Document document = Document.builder()
                .userId(userId)
                .fileName(generateStoredFileName(file.getOriginalFilename()))
                .originalFileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .storagePath(storagePath)
                .status(DocumentStatus.UPLOADED)
                .build();

        document = documentRepository.save(document);
        log.info("Document saved: id={}, user={}, file={}", document.getId(), userId, document.getOriginalFileName());

        // Step 4: Publish event to Kafka
        // WHY publish AFTER save? If Kafka publish fails, the file is saved but
        // we can always replay from a recovery job. If we saved to Kafka first
        // and the DB save failed, we'd process a document that doesn't exist in DB.
        // This ordering ensures at-least-once processing.
        DocumentUploadedEvent event = DocumentUploadedEvent.builder()
                .documentId(document.getId().toString())
                .userId(userId)
                .storagePath(storagePath)
                .fileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .uploadedAt(Instant.now())
                .build();

        eventProducer.publishDocumentUploaded(event);

        return DocumentDTOs.UploadResponse.builder()
                .documentId(document.getId())
                .status(document.getStatus())
                .uploadedAt(document.getCreatedAt())
                .message("Document uploaded successfully. Processing started asynchronously.")
                .build();
    }

    /**
     * WHY @Transactional(readOnly = true)?
     * Hibernate won't track dirty state for read-only transactions.
     * The DB can route reads to a read replica. Minor but real perf gain.
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentDTOs.DocumentResponse getDocument(String userId, UUID documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        return toResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDTOs.DocumentListResponse getDocuments(String userId, Pageable pageable) {
        Page<Document> page = documentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return DocumentDTOs.DocumentListResponse.builder()
                .documents(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        // In production: also validate content type, virus scan, etc.
        long maxSize = 50L * 1024 * 1024; // 50MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds 50MB limit");
        }
    }

    private String storeFile(MultipartFile file, String userId) {
        try {
            String subDir = uploadDir + "/" + userId;
            Path uploadPath = Paths.get(subDir);
            Files.createDirectories(uploadPath);

            String storedFileName = generateStoredFileName(file.getOriginalFilename());
            Path destination = uploadPath.resolve(storedFileName);

            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.debug("File stored at: {}", destination);
            return destination.toString();

        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage());
            throw new RuntimeException("Failed to store uploaded file", e);
        }
    }

    private String generateStoredFileName(String originalFilename) {
        // Prefix with UUID to prevent collisions and path traversal attacks
        return UUID.randomUUID() + "_" + sanitizeFileName(originalFilename);
    }

    private String sanitizeFileName(String filename) {
        if (filename == null) return "unknown";
        // Remove path separators and special characters — security measure
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private DocumentDTOs.DocumentResponse toResponse(Document doc) {
        return DocumentDTOs.DocumentResponse.builder()
                .id(doc.getId())
                .fileName(doc.getOriginalFileName())
                .fileSize(doc.getFileSize())
                .contentType(doc.getContentType())
                .status(doc.getStatus())
                .totalChunks(doc.getTotalChunks())
                .processedChunks(doc.getProcessedChunks())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .completedAt(doc.getCompletedAt())
                .build();
    }
}
