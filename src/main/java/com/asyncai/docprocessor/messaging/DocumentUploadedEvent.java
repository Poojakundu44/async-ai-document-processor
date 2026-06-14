package com.asyncai.docprocessor.messaging;

import lombok.Builder;
import lombok.Value;
import java.time.Instant;

/**
 * WHY a dedicated event class (not reusing the Document entity)?
 *
 * The Kafka event is an EXTERNAL CONTRACT. Other services might consume it.
 * If you publish your JPA entity directly:
 * 1. Adding a DB column causes a Kafka schema change
 * 2. JPA lazy-load proxies don't serialize cleanly to JSON
 * 3. You might accidentally publish sensitive DB internals
 *
 * Keep events minimal, stable, and explicitly versioned.
 * This is the "event schema" concept in event-driven architecture.
 */
@Value
@Builder
public class DocumentUploadedEvent {
    String documentId;
    String userId;
    String storagePath;
    String fileName;
    String contentType;
    Instant uploadedAt;
}
