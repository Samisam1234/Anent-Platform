package com.agentplatform.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryConversationStore}.
 */
class InMemoryConversationStoreTest {

    private final InMemoryConversationStore store = new InMemoryConversationStore();

    @Test
    @DisplayName("append() creates the conversation on first write and keeps ordering")
    void append_createsAndOrdered() {
        store.append("conv-1", ConversationMessage.user("Hello"));
        store.append("conv-1", ConversationMessage.ai("Hi there"));

        List<ConversationMessage> messages = store.messages("conv-1");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).isEqualTo("Hello");
        assertThat(messages.get(1).role()).isEqualTo("ai");
        assertThat(store.exists("conv-1")).isTrue();
        assertThat(store.count()).isEqualTo(1);
        assertThat(store.snapshot("conv-1")).isPresent();
    }

    @Test
    @DisplayName("fresh conversation is empty and does not exist")
    void unknown_conversationReturnsEmpty() {
        assertThat(store.exists("nope")).isFalse();
        assertThat(store.messages("nope")).isEmpty();
        assertThat(store.snapshot("nope")).isEmpty();
        assertThat(store.lastUpdated("nope")).isNull();
    }

    @Test
    @DisplayName("messages() returns an immutable defensive copy")
    void messages_areDefensiveCopies() {
        store.append("conv-1", ConversationMessage.user("a"));
        List<ConversationMessage> copy = store.messages("conv-1");
        assertThatThrownBy(() -> copy.add(ConversationMessage.user("b")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(store.messages("conv-1")).hasSize(1);
    }

    @Test
    @DisplayName("save() replaces the history")
    void save_replacesHistory() {
        store.append("conv-1", ConversationMessage.user("old"));
        store.save("conv-1", List.of(ConversationMessage.user("new"), ConversationMessage.ai("x")));
        assertThat(store.messages("conv-1")).hasSize(2);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("new");
    }

    @Test
    @DisplayName("trim() keeps only the most recent messages")
    void trim_keepsMostRecent() {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        store.trim("conv-1", 5);
        assertThat(store.messages("conv-1")).hasSize(5);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
        assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");
    }

    @Test
    @DisplayName("clear() removes the conversation")
    void clear_removesConversation() {
        store.append("conv-1", ConversationMessage.user("a"));
        store.clear("conv-1");
        assertThat(store.exists("conv-1")).isFalse();
        assertThat(store.count()).isZero();
    }

    @Test
    @DisplayName("lastUpdated() never goes backwards for a conversation")
    void lastUpdated_neverGoesBackwards() {
        store.append("conv-1", ConversationMessage.user("a"));
        Instant first = store.lastUpdated("conv-1");
        store.append("conv-1", ConversationMessage.user("b"));
        Instant second = store.lastUpdated("conv-1");
        assertThat(second).isNotNull().isAfterOrEqualTo(first);
    }

    @Test
    @DisplayName("conversationIds() exposes created ids")
    void conversationIds_exposesKeys() {
        store.append("c1", ConversationMessage.user("a"));
        store.append("c2", ConversationMessage.user("b"));
        assertThat(store.conversationIds()).containsExactlyInAnyOrder("c1", "c2");
        assertThat(store.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("null/blank conversationId is rejected")
    void blankId_rejected() {
        assertThatThrownBy(() -> store.append(null, ConversationMessage.user("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.append("  ", ConversationMessage.user("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("store evicts oldest conversations once above capacity")
    void capacity_evictsOldest() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < InMemoryConversationStore.MAX_CONVERSATIONS + 10; i++) {
            String id = "conv-" + i;
            ids.add(id);
            store.append(id, ConversationMessage.user("payload"));
        }
        assertThat(store.count()).isLessThanOrEqualTo(InMemoryConversationStore.MAX_CONVERSATIONS);
        // The oldest ids should have been evicted first.
        assertThat(store.exists("conv-0")).isFalse();
        assertThat(store.exists(ids.get(ids.size() - 1))).isTrue();
    }
}