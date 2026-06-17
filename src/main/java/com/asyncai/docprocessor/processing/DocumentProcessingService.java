package com.asyncai.docprocessor.processing;

import java.util.UUID;

public interface DocumentProcessingService {
    void processDocument(UUID documentId, String userId);
}