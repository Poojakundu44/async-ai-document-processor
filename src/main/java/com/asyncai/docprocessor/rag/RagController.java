package com.asyncai.docprocessor.rag;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/documents/{documentId}/process")
    public ResponseEntity<String> processDocument(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID documentId) {

        ragService.processDocument(userId, documentId);

        return ResponseEntity.ok("Document processed successfully for RAG");
    }

    @PostMapping("/documents/{documentId}/ask")
    public ResponseEntity<AskResponse> askQuestion(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID documentId,
            @RequestBody AskRequest request) {

        String answer = ragService.askQuestion(userId, documentId, request.getQuestion());

        return ResponseEntity.ok(new AskResponse(answer));
    }

    @Data
    public static class AskRequest {
        @NotBlank
        private String question;
    }

    public record AskResponse(String answer) {
    }
}