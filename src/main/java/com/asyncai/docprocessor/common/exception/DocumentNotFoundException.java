package com.asyncai.docprocessor.common.exception;

import java.util.UUID;

/**
 * WHY custom exceptions?
 * They carry domain-specific meaning. The GlobalExceptionHandler
 * pattern-matches on exception type to return the right HTTP status.
 * Generic RuntimeException gives you no information at the catch site.
 *
 * WHY extend RuntimeException (not Exception)?
 * Checked exceptions (extends Exception) force callers to catch or
 * declare throws — this pollutes method signatures and creates verbose
 * boilerplate. Spring and modern Java favor unchecked exceptions.
 * They propagate up to the @ExceptionHandler automatically.
 */
public class DocumentNotFoundException extends RuntimeException {

    private final UUID documentId;

    public DocumentNotFoundException(UUID documentId) {
        super("Document not found: " + documentId);
        this.documentId = documentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
