package com.example.jtorrent.automation;

/**
 * Rule that stops seeding when ratio threshold is reached.
 */
public class RatioStopRule extends AutomationRule {

    private double targetRatio;

    public RatioStopRule(String name, double targetRatio) {
        super(name);
        this.targetRatio = targetRatio;
    }

    public double getTargetRatio() {
        return targetRatio;
    }

    public void setTargetRatio(double targetRatio) {
        this.targetRatio = targetRatio;
    }

    @Override
    public boolean evaluate(RuleContext context) {
        return context.isComplete() && context.getRatio() >= targetRatio;
    }

    @Override
    public void execute(RuleContext context) throws Exception {
        // Action: stop torrent
        // Implementation would call TorrentSession.stop() or similar
        System.out.println(String.format(
                "Stopping torrent - ratio %.2f reached target %.2f",
                context.getRatio(), targetRatio));
    }

    @Override
    public String toString() {
        return String.format("RatioStopRule[%s, ratio=%.2f]", getName(), targetRatio);
    }
}
