package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.channel.chain.AbstractLinkedHandlerChain;
import org.traffichunter.titan.core.util.channel.chain.LinkedNode;

class AbstractLinkedHandlerChainTest {

    @Test
    void runs_nodes_in_insertion_order() {
        TestChain chain = new TestChain()
                .add(values -> values.add("first"))
                .add(values -> values.add("second"));

        List<String> values = new ArrayList<>();

        chain.execute(values).join();

        assertThat(values).containsExactly("first", "second");
    }

    @Test
    void can_insert_node_at_front() {
        TestChain chain = new TestChain()
                .add(values -> values.add("second"))
                .addFirst(values -> values.add("first"));

        List<String> values = new ArrayList<>();

        chain.execute(values).join();

        assertThat(values).containsExactly("first", "second");
    }

    @Test
    void clear_removes_nodes_and_allows_chain_reuse() {
        List<String> values = new ArrayList<>();
        TestChain chain = new TestChain()
                .add(value -> value.add("removed-first"))
                .add(value -> value.add("removed-last"));

        chain.clear();
        chain.add(value -> value.add("new"));
        chain.execute(values).join();

        assertThat(values).containsExactly("new");
    }

    private static final class TestChain extends AbstractLinkedHandlerChain<TestNode> {

        private TestChain() {
            super(new TestNode(values -> { }));
        }

        @CanIgnoreReturnValue
        private TestChain add(Consumer<List<String>> action) {
            add(new TestNode(action));
            return this;
        }

        @CanIgnoreReturnValue
        private TestChain addFirst(Consumer<List<String>> action) {
            addFirst(new TestNode(action));
            return this;
        }

        private CompletableFuture<Void> execute(List<String> context) {
            return head().execute(context);
        }
    }

    private static final class TestNode implements LinkedNode<TestNode> {

        private final Consumer<List<String>> action;
        private @Nullable TestNode next;

        private TestNode(Consumer<List<String>> action) {
            this.action = action;
        }

        @Override
        public @Nullable TestNode next() {
            return next;
        }

        @Override
        public void next(@Nullable TestNode next) {
            this.next = next;
        }

        private CompletableFuture<Void> execute(List<String> context) {
            TestNode chain = next;
            if (chain == null) {
                return CompletableFuture.completedFuture(null);
            }
            chain.action.accept(context);
            return chain.execute(context);
        }
    }
}
