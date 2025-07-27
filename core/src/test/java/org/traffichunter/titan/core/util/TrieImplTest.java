package org.traffichunter.titan.core.util;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.traffichunter.titan.core.queue.DispatcherQueue;
import org.traffichunter.titan.servicediscovery.RoutingKey;

/**
 * @author yungwang-o
 */
class TrieImplTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "a.b", "a.b.c", "a.b.d"})
    void startWith_success_test(String path) {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 100);
        DispatcherQueue dq2 = DispatcherQueue.get(RoutingKey.create("a.b.d"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);
        trie.insert(dq2.route().getKey(), dq2);

        boolean isCheck = trie.startsWith(path);

        assertThat(isCheck).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"c", "c.b", "aaa.bbb.c", "ac.bd.d"})
    void startWith_failed_test(String path) {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 100);
        DispatcherQueue dq2 = DispatcherQueue.get(RoutingKey.create("a.b.d"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);
        trie.insert(dq2.route().getKey(), dq2);

        boolean isCheck = trie.startsWith(path);

        assertThat(isCheck).isFalse();
    }

    @Test
    void get_success_test() {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);

        DispatcherQueue resultDq = trie.search("a.b.c")
                .orElseThrow(() -> new IllegalArgumentException("Not found route"));

        assertThat(resultDq.route().getKey()).isEqualTo(dq1.route().getKey());
    }

    @Test
    void get_failed_test() {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);

        Optional<DispatcherQueue> resultDq = trie.search("a.b");

        assertThat(resultDq.isPresent()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"*, 5", "a.*, 4", "a.b.*, 4", "b.*, 1"})
    void search_all_success_test(String path, int result) {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("b.b.a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.get(RoutingKey.create("a.b.b"), 1);
        DispatcherQueue dq3 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 1);
        DispatcherQueue dq4 = DispatcherQueue.get(RoutingKey.create("a.b.d"), 1);
        DispatcherQueue dq5 = DispatcherQueue.get(RoutingKey.create("a.b.e"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);
        trie.insert(dq2.route().getKey(), dq2);
        trie.insert(dq3.route().getKey(), dq3);
        trie.insert(dq4.route().getKey(), dq4);
        trie.insert(dq5.route().getKey(), dq5);

        List<DispatcherQueue> dqs = trie.searchAll(path);

        assertThat(dqs).hasSize(result);
        assertThat(dqs)
                .extracting(dq -> dq.route().getKey())
                .containsExactlyInAnyOrderElementsOf(getExpectedKeys(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*.a", "*.*.a", "a.*.a"})
    void searchAll_failed_test(String path) {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("a.b.a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.get(RoutingKey.create("a.b.b"), 1);
        DispatcherQueue dq3 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 1);
        DispatcherQueue dq4 = DispatcherQueue.get(RoutingKey.create("a.b.d"), 1);
        DispatcherQueue dq5 = DispatcherQueue.get(RoutingKey.create("a.b.e"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);
        trie.insert(dq2.route().getKey(), dq2);
        trie.insert(dq3.route().getKey(), dq3);
        trie.insert(dq4.route().getKey(), dq4);
        trie.insert(dq5.route().getKey(), dq5);

        assertThatThrownBy(() -> trie.searchAll(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must end with '*'");
    }

    @Test
    void remove_test() {
        DispatcherQueue dq1 = DispatcherQueue.get(RoutingKey.create("a.b.a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.get(RoutingKey.create("a.b.b"), 1);
        DispatcherQueue dq3 = DispatcherQueue.get(RoutingKey.create("a.b.c"), 1);
        DispatcherQueue dq4 = DispatcherQueue.get(RoutingKey.create("a.b.d"), 1);
        DispatcherQueue dq5 = DispatcherQueue.get(RoutingKey.create("a.b.e"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().getKey(), dq1);
        trie.insert(dq2.route().getKey(), dq2);
        trie.insert(dq3.route().getKey(), dq3);
        trie.insert(dq4.route().getKey(), dq4);
        trie.insert(dq5.route().getKey(), dq5);

        trie.remove("a.b.a");
        trie.remove("a.b.b");

        List<DispatcherQueue> dqs = trie.searchAll("*");
        assertThat(dqs).hasSize(3);
        assertThat(dqs)
                .extracting(dq -> dq.route().getKey())
                .containsExactlyInAnyOrder("a.b.c", "a.b.d", "a.b.e");
    }

    private List<String> getExpectedKeys(String path) {
        return switch (path) {
            case "*" -> List.of("b.b.a", "a.b.b", "a.b.c", "a.b.d", "a.b.e");
            case "a.*", "a.b.*" -> List.of("a.b.b", "a.b.c", "a.b.d", "a.b.e");
            case "b.*" -> List.of("b.b.a");
            default -> List.of();
        };
    }
}