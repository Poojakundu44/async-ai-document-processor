package com.asyncai.docprocessor.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer — thin wrapper around KafkaTemplate.
 * Full deep-dive in Phase 5. Stub here so the service compiles.
 *
 * WHY use the document ID as the Kafka message key?
 * Kafka guarantees ordering WITHIN a partition.
 * If we use documentId as key, all events for the same document
 * go to the same partition — preserving event order for that document.
 * This is critical if we ever publish multiple events per document.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.document-uploaded}")
    private String documentUploadedTopic;

    public void publishDocumentUploaded(DocumentUploadedEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(documentUploadedTopic, event.getDocumentId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish DocumentUploadedEvent for documentId={}: {}",
                        event.getDocumentId(), ex.getMessage(), ex);
                // In Phase 5 we'll add retry logic and dead letter queue
            } else {
                log.info("Published DocumentUploadedEvent: documentId={}, partition={}, offset={}",
                        event.getDocumentId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
