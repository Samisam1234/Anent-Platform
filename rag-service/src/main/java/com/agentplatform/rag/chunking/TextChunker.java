package com.agentplatform.rag.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, separator-aware text chunker for RAG ingestion.
 *
 * <p>Splits text into sentence-sized pieces (paragraph-first, then
 * sentence-boundary), greedily packs pieces into chunks up to {@code maxChunkSize}
 * characters, and carries an {@code overlap}-character tail (aligned to a word
 * boundary) from each emitted chunk into the start of the next one.</p>
 *
 * <p>Behavior notes:</p>
 * <ul>
 *   <li>{@code null}/{@code blank} input produces no chunks.</li>
 *   <li>Input that fits in {@code maxChunkSize} produces a single chunk.</li>
 *   <li>A piece that alone exceeds {@code maxChunkSize} is hard-split on word
 *       boundaries into max-size parts.</li>
 *   <li>Output order and content are fully deterministic.</li>
 * </ul>
 *
 * <p>Defaults are configured via {@code rag.chunk.*} in {@code application.yml}
 * (see {@code RagProperties}); this class is constructed with the resolved values.</p>
 */
public class TextChunker {

    private final int maxChunkSize;
    private final int overlap;

    public TextChunker(int maxChunkSize, int overlap) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must not be negative");
        }
        if (overlap >= maxChunkSize) {
            throw new IllegalArgumentException("overlap must be smaller than maxChunkSize");
        }
        this.maxChunkSize = maxChunkSize;
        this.overlap = overlap;
    }

    /**
     * Chunks the given text deterministically.
     *
     * @param text the text to chunk
     * @return ordered list of chunks; empty for {@code null}/blank input
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = text.strip();
        if (cleaned.length() <= maxChunkSize) {
            return List.of(cleaned);
        }

        List<String> chunks = new ArrayList<>();
        String current = "";
        for (String piece : splitIntoPieces(cleaned)) {
            List<String> atoms = piece.length() > maxChunkSize ? hardSplit(piece) : List.of(piece);
            for (String atom : atoms) {
                if (current.isEmpty()) {
                    current = atom;
                } else {
                    String candidate = current + " " + atom;
                    if (candidate.length() <= maxChunkSize) {
                        current = candidate;
                    } else {
                        chunks.add(current);
                        String tail = overlapOf(current);
                        current = tail.isEmpty() ? atom : tail + " " + atom;
                        if (current.length() > maxChunkSize) {
                            current = atom;
                        }
                    }
                }
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    /**
     * Splits text into pieces on paragraph breaks first, then sentence boundaries.
     */
    private static List<String> splitIntoPieces(String text) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : text.split("\\r?\\n\\s*\\r?\\n")) {
            for (String sentence : paragraph.strip().split("(?<=[.!?])\\s+")) {
                String s = sentence.strip();
                if (!s.isEmpty()) {
                    pieces.add(s);
                }
            }
        }
        return pieces;
    }

    /**
     * Splits a single over-long piece into max-size parts on word boundaries.
     */
    private List<String> hardSplit(String text) {
        List<String> words = List.of(text.split("\\s+"));
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxChunkSize) {
                current.append(' ').append(word);
            } else {
                parts.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    /**
     * Returns the word-aligned tail of the emitted chunk (up to {@code overlap}
     * characters) that will be carried into the next chunk.
     */
    private String overlapOf(String chunk) {
        if (overlap <= 0 || chunk.isEmpty()) {
            return "";
        }
        if (chunk.length() <= overlap) {
            return chunk;
        }
        int start = chunk.length() - overlap;
        int space = chunk.indexOf(' ', start);
        if (space < 0) {
            return "";
        }
        return chunk.substring(space + 1);
    }
}