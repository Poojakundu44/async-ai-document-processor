package com.asyncai.docprocessor.document.domain;

/**
 * Document lifecycle states.
 *
 * INTERVIEW QUESTION: "How do you handle state machines in Java?"
 * Answer: For simple state machines, an enum with transition methods works.
 * For complex ones (many states, guards, async transitions), use Spring StateMachine
 * or a dedicated library.
 *
 * Valid transitions:
 *   UPLOADED → PROCESSING → COMPLETED
 *                        → FAILED
 */
public enum DocumentStatus {
    UPLOADED,    // File stored, waiting for Kafka consumer to pick it up
    PROCESSING,  // Kafka consumer is extracting/chunking/embedding
    COMPLETED,   // All chunks embedded, ready for querying
    FAILED       // Something went wrong (check error_message column)
}
