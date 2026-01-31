package com.example.jtorrent.automation;

import java.util.UUID;

/**
 * Base class for automation rules.
 */
public abstract class AutomationRule {

    private final String id;
    private String name;
    private boolean enabled;
    private int priority; // Higher priority rules execute first

    protected AutomationRule(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.enabled = true;
        this.priority = 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Check if rule condition is met.
     *
     * @param context execution context
     * @return true if condition is satisfied
     */
    public abstract boolean evaluate(RuleContext context);

    /**
     * Execute rule action.
     *
     * @param context execution context
     */
    public abstract void execute(RuleContext context) throws Exception;

    @Override
    public String toString() {
        return String.format("%s[%s, enabled=%s]", getClass().getSimpleName(), name, enabled);
    }
}
