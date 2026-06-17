package com.asyncai.docprocessor.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    public List<String> chunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String[] words = text.split("\\s+");

        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);

            StringBuilder chunk = new StringBuilder();

            for (int i = start; i < end; i++) {
                chunk.append(words[i]).append(" ");
            }

            chunks.add(chunk.toString().trim());

            if (end == words.length) {
                break;
            }

            start = Math.max(0, end - overlap);
        }

        return chunks;
    }
}
