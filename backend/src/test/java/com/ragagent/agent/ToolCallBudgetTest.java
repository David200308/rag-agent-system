package com.ragagent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallBudgetTest {

    @Test
    void tryConsume_underLimit_returnsTrue() {
        ToolCallBudget budget = new ToolCallBudget();
        budget.reset();

        assertThat(budget.tryConsume()).isTrue();

        budget.clear();
    }

    @Test
    void tryConsume_exceedsLimit_returnsFalse() {
        ToolCallBudget budget = new ToolCallBudget();
        budget.reset();

        for (int i = 0; i < 8; i++) {
            assertThat(budget.tryConsume()).isTrue();
        }
        assertThat(budget.tryConsume()).isFalse();

        budget.clear();
    }

    @Test
    void reset_clearsPreviousCount() {
        ToolCallBudget budget = new ToolCallBudget();
        budget.reset();
        for (int i = 0; i < 8; i++) {
            budget.tryConsume();
        }
        assertThat(budget.tryConsume()).isFalse();

        budget.reset();

        assertThat(budget.tryConsume()).isTrue();
        budget.clear();
    }

    @Test
    void clear_thenReset_startsFreshCount() {
        ToolCallBudget budget = new ToolCallBudget();
        budget.reset();
        budget.tryConsume();
        budget.clear();

        budget.reset();
        for (int i = 0; i < 8; i++) {
            assertThat(budget.tryConsume()).isTrue();
        }
        assertThat(budget.tryConsume()).isFalse();

        budget.clear();
    }
}
