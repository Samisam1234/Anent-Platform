package com.agentplatform.rag.chunking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure-JUnit tests for {@link TextChunker} — no Spring context, no database.
 */
@DisplayName("TextChunker — deterministic separator-aware chunking")
class TextChunkerTest {

    private static final int MAX_SIZE = 50;
    private static final int OVERLAP = 15;

    @Test
    @DisplayName("empty input produces no chunks")
    void emptyInput_producesNoChunks() {
        assertThat(new TextChunker(MAX_SIZE, OVERLAP).chunk("")).isEmpty();
    }

    @Test
    @DisplayName("null input produces no chunks")
    void nullInput_producesNoChunks() {
        assertThat(new TextChunker(MAX_SIZE, OVERLAP).chunk(null)).isEmpty();
    }

    @Test
    @DisplayName("blank input produces no chunks")
    void blankInput_producesNoChunks() {
        assertThat(new TextChunker(MAX_SIZE, OVERLAP).chunk("   \n \t ")).isEmpty();
    }

    @Test
    @DisplayName("short input produces a single chunk equal to the stripped text")
    void shortInput_producesSingleChunk() {
        String text = "Short text, well below the maximum size.";
        List<String> chunks = new TextChunker(MAX_SIZE, OVERLAP).chunk(text);
        assertThat(chunks).containsExactly(text);
    }

    @Test
    @DisplayName("long input is split so every chunk respects maxSize")
    void longInput_respectsMaxSize() {
        String text = IntStream.range(0, 30)
                .mapToObj(i -> "sentence number " + i + " here.")
                .collect(Collectors.joining(" "));

        List<String> chunks = new TextChunker(MAX_SIZE, OVERLAP).chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(MAX_SIZE));
        assertThat(chunks).extracting(String::length)
                .allMatch(len -> len > 0);
    }

    @Test
    @DisplayName("overlap carries the tail of each chunk into the next one")
    void overlap_preservedBetweenChunks() {
        String text = IntStream.range(0, 12)
                .mapToObj(i -> "word" + i)
                .collect(Collectors.joining(" "));

        List<String> chunks = new TextChunker(MAX_SIZE, OVERLAP).chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 0; i + 1 < chunks.size(); i++) {
            String lastWord = lastWord(chunks.get(i));
            assertThat(chunks.get(i + 1)).as("chunk %d carries overlap from chunk %d", i + 1, i)
                    .contains(lastWord);
        }
    }

    @Test
    @DisplayName("output is deterministic for identical input")
    void output_isDeterministic() {
        String text = IntStream.range(0, 20)
                .mapToObj(i -> "topic " + i + " discusses retrieval.")
                .collect(Collectors.joining(" "));

        List<String> first = new TextChunker(MAX_SIZE, OVERLAP).chunk(text);
        List<String> second = new TextChunker(MAX_SIZE, OVERLAP).chunk(text);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("text without separators is hard-split on word boundaries and reassembles exactly")
    void noSeparators_hardSplitsOnWordBoundaries() {
        String text = IntStream.range(0, 100)
                .mapToObj(i -> "word" + i)
                .collect(Collectors.joining(" "));

        List<String> chunks = new TextChunker(MAX_SIZE, 0).chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(MAX_SIZE));
        assertThat(String.join(" ", chunks)).isEqualTo(text);
    }

    @Test
    @DisplayName("constructor rejects invalid max-size/overlap combinations")
    void constructor_rejectsInvalidCombinations() {
        assertThatThrownBy(() -> new TextChunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(10, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String lastWord(String chunk) {
        String[] words = chunk.trim().split("\\s+");
        return words[words.length - 1];
    }
}