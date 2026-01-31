package com.example.jtorrent.automation;

/**
 * Rule that stops seeding after specified time.
 */
public class SeedTimeStopRule extends AutomationRule {

    private long targetSecondsSeconds;

    public SeedTimeStopRule(String name, long targetSeconds) {
        super(name);
        this.targetSecondsSeconds = targetSeconds;
    }

    public long getTargetSeconds() {
        return targetSecondsSeconds;
    }

    public void setTargetSeconds(long targetSeconds) {
        this.targetSecondsSeconds = targetSeconds;
    }

    @Override
    public boolean evaluate(RuleContext context) {
        return context.isComplete() &&
                context.getSeedingTimeSeconds() >= targetSecondsSeconds;
    }

    @Override
    public void execute(RuleContext context) throws Exception {
        System.out.println(String.format(
                "Stopping torrent - seeded for %d seconds (target %d)",
                context.getSeedingTimeSeconds(), targetSecondsSeconds));
    }

    @Override
    public String toString() {
        return String.format("SeedTimeStopRule[%s, time=%ds]", getName(), targetSecondsSeconds);
    }
}
