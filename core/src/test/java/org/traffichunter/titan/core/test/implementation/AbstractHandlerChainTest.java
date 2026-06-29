package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.channel.chain.AbstractHandlerChain;
import org.traffichunter.titan.core.util.channel.chain.LinkedHandlerChainNode;

class AbstractHandlerChainTest {

    @Test
    void runs_nodes_in_insertion_order() {
        TestChain chain = new TestChain()
                .add(values -> values.add("first"))
                .add(values -> values.add("second"));

        List<String> values = new ArrayList<>();

        chain.sparkChainHandler(values).join();

        assertThat(values).containsExactly("first", "second");
    }

    @Test
    void can_insert_node_at_front() {
        TestChain chain = new TestChain()
                .add(values -> values.add("second"))
                .addFirst(values -> values.add("first"));

        List<String> values = new ArrayList<>();

        chain.sparkChainHandler(values).join();

        assertThat(values).containsExactly("first", "second");
    }

    @Test
    void close_closes_nodes_and_suppresses_later_failures() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        TestChain chain = new TestChain()
                .add(values -> values.add("ok"))
                .addClosingFailure(first)
                .addClosingFailure(second);

        assertThatThrownBy(chain::close)
                .isSameAs(first)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(second));
    }

    private static final class TestChain extends AbstractHandlerChain<TestNode, List<String>> {

        private TestChain() {
            super(new TestNode(values -> { }, null));
        }

        @CanIgnoreReturnValue
        private TestChain add(Consumer<List<String>> action) {
            add(new TestNode(action, null));
            return this;
        }

        @CanIgnoreReturnValue
        private TestChain addFirst(Consumer<List<String>> action) {
            addFirst(new TestNode(action, null));
            return this;
        }

        @CanIgnoreReturnValue
        private TestChain addClosingFailure(RuntimeException closeFailure) {
            add(new TestNode(values -> { }, closeFailure));
            return this;
        }
    }

    private static final class TestNode implements LinkedHandlerChainNode<List<String>>, AutoCloseable {

        private final Consumer<List<String>> action;
        private final @Nullable RuntimeException closeFailure;
        private @Nullable LinkedHandlerChainNode<List<String>> next;

        private TestNode(Consumer<List<String>> action, @Nullable RuntimeException closeFailure) {
            this.action = action;
            this.closeFailure = closeFailure;
        }

        @Override
        public @Nullable LinkedHandlerChainNode<List<String>> next() {
            return next;
        }

        @Override
        public void next(@Nullable LinkedHandlerChainNode<List<String>> next) {
            this.next = next;
        }

        @Override
        public CompletableFuture<Void> next(List<String> context) {
            LinkedHandlerChainNode<List<String>> chain = next;
            if (chain == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (chain instanceof TestNode node) {
                node.action.accept(context);
                return node.sparkChainHandler(context);
            }
            return chain.sparkChainHandler(context);
        }

        @Override
        public void close() {
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
