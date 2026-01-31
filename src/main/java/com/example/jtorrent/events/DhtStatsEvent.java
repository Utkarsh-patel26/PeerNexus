package com.example.jtorrent.events;

/**
 * Event fired with DHT statistics.
 */
public class DhtStatsEvent extends TorrentEvent {

    private final int nodeCount;
    private final int queriesPerSecond;
    private final int peersInDatabase;

    public DhtStatsEvent(int nodeCount, int queriesPerSecond, int peersInDatabase) {
        super();
        this.nodeCount = nodeCount;
        this.queriesPerSecond = queriesPerSecond;
        this.peersInDatabase = peersInDatabase;
    }

    @Override
    public byte[] getInfoHash() {
        return null; // Global event
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getQueriesPerSecond() {
        return queriesPerSecond;
    }

    public int getPeersInDatabase() {
        return peersInDatabase;
    }

    @Override
    public String toString() {
        return String.format("DhtStats[nodes=%d, qps=%d, peers=%d]",
                nodeCount, queriesPerSecond, peersInDatabase);
    }
}
