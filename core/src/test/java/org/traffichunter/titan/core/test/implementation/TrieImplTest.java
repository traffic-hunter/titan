package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.traffichunter.titan.core.util.Trie;
import org.traffichunter.titan.core.util.TrieImpl;

/**
 * @author yungwang-o
 */
class TrieImplTest {

    @ParameterizedTest
    @ValueSource(strings = {"/a", "/a/b", "/a/b/c", "/a/b/d"})
    void startWith_success_test(String path) {
        String dq1 = "/a/b/c";
        String dq2 = "/a/b/d";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);
        trie.insert(dq2, dq2);

        boolean isCheck = trie.startsWith(path);

        assertThat(isCheck).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/c", "/c/b", "/aaa/bbb/c", "/ac/bd/d"})
    void startWith_failed_test(String path) {
        String dq1 = "/a/b/c";
        String dq2 = "/a/b/d";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);
        trie.insert(dq2, dq2);

        boolean isCheck = trie.startsWith(path);

        assertThat(isCheck).isFalse();
    }

    @Test
    void get_success_test() {
        String dq1 = "/a/b/c";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);

        String resultDq = trie.get("/a/b/c");
        if(resultDq == null) {
            throw new IllegalArgumentException("No such trie path: " + dq1);
        }

        assertThat(resultDq).isEqualTo(dq1);
    }

    @Test
    void get_failed_test() {
        String dq1 = "/a/b/c";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);

        String resultDq = trie.get("/a/b");

        assertThat(resultDq).isNull();
    }

    @ParameterizedTest
    @CsvSource({"/*, 5", "/a/*, 4", "/a/b/*, 4", "/b/*, 1"})
    void get_all_success_test(String path, int result) {
        String dq1 = "/b/b/a";
        String dq2 = "/a/b/b";
        String dq3 = "/a/b/c";
        String dq4 = "/a/b/d";
        String dq5 = "/a/b/e";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);
        trie.insert(dq2, dq2);
        trie.insert(dq3, dq3);
        trie.insert(dq4, dq4);
        trie.insert(dq5, dq5);

        List<String> dqs = trie.searchAll(path);

        assertThat(dqs).hasSize(result);
        assertThat(dqs).containsExactlyInAnyOrderElementsOf(getExpectedKeys(path));
    }

    @Test
    void searchAll_with_nested_wildcard_returns_descendants_without_prefix_node() {
        String parent = "/a/b";
        String child = "/a/b/c";
        String grandChild = "/a/b/c/d";
        String sibling = "/a/c";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(parent, parent);
        trie.insert(child, child);
        trie.insert(grandChild, grandChild);
        trie.insert(sibling, sibling);

        List<String> queues = trie.searchAll("/a/b/*");

        assertThat(queues).containsExactlyInAnyOrder("/a/b/c", "/a/b/c/d");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/*/a", "/*/*/a", "/a/*/a", "/a/bc*", "abc*"})
    void getAll_failed_test(String path) {
        String dq1 = "/a/b/a";
        String dq2 = "/a/b/b";
        String dq3 = "/a/b/c";
        String dq4 = "/a/b/d";
        String dq5 = "/a/b/e";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);
        trie.insert(dq2, dq2);
        trie.insert(dq3, dq3);
        trie.insert(dq4, dq4);
        trie.insert(dq5, dq5);

        assertThatThrownBy(() -> trie.searchAll(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix must");
    }

    @Test
    void remove_test() {
        String dq1 = "/a/b/a";
        String dq2 = "/a/b/b";
        String dq3 = "/a/b/c";
        String dq4 = "/a/b/d";
        String dq5 = "/a/b/e";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);
        trie.insert(dq2, dq2);
        trie.insert(dq3, dq3);
        trie.insert(dq4, dq4);
        trie.insert(dq5, dq5);

        trie.remove("/a/b/a");
        trie.remove("/a/b/b");

        List<String> dqs = trie.searchAll("/*");
        assertThat(dqs).hasSize(3);
        assertThat(dqs).containsExactlyInAnyOrder("/a/b/c", "/a/b/d", "/a/b/e");
    }

    @Test
    void remove_success_when_sibling_nodes_still_exist_test() {
        String dq1 = "/a/b/a";
        String dq2 = "/a/b/b";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);
        trie.insert(dq2, dq2);

        trie.remove("/a/b/a");

        assertThat(trie.get("/a/b/a")).isNull();
        assertThat(trie.get("/a/b/b")).isSameAs(dq2);
    }

    @Test
    void remove_failed_when_path_does_not_exist_test() {
        String dq1 = "/a/b/a";

        Trie<String> trie = new TrieImpl<>();

        trie.insert(dq1, dq1);

        assertThatThrownBy(() -> trie.remove("/a/b/c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No such word: /a/b/c");
    }

    @Test
    void computeIfAbsent_test() {
        String path = "/a/b/c";

        Trie<String> trie = new TrieImpl<>();
        String dq = trie.computeIfAbsent(path, key -> key);

        assertThat(trie.get(path)).isSameAs(dq);
        assertThat(dq).isEqualTo(path);
    }

    @Test
    void computeIfAbsent_returns_existing_value_without_invoking_mapping_function() {
        Trie<String> trie = new TrieImpl<>();
        AtomicInteger calls = new AtomicInteger();

        trie.insert("/a/b/c", "first");
        String value = trie.computeIfAbsent("/a/b/c", key -> {
            calls.incrementAndGet();
            return "second";
        });

        assertThat(value).isEqualTo("first");
        assertThat(trie.get("/a/b/c")).isEqualTo("first");
        assertThat(calls).hasValue(0);
    }

    @Test
    void computeIfAbsent_creates_value_once_when_called_concurrently() throws Exception {
        Trie<String> trie = new TrieImpl<>();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        int taskCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return trie.computeIfAbsent("/a/b/c", key -> "value-" + calls.incrementAndGet());
                }));
            }

            start.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo("value-1");
            }
            assertThat(calls).hasValue(1);
            assertThat(trie.get("/a/b/c")).isEqualTo("value-1");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void putIfAbsent_inserts_value_and_returns_null_when_missing() {
        Trie<String> trie = new TrieImpl<>();

        String previous = trie.putIfAbsent("/a/b/c", "first");

        assertThat(previous).isNull();
        assertThat(trie.get("/a/b/c")).isEqualTo("first");
    }

    @Test
    void putIfAbsent_keeps_existing_value_and_returns_previous_value() {
        Trie<String> trie = new TrieImpl<>();

        trie.insert("/a/b/c", "first");
        String previous = trie.putIfAbsent("/a/b/c", "second");

        assertThat(previous).isEqualTo("first");
        assertThat(trie.get("/a/b/c")).isEqualTo("first");
    }

    private List<String> getExpectedKeys(String path) {
        return switch (path) {
            case "/*" -> List.of("/b/b/a", "/a/b/b", "/a/b/c", "/a/b/d", "/a/b/e");
            case "/a/*", "/a/b/*" -> List.of("/a/b/b", "/a/b/c", "/a/b/d", "/a/b/e");
            case "/b/*" -> List.of("/b/b/a");
            default -> List.of();
        };
    }
}
