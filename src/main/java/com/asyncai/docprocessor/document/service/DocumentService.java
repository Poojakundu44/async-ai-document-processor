package com.asyncai.docprocessor.document.service;

import com.asyncai.docprocessor.document.api.DocumentDTOs;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * WHY define an interface?
 * 1. Testability: inject a mock in unit tests without Spring context
 * 2. Multiple implementations: LocalDocumentService vs S3DocumentService
 * 3. Clear contract: the interface is the API of this component
 *
 * COMMON MISTAKE: Creating a 1:1 interface for every class "just in case".
 * Only create interfaces when there's a real reason (multiple implementations,
 * or a strong testing/mocking boundary). Premature abstraction adds noise.
 */
public interface DocumentService {

    DocumentDTOs.UploadResponse uploadDocument(
            String userId,
            MultipartFile file,
            DocumentDTOs.UploadRequest request
    );

    DocumentDTOs.DocumentResponse getDocument(String userId, UUID documentId);

    DocumentDTOs.DocumentListResponse getDocuments(String userId, Pageable pageable);
}
