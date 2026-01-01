package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * IP ban list manager for blocking malicious or unwanted peers.
 * 
 * Supports loading blocklists in various formats:
 * - Simple IP list (one IP per line)
 * - CIDR notation (192.168.0.0/24)
 * - Range format (192.168.0.0-192.168.0.255)
 * - P2P blocklist format (description:start-end)
 */
public class IPBanlistManager {

    private static final Logger logger = Logger.getLogger(IPBanlistManager.class);

    // Individual banned IPs
    private final ConcurrentHashMap<String, Long> bannedIPs = new ConcurrentHashMap<>();

    // IP ranges stored as start-end pairs
    private final List<long[]> bannedRanges = new ArrayList<>();

    // Lock for range access
    private final ReentrantReadWriteLock rangeLock = new ReentrantReadWriteLock();

    private int totalEntries = 0;

    /**
     * Load a blocklist file.
     * 
     * @param file the blocklist file to load
     * @return number of entries loaded
     */
    public int loadBlocklist(File file) {
        if (!file.exists() || !file.isFile()) {
            logger.warn("Blocklist file does not exist: %s", file.getAbsolutePath());
            return 0;
        }

        logger.info("Loading blocklist from: %s", file.getAbsolutePath());
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                if (parseLine(line)) {
                    count++;
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load blocklist: %s", e.getMessage());
        }

        totalEntries += count;
        logger.info("Loaded %d entries from blocklist (total: %d)", count, totalEntries);
        return count;
    }

    /**
     * Parse a blocklist line.
     */
    private boolean parseLine(String line) {
        try {
            // P2P format: description:start-end
            if (line.contains(":") && line.contains("-")) {
                int colonIdx = line.lastIndexOf(':');
                if (colonIdx > 0 && colonIdx < line.length() - 1) {
                    String range = line.substring(colonIdx + 1);
                    return parseRange(range);
                }
            }

            // CIDR format: 192.168.0.0/24
            if (line.contains("/")) {
                return parseCIDR(line);
            }

            // Range format: 192.168.0.0-192.168.0.255
            if (line.contains("-") && !line.contains(":")) {
                return parseRange(line);
            }

            // Single IP
            if (isValidIP(line)) {
                bannedIPs.put(line, System.currentTimeMillis());
                return true;
            }

        } catch (Exception e) {
            logger.debug("Failed to parse blocklist line: %s - %s", line, e.getMessage());
        }

        return false;
    }

    /**
     * Parse a CIDR notation entry.
     */
    private boolean parseCIDR(String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2)
                return false;

            long ip = ipToLong(parts[0]);
            int prefixLen = Integer.parseInt(parts[1]);

            if (prefixLen < 0 || prefixLen > 32)
                return false;

            long mask = prefixLen == 0 ? 0 : 0xFFFFFFFFL << (32 - prefixLen);
            long start = ip & mask;
            long end = start | ~mask & 0xFFFFFFFFL;

            addRange(start, end);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse a range entry (start-end).
     */
    private boolean parseRange(String range) {
        try {
            String[] parts = range.split("-");
            if (parts.length != 2)
                return false;

            long start = ipToLong(parts[0].trim());
            long end = ipToLong(parts[1].trim());

            if (start > end) {
                long tmp = start;
                start = end;
                end = tmp;
            }

            addRange(start, end);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Add a range to the banned ranges list.
     */
    private void addRange(long start, long end) {
        rangeLock.writeLock().lock();
        try {
            bannedRanges.add(new long[] { start, end });
        } finally {
            rangeLock.writeLock().unlock();
        }
    }

    /**
     * Ban a specific IP address.
     * 
     * @param ip the IP address to ban
     */
    public void banIP(String ip) {
        if (isValidIP(ip)) {
            bannedIPs.put(ip, System.currentTimeMillis());
            totalEntries++;
            logger.info("Banned IP: %s", ip);
        }
    }

    /**
     * Ban an IP range.
     * 
     * @param startIP start of range
     * @param endIP   end of range
     */
    public void banRange(String startIP, String endIP) {
        try {
            long start = ipToLong(startIP);
            long end = ipToLong(endIP);
            if (start > end) {
                long tmp = start;
                start = end;
                end = tmp;
            }
            addRange(start, end);
            totalEntries++;
            logger.info("Banned IP range: %s - %s", startIP, endIP);
        } catch (Exception e) {
            logger.warn("Invalid IP range: %s - %s", startIP, endIP);
        }
    }

    /**
     * Unban a specific IP address.
     * 
     * @param ip the IP address to unban
     * @return true if the IP was banned
     */
    public boolean unbanIP(String ip) {
        if (bannedIPs.remove(ip) != null) {
            logger.info("Unbanned IP: %s", ip);
            return true;
        }
        return false;
    }

    /**
     * Check if an IP address is banned.
     * 
     * @param ip the IP address to check
     * @return true if banned
     */
    public boolean isBanned(String ip) {
        // Check individual IPs first
        if (bannedIPs.containsKey(ip)) {
            return true;
        }

        // Check ranges
        try {
            long ipLong = ipToLong(ip);

            rangeLock.readLock().lock();
            try {
                for (long[] range : bannedRanges) {
                    if (ipLong >= range[0] && ipLong <= range[1]) {
                        return true;
                    }
                }
            } finally {
                rangeLock.readLock().unlock();
            }
        } catch (Exception e) {
            // Invalid IP format
        }

        return false;
    }

    /**
     * Check if an InetAddress is banned.
     * 
     * @param address the address to check
     * @return true if banned
     */
    public boolean isBanned(InetAddress address) {
        return isBanned(address.getHostAddress());
    }

    /**
     * Convert an IP address string to a long value.
     */
    private long ipToLong(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP: " + ip);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IP octet: " + parts[i]);
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    /**
     * Convert a long value to an IP address string.
     */
    private String longToIP(long ip) {
        return String.format("%d.%d.%d.%d",
                (ip >> 24) & 0xFF,
                (ip >> 16) & 0xFF,
                (ip >> 8) & 0xFF,
                ip & 0xFF);
    }

    /**
     * Check if a string is a valid IPv4 address.
     */
    private boolean isValidIP(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4)
                return false;

            for (String part : parts) {
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255)
                    return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Get the number of banned entries.
     * 
     * @return number of banned IPs and ranges
     */
    public int getBanCount() {
        rangeLock.readLock().lock();
        try {
            return bannedIPs.size() + bannedRanges.size();
        } finally {
            rangeLock.readLock().unlock();
        }
    }

    /**
     * Get the total number of loaded entries.
     * 
     * @return total entries
     */
    public int getTotalEntries() {
        return totalEntries;
    }

    /**
     * Get all individually banned IPs.
     * 
     * @return list of banned IPs
     */
    public List<String> getBannedIPs() {
        return new ArrayList<>(bannedIPs.keySet());
    }

    /**
     * Clear all bans.
     */
    public void clear() {
        bannedIPs.clear();
        rangeLock.writeLock().lock();
        try {
            bannedRanges.clear();
        } finally {
            rangeLock.writeLock().unlock();
        }
        totalEntries = 0;
        logger.info("Cleared all bans");
    }
}
