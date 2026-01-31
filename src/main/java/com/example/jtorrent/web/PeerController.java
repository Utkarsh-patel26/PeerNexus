package com.example.jtorrent.web;

import com.example.jtorrent.core.ActivePeer;
import com.example.jtorrent.core.TorrentSessionManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PeerController {

    private final TorrentSessionManager sessionManager;

    public PeerController(TorrentSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String getPeers(String hash) {
        try {
            var session = sessionManager.getSession(hash);
            if (session == null) {
                return JsonResponse.error("Torrent not found");
            }

            List<ActivePeer> peers = session.getActivePeersSnapshot();
            List<Map<String, Object>> peerList = peers.stream()
                    .map(this::toJson)
                    .collect(Collectors.toList());

            return JsonResponse.success(Map.of(
                    "hash", hash,
                    "count", peerList.size(),
                    "peers", peerList));
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    public String getAllPeers() {
        try {
            List<Map<String, Object>> allPeers = sessionManager.getAllSessions().stream()
                    .flatMap(session -> {
                        String hash = session.getTorrentFile().infoHashHex();
                        return session.getActivePeersSnapshot().stream()
                                .map(peer -> {
                                    Map<String, Object> json = new java.util.HashMap<>(toJson(peer));
                                    json.put("torrentHash", hash);
                                    return json;
                                });
                    })
                    .collect(Collectors.toList());

            return JsonResponse.success(Map.of("count", allPeers.size(), "peers", allPeers));
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    private Map<String, Object> toJson(ActivePeer peer) {
        var conn = peer.getConnection();
        var stats = peer.getStats();
        return Map.of(
                "address", peer.getAddress().toString(),
                "client", parseClientName(conn.remotePeerId()),
                "downloadRate", peer.getDownloadRate(),
                "uploadRate", stats != null ? stats.getUploadRateBytesPerSec() : 0.0,
                "downloaded", stats != null ? stats.getTotalDownloaded() : 0L,
                "uploaded", stats != null ? stats.getTotalUploaded() : 0L,
                "isSeeder", conn.isPeerSeeder(),
                "isChoking", conn.peerChoking(),
                "isInterested", conn.peerInterested());
    }

    private String parseClientName(byte[] peerId) {
        if (peerId == null || peerId.length < 8) {
            return "Unknown";
        }
        if (peerId[0] == '-' && peerId[7] == '-') {
            return new String(peerId, 1, 2) + " " +
                    String.format("%c.%c.%c.%c", peerId[3], peerId[4], peerId[5], peerId[6]);
        }
        return "Unknown";
    }
}
