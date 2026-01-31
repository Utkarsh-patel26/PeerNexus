package com.example.jtorrent.automation;

/**
 * Rule that pauses downloads when disk space is low.
 */
public class DiskSpaceRule extends AutomationRule {

    private long minimumBytesRequired;

    public DiskSpaceRule(String name, long minimumBytes) {
        super(name);
        this.minimumBytesRequired = minimumBytes;
    }

    public long getMinimumBytesRequired() {
        return minimumBytesRequired;
    }

    public void setMinimumBytesRequired(long minimumBytes) {
        this.minimumBytesRequired = minimumBytes;
    }

    @Override
    public boolean evaluate(RuleContext context) {
        return context.getDiskSpaceAvailable() < minimumBytesRequired;
    }

    @Override
    public void execute(RuleContext context) throws Exception {
        System.out.println(String.format(
                "Pausing torrent - disk space low: %d bytes available (need %d)",
                context.getDiskSpaceAvailable(), minimumBytesRequired));
    }

    @Override
    public String toString() {
        return String.format("DiskSpaceRule[%s, min=%d bytes]",
                getName(), minimumBytesRequired);
    }
}
