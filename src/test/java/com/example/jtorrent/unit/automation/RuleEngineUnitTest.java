package com.example.jtorrent.unit.automation;

import com.example.jtorrent.automation.AutomationRule;
import com.example.jtorrent.automation.RuleContext;
import com.example.jtorrent.automation.RuleEngine;
import com.example.jtorrent.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleEngine.
 * Tests automation rule management and execution.
 */
class RuleEngineUnitTest {

    private RuleEngine ruleEngine;
    private Logger logger;
    private TestExecutor executor;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("RuleEngineTest");
        executor = new TestExecutor();
        ruleEngine = new RuleEngine(logger, executor);
    }

    @Nested
    class RuleManagementTests {

        @Test
        void addRuleIncreasesRuleCount() {
            assertEquals(0, ruleEngine.getRules().size());

            ruleEngine.addRule(new SimpleRule("Test Rule"));
            assertEquals(1, ruleEngine.getRules().size());
        }

        @Test
        void addMultipleRules() {
            ruleEngine.addRule(new SimpleRule("Rule 1"));
            ruleEngine.addRule(new SimpleRule("Rule 2"));
            ruleEngine.addRule(new SimpleRule("Rule 3"));

            assertEquals(3, ruleEngine.getRules().size());
        }

        @Test
        void removeRuleDecreasesRuleCount() {
            SimpleRule rule = new SimpleRule("Test Rule");
            ruleEngine.addRule(rule);
            assertEquals(1, ruleEngine.getRules().size());

            ruleEngine.removeRule(rule);
            assertEquals(0, ruleEngine.getRules().size());
        }

        @Test
        void removeNonexistentRuleDoesNothing() {
            ruleEngine.addRule(new SimpleRule("Rule 1"));
            ruleEngine.removeRule(new SimpleRule("Rule 2"));

            assertEquals(1, ruleEngine.getRules().size());
        }

        @Test
        void getRulesReturnsCopy() {
            ruleEngine.addRule(new SimpleRule("Test Rule"));
            List<AutomationRule> rules1 = ruleEngine.getRules();
            List<AutomationRule> rules2 = ruleEngine.getRules();

            assertNotSame(rules1, rules2);
        }

        @Test
        void getRulesReturnsEmptyListInitially() {
            List<AutomationRule> rules = ruleEngine.getRules();
            assertNotNull(rules);
            assertTrue(rules.isEmpty());
        }
    }

    @Nested
    class RuleEvaluationTests {

        @Test
        void evaluateRulesReturnsTrueCount() {
            ruleEngine.addRule(new SimpleRule("Rule 1", true));
            ruleEngine.addRule(new SimpleRule("Rule 2", false));
            ruleEngine.addRule(new SimpleRule("Rule 3", true));

            RuleContext context = createTestContext();
            int executed = ruleEngine.evaluateRules(context);

            assertEquals(2, executed);
        }

        @Test
        void evaluateRulesSkipsDisabledRules() {
            SimpleRule enabledRule = new SimpleRule("Enabled", true);
            SimpleRule disabledRule = new SimpleRule("Disabled", true);
            disabledRule.setEnabled(false);

            ruleEngine.addRule(enabledRule);
            ruleEngine.addRule(disabledRule);

            RuleContext context = createTestContext();
            int executed = ruleEngine.evaluateRules(context);

            assertEquals(1, executed);
        }

        @Test
        void evaluateRulesRespectsRulePriority() {
            AtomicInteger order = new AtomicInteger(0);

            OrderTrackingRule lowPriorityRule = new OrderTrackingRule("Low", 0, order);
            OrderTrackingRule highPriorityRule = new OrderTrackingRule("High", 10, order);

            ruleEngine.addRule(lowPriorityRule);
            ruleEngine.addRule(highPriorityRule);

            RuleContext context = createTestContext();
            ruleEngine.evaluateRules(context);

            // High priority rule should execute first (get order 0)
            assertEquals(0, highPriorityRule.getExecutionOrder());
            assertEquals(1, lowPriorityRule.getExecutionOrder());
        }

        @Test
        void evaluateRulesWithNoRulesReturnsZero() {
            RuleContext context = createTestContext();
            int executed = ruleEngine.evaluateRules(context);
            assertEquals(0, executed);
        }

        @Test
        void evaluateRulesWithAllFalseConditionsReturnsZero() {
            ruleEngine.addRule(new SimpleRule("Rule 1", false));
            ruleEngine.addRule(new SimpleRule("Rule 2", false));

            RuleContext context = createTestContext();
            int executed = ruleEngine.evaluateRules(context);
            assertEquals(0, executed);
        }

        @Test
        void evaluateRulesCatchesExceptions() {
            ruleEngine.addRule(new ThrowingRule("Throwing Rule"));
            ruleEngine.addRule(new SimpleRule("Normal Rule", true));

            RuleContext context = createTestContext();

            // Should not throw, and should continue to next rule
            int executed = ruleEngine.evaluateRules(context);

            // Only the normal rule should count as executed
            assertEquals(1, executed);
        }

        @Test
        void evaluateRulesCallsExecutor() {
            ruleEngine.addRule(new SimpleRule("Test Rule", true));

            RuleContext context = createTestContext();
            ruleEngine.evaluateRules(context);

            assertEquals(1, executor.getExecutionCount());
        }
    }

    @Nested
    class EvaluateAllTorrentsTests {

        @Test
        void evaluateAllTorrentsWithMultipleContexts() {
            ruleEngine.addRule(new SimpleRule("Test Rule", true));

            List<RuleContext> contexts = List.of(
                    createTestContext(),
                    createTestContext(),
                    createTestContext());

            int totalExecuted = ruleEngine.evaluateAllTorrents(contexts);

            assertEquals(3, totalExecuted);
        }

        @Test
        void evaluateAllTorrentsWithEmptyListReturnsZero() {
            ruleEngine.addRule(new SimpleRule("Test Rule", true));

            int executed = ruleEngine.evaluateAllTorrents(List.of());
            assertEquals(0, executed);
        }

        @Test
        void evaluateAllTorrentsAccumulatesCount() {
            ruleEngine.addRule(new SimpleRule("Rule 1", true));
            ruleEngine.addRule(new SimpleRule("Rule 2", true));

            List<RuleContext> contexts = List.of(
                    createTestContext(),
                    createTestContext());

            int totalExecuted = ruleEngine.evaluateAllTorrents(contexts);

            // 2 rules x 2 contexts = 4 executions
            assertEquals(4, totalExecuted);
        }
    }

    @Nested
    class NullExecutorTests {

        @Test
        void evaluateRulesWithNullExecutorDoesNotThrow() {
            RuleEngine engineWithNullExecutor = new RuleEngine(logger, null);
            engineWithNullExecutor.addRule(new SimpleRule("Test Rule", true));

            RuleContext context = createTestContext();
            assertDoesNotThrow(() -> engineWithNullExecutor.evaluateRules(context));
        }

        @Test
        void evaluateRulesWithNullExecutorStillExecutesRule() {
            RuleEngine engineWithNullExecutor = new RuleEngine(logger, null);
            SimpleRule rule = new SimpleRule("Test Rule", true);
            engineWithNullExecutor.addRule(rule);

            RuleContext context = createTestContext();
            int executed = engineWithNullExecutor.evaluateRules(context);

            assertEquals(1, executed);
            assertTrue(rule.wasExecuted());
        }
    }

    // Helper classes

    private RuleContext createTestContext() {
        return new RuleContext(new byte[20], null, null);
    }

    private static class SimpleRule extends AutomationRule {
        private final boolean shouldTrigger;
        private boolean executed = false;

        SimpleRule(String name) {
            this(name, true);
        }

        SimpleRule(String name, boolean shouldTrigger) {
            super(name);
            this.shouldTrigger = shouldTrigger;
        }

        @Override
        public boolean evaluate(RuleContext context) {
            return shouldTrigger;
        }

        @Override
        public void execute(RuleContext context) {
            executed = true;
        }

        boolean wasExecuted() {
            return executed;
        }
    }

    private static class OrderTrackingRule extends AutomationRule {
        private final AtomicInteger orderCounter;
        private int executionOrder = -1;

        OrderTrackingRule(String name, int priority, AtomicInteger orderCounter) {
            super(name);
            setPriority(priority);
            this.orderCounter = orderCounter;
        }

        @Override
        public boolean evaluate(RuleContext context) {
            return true;
        }

        @Override
        public void execute(RuleContext context) {
            executionOrder = orderCounter.getAndIncrement();
        }

        int getExecutionOrder() {
            return executionOrder;
        }
    }

    private static class ThrowingRule extends AutomationRule {
        ThrowingRule(String name) {
            super(name);
        }

        @Override
        public boolean evaluate(RuleContext context) {
            return true;
        }

        @Override
        public void execute(RuleContext context) {
            throw new RuntimeException("Intentional exception for testing");
        }
    }

    private static class TestExecutor implements RuleEngine.RuleExecutor {
        private int executionCount = 0;

        @Override
        public void executeRule(AutomationRule rule, RuleContext context) {
            executionCount++;
        }

        int getExecutionCount() {
            return executionCount;
        }
    }
}
