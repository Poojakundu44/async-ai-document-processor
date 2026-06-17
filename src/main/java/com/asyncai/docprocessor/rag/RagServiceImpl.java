package com.asyncai.docprocessor.rag;

import com.asyncai.docprocessor.common.exception.DocumentNotFoundException;
import com.asyncai.docprocessor.document.domain.Document;
import com.asyncai.docprocessor.document.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagServiceImpl implements RagService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final OpenAiChatModel openAiChatModel;

    @Value("${app.processing.chunk-size:512}")
    private int chunkSize;

    @Value("${app.processing.chunk-overlap:50}")
    private int chunkOverlap;

    @Override
    @Transactional
    public void processDocument(String userId, UUID documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        try {
            document.markAsProcessing();

            Tika tika = new Tika();
            String text = tika.parseToString(new File(document.getStoragePath()));

            List<String> chunks = textChunker.chunk(text, chunkSize, chunkOverlap);

            int index = 0;

            for (String chunkText : chunks) {
                Embedding embedding = embeddingModel.embed(chunkText).content();

                DocumentChunk chunk = DocumentChunk.builder()
                        .documentId(document.getId())
                        .userId(userId)
                        .chunkIndex(index++)
                        .content(chunkText)
                        .embedding(embedding.vectorAsList().toString())
                        .build();

                chunkRepository.save(chunk);
            }

            document.markAsCompleted(chunks.size());

            log.info("RAG processing completed for documentId={}, chunks={}", documentId, chunks.size());

        } catch (Exception e) {
            document.markAsFailed(e.getMessage());
            log.error("RAG processing failed for documentId={}", documentId, e);
            throw new RuntimeException("Document processing failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String askQuestion(String userId, UUID documentId, String question) {
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdAndUserId(documentId, userId);

        if (chunks.isEmpty()) {
            return "Document is not processed yet. Please process it first.";
        }

        Embedding questionEmbedding = embeddingModel.embed(question).content();

        List<DocumentChunk> topChunks = chunks.stream()
                .sorted(Comparator.comparingDouble(chunk ->
                        -cosineSimilarity(
                                questionEmbedding.vectorAsList(),
                                parseEmbedding(chunk.getEmbedding())
                        )
                ))
                .limit(3)
                .toList();

        String context = topChunks.stream()
                .map(DocumentChunk::getContent)
                .reduce("", (a, b) -> a + "\n\n" + b);

        String prompt = """
                You are an AI assistant answering questions from an uploaded document.

                Rules:
                - Answer only using the provided context.
                - If the answer is not present, say: "I could not find this information in the document."
                - Keep the answer clear and concise.

                Context:
                %s

                Question:
                %s
                """.formatted(context, question);

        return openAiChatModel.chat(prompt);
    }

    private List<Double> parseEmbedding(String value) {
        return List.of(value.replace("[", "").replace("]", "").split(","))
                .stream()
                .map(String::trim)
                .map(Double::parseDouble)
                .toList();
    }

    private double cosineSimilarity(List<Float> a, List<Double> b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}