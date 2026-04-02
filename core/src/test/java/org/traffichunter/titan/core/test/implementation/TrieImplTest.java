package org.traffichunter.titan.core.test.implementation;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.traffichunter.titan.core.message.dispatcher.DispatcherQueue;
import org.traffichunter.titan.core.util.Destination;
import org.traffichunter.titan.core.util.Trie;
import org.traffichunter.titan.core.util.TrieImpl;

/**
 * @author yungwang-o
 */
class TrieImplTest {

    @ParameterizedTest
    @ValueSource(strings = {"/a", "/a/b", "/a/b/c", "/a/b/d"})
    void startWith_success_test(String path) {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/c"), 100);
        DispatcherQueue dq2 = DispatcherQueue.create(Destination.create("/a/b/d"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);
        trie.insert(dq2.route().path(), dq2);

        boolean isCheck = trie.startsWith(path);

        assertThat(isCheck).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/c", "/c/b", "/aaa/bbb/c", "/ac/bd/d"})
    void startWith_failed_test(String path) {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/c"), 100);
        DispatcherQueue dq2 = DispatcherQueue.create(Destination.create("/a/b/d"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);
        trie.insert(dq2.route().path(), dq2);

        boolean isCheck = trie.startsWith(path);

        assertThat(isCheck).isFalse();
    }

    @Test
    void get_success_test() {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/c"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);

        DispatcherQueue resultDq = trie.get("/a/b/c");
        if(resultDq == null) {
            throw new IllegalArgumentException("No such trie path: " + dq1.route().path());
        }

        assertThat(resultDq.route().path()).isEqualTo(dq1.route().path());
    }

    @Test
    void get_failed_test() {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/c"), 100);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);

        DispatcherQueue resultDq = trie.get("/a/b");

        assertThat(resultDq).isNull();
    }

    @ParameterizedTest
    @CsvSource({"/*, 5", "/a/*, 4", "/a/b/*, 4", "/b/*, 1"})
    void get_all_success_test(String path, int result) {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/b/b/a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.create(Destination.create("/a/b/b"), 1);
        DispatcherQueue dq3 = DispatcherQueue.create(Destination.create("/a/b/c"), 1);
        DispatcherQueue dq4 = DispatcherQueue.create(Destination.create("/a/b/d"), 1);
        DispatcherQueue dq5 = DispatcherQueue.create(Destination.create("/a/b/e"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);
        trie.insert(dq2.route().path(), dq2);
        trie.insert(dq3.route().path(), dq3);
        trie.insert(dq4.route().path(), dq4);
        trie.insert(dq5.route().path(), dq5);

        List<DispatcherQueue> dqs = trie.searchAll(path);

        assertThat(dqs).hasSize(result);
        assertThat(dqs)
                .extracting(dq -> dq.route().path())
                .containsExactlyInAnyOrderElementsOf(getExpectedKeys(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/*/a", "/*/*/a", "/a/*/a", "/a/bc*", "abc*"})
    void getAll_failed_test(String path) {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.create(Destination.create("/a/b/b"), 1);
        DispatcherQueue dq3 = DispatcherQueue.create(Destination.create("/a/b/c"), 1);
        DispatcherQueue dq4 = DispatcherQueue.create(Destination.create("/a/b/d"), 1);
        DispatcherQueue dq5 = DispatcherQueue.create(Destination.create("/a/b/e"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);
        trie.insert(dq2.route().path(), dq2);
        trie.insert(dq3.route().path(), dq3);
        trie.insert(dq4.route().path(), dq4);
        trie.insert(dq5.route().path(), dq5);

        assertThatThrownBy(() -> trie.searchAll(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix must");
    }

    @Test
    void remove_test() {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.create(Destination.create("/a/b/b"), 1);
        DispatcherQueue dq3 = DispatcherQueue.create(Destination.create("/a/b/c"), 1);
        DispatcherQueue dq4 = DispatcherQueue.create(Destination.create("/a/b/d"), 1);
        DispatcherQueue dq5 = DispatcherQueue.create(Destination.create("/a/b/e"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);
        trie.insert(dq2.route().path(), dq2);
        trie.insert(dq3.route().path(), dq3);
        trie.insert(dq4.route().path(), dq4);
        trie.insert(dq5.route().path(), dq5);

        trie.remove("/a/b/a");
        trie.remove("/a/b/b");

        List<DispatcherQueue> dqs = trie.searchAll("/*");
        assertThat(dqs).hasSize(3);
        assertThat(dqs)
                .extracting(dq -> dq.route().path())
                .containsExactlyInAnyOrder("/a/b/c", "/a/b/d", "/a/b/e");
    }

    @Test
    void remove_success_when_sibling_nodes_still_exist_test() {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/a"), 1);
        DispatcherQueue dq2 = DispatcherQueue.create(Destination.create("/a/b/b"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);
        trie.insert(dq2.route().path(), dq2);

        trie.remove("/a/b/a");

        assertThat(trie.get("/a/b/a")).isNull();
        assertThat(trie.get("/a/b/b")).isSameAs(dq2);
    }

    @Test
    void remove_failed_when_path_does_not_exist_test() {
        DispatcherQueue dq1 = DispatcherQueue.create(Destination.create("/a/b/a"), 1);

        Trie<DispatcherQueue> trie = new TrieImpl<>();

        trie.insert(dq1.route().path(), dq1);

        assertThatThrownBy(() -> trie.remove("/a/b/c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No such word: /a/b/c");
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
