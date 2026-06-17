package com.asyncai.docprocessor.rag;

import java.util.UUID;

public interface RagService {

    void processDocument(String userId, UUID documentId);

    String askQuestion(String userId, UUID documentId, String question);
}