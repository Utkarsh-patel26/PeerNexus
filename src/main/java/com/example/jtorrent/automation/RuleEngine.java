package com.example.jtorrent.automation;

import com.example.jtorrent.logging.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages and executes automation rules.
 */
public class RuleEngine {

    private final Logger logger;
    private final List<AutomationRule> rules;
    private final RuleExecutor executor;

    public RuleEngine(Logger logger, RuleExecutor executor) {
        this.logger = logger;
        this.rules = new CopyOnWriteArrayList<>();
        this.executor = executor;
    }

    /**
     * Add automation rule.
     */
    public void addRule(AutomationRule rule) {
        rules.add(rule);
        logger.info("Added automation rule: %s", rule);
    }

    /**
     * Remove automation rule.
     */
    public void removeRule(AutomationRule rule) {
        rules.remove(rule);
        logger.info("Removed automation rule: %s", rule);
    }

    /**
     * Get all rules.
     */
    public List<AutomationRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * Evaluate and execute rules for a torrent.
     *
     * @param context rule context
     * @return number of rules executed
     */
    public int evaluateRules(RuleContext context) {
        int executed = 0;

        // Sort rules by priority (higher first)
        List<AutomationRule> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparingInt(AutomationRule::getPriority).reversed());

        for (AutomationRule rule : sortedRules) {
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                if (rule.evaluate(context)) {
                    logger.info("Rule triggered: %s", rule.getName());
                    rule.execute(context);

                    // Execute through executor for side effects
                    if (executor != null) {
                        executor.executeRule(rule, context);
                    }

                    executed++;
                }
            } catch (Exception e) {
                logger.error("Rule execution failed: %s - %s", rule.getName(), e.getMessage());
            }
        }

        return executed;
    }

    /**
     * Evaluate rules for all torrents.
     *
     * @param contexts list of torrent contexts
     * @return total rules executed
     */
    public int evaluateAllTorrents(List<RuleContext> contexts) {
        int totalExecuted = 0;

        for (RuleContext context : contexts) {
            totalExecuted += evaluateRules(context);
        }

        if (totalExecuted > 0) {
            logger.info("Executed %d automation rules across %d torrents",
                    totalExecuted, contexts.size());
        }

        return totalExecuted;
    }

    /**
     * Interface for executing rule actions on torrents.
     */
    public interface RuleExecutor {
        void executeRule(AutomationRule rule, RuleContext context) throws Exception;
    }
}
