package com.asyncai.docprocessor.processing;

import com.asyncai.docprocessor.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final RagService ragService;

    @Override
    public void processDocument(UUID documentId, String userId) {

        log.info("Starting document processing for documentId={}", documentId);

        ragService.processDocument(userId, documentId);

        log.info("Completed document processing for documentId={}", documentId);
    }
}