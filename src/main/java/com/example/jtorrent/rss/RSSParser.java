package com.example.jtorrent.rss;

import com.example.jtorrent.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RSS feed parser supporting RSS 2.0 and Atom formats.
 */
public class RSSParser {

    private final Logger logger;

    // Common RSS date formats
    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss"
    };

    public RSSParser(Logger logger) {
        this.logger = logger;
    }

    /**
     * Fetch and parse RSS feed.
     *
     * @param feedUrl RSS feed URL
     * @return list of RSS items
     * @throws Exception if fetch or parse fails
     */
    public List<RSSItem> fetchFeed(URI feedUrl) throws Exception {
        logger.debug("Fetching RSS feed: %s", feedUrl);

        HttpURLConnection connection = (HttpURLConnection) feedUrl.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "PeerNexus/1.0");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP error: " + responseCode);
        }

        try (InputStream inputStream = connection.getInputStream()) {
            return parseFeed(inputStream);
        }
    }

    /**
     * Parse RSS feed from input stream.
     *
     * @param inputStream RSS XML data
     * @return list of RSS items
     * @throws Exception if parse fails
     */
    public List<RSSItem> parseFeed(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);

        // Detect RSS 2.0 vs Atom
        Element root = doc.getDocumentElement();
        if ("rss".equals(root.getNodeName())) {
            return parseRss2(doc);
        } else if ("feed".equals(root.getNodeName())) {
            return parseAtom(doc);
        } else {
            throw new Exception("Unsupported feed format: " + root.getNodeName());
        }
    }

    /**
     * Parse RSS 2.0 format.
     */
    private List<RSSItem> parseRss2(Document doc) {
        List<RSSItem> items = new ArrayList<>();
        NodeList itemNodes = doc.getElementsByTagName("item");

        for (int i = 0; i < itemNodes.getLength(); i++) {
            Node itemNode = itemNodes.item(i);
            if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
                Element itemElement = (Element) itemNode;
                RSSItem item = parseRss2Item(itemElement);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        logger.debug("Parsed %d RSS 2.0 items", items.size());
        return items;
    }

    /**
     * Parse single RSS 2.0 item.
     */
    private RSSItem parseRss2Item(Element itemElement) {
        try {
            String title = getElementText(itemElement, "title");
            String description = getElementText(itemElement, "description");
            String linkStr = getElementText(itemElement, "link");
            String pubDateStr = getElementText(itemElement, "pubDate");
            String category = getElementText(itemElement, "category");

            // Extract torrent URL from enclosure or link
            URI torrentUrl = null;
            NodeList enclosures = itemElement.getElementsByTagName("enclosure");
            if (enclosures.getLength() > 0) {
                Element enclosure = (Element) enclosures.item(0);
                String url = enclosure.getAttribute("url");
                if (url != null && !url.isEmpty()) {
                    torrentUrl = new URI(url);
                }
            }

            // Fallback to link if no enclosure
            if (torrentUrl == null && linkStr != null) {
                torrentUrl = new URI(linkStr);
            }

            // Extract size from enclosure
            long size = 0;
            if (enclosures.getLength() > 0) {
                Element enclosure = (Element) enclosures.item(0);
                String lengthStr = enclosure.getAttribute("length");
                if (lengthStr != null && !lengthStr.isEmpty()) {
                    try {
                        size = Long.parseLong(lengthStr);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }

            // Parse date
            Date publishDate = parseDate(pubDateStr);

            URI link = linkStr != null ? new URI(linkStr) : null;

            return new RSSItem(title, description, link, torrentUrl,
                    publishDate, size, category);

        } catch (Exception e) {
            logger.warn("Failed to parse RSS item: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Parse Atom format.
     */
    private List<RSSItem> parseAtom(Document doc) {
        List<RSSItem> items = new ArrayList<>();
        NodeList entryNodes = doc.getElementsByTagName("entry");

        for (int i = 0; i < entryNodes.getLength(); i++) {
            Node entryNode = entryNodes.item(i);
            if (entryNode.getNodeType() == Node.ELEMENT_NODE) {
                Element entryElement = (Element) entryNode;
                RSSItem item = parseAtomEntry(entryElement);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        logger.debug("Parsed %d Atom entries", items.size());
        return items;
    }

    /**
     * Parse single Atom entry.
     */
    private RSSItem parseAtomEntry(Element entryElement) {
        try {
            String title = getElementText(entryElement, "title");
            String summary = getElementText(entryElement, "summary");
            String published = getElementText(entryElement, "published");

            // Extract link
            URI link = null;
            URI torrentUrl = null;
            NodeList links = entryElement.getElementsByTagName("link");
            for (int i = 0; i < links.getLength(); i++) {
                Element linkElement = (Element) links.item(i);
                String href = linkElement.getAttribute("href");
                String rel = linkElement.getAttribute("rel");
                String type = linkElement.getAttribute("type");

                if (type != null && type.contains("torrent")) {
                    torrentUrl = new URI(href);
                } else if ("alternate".equals(rel) || rel.isEmpty()) {
                    link = new URI(href);
                }
            }

            // Parse date
            Date publishDate = parseDate(published);

            return new RSSItem(title, summary, link, torrentUrl, publishDate, 0, null);

        } catch (Exception e) {
            logger.warn("Failed to parse Atom entry: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Extract text content from element.
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            return node.getTextContent();
        }
        return null;
    }

    /**
     * Parse date from string using multiple formats.
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                return sdf.parse(dateStr);
            } catch (Exception e) {
                // Try next format
            }
        }

        logger.debug("Failed to parse date: %s", dateStr);
        return null;
    }
}
