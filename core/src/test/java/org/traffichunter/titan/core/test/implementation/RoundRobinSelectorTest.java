package org.traffichunter.titan.core.test.implementation;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.traffichunter.titan.core.util.selector.RoundRobinSelector;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

/**
 * @author yun
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class RoundRobinSelectorTest {

    @Test
    void next_should_select_next_candidate_on_each_call() {
        RoundRobinSelector<String> selector = new RoundRobinSelector<>();
        List<String> candidates = List.of("first", "second", "third");

        assertThat(selector.next(candidates)).isEqualTo("first");
        assertThat(selector.next(candidates)).isEqualTo("second");
        assertThat(selector.next(candidates)).isEqualTo("third");
    }

    @Test
    void next_should_wrap_to_first_candidate_after_last_candidate() {
        RoundRobinSelector<String> selector = new RoundRobinSelector<>();
        List<String> candidates = List.of("first", "second", "third");

        selector.next(candidates);
        selector.next(candidates);
        selector.next(candidates);

        assertThat(selector.next(candidates)).isEqualTo("first");
    }

    @Test
    void next_should_fail_when_candidates_are_empty() {
        RoundRobinSelector<String> selector = new RoundRobinSelector<>();

        assertThatThrownBy(() -> selector.next(List.of()))
                .isExactlyInstanceOf(NoSuchElementException.class);
    }
}
