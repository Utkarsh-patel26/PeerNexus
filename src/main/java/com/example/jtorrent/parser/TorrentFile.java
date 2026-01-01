package com.example.jtorrent.parser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parsed .torrent file.
 */
public class TorrentFile {

  private final Map<String, Object> rootDict;
  private final Map<String, Object> infoDict;
  private final byte[] infoHash;
  private final String name;
  private final byte[] nameRaw;
  private final long totalLength;
  private final int pieceLength;
  private final List<byte[]> pieces;
  private final List<FileEntry> files;
  private final URI primaryAnnounce;
  private final List<URI> announceList;

  private TorrentFile(
      Map<String, Object> rootDict,
      Map<String, Object> infoDict,
      byte[] infoHash,
      String name,
      byte[] nameRaw,
      long totalLength,
      int pieceLength,
      List<byte[]> pieces,
      List<FileEntry> files,
      URI primaryAnnounce,
      List<URI> announceList) {
    this.rootDict = rootDict;
    this.infoDict = infoDict;
    this.infoHash = infoHash;
    this.name = name;
    this.nameRaw = nameRaw;
    this.totalLength = totalLength;
    this.pieceLength = pieceLength;
    this.pieces = pieces;
    this.files = files;
    this.primaryAnnounce = primaryAnnounce;
    this.announceList = announceList;
  }

  /**
   * Parse a .torrent file.
   *
   * @param torrentFile the file to parse
   * @return parsed TorrentFile object
   * @throws IOException      if reading fails
   * @throws BencodeException if parsing fails
   */
  public static TorrentFile parse(File torrentFile) throws IOException, BencodeException {
    byte[] data = Files.readAllBytes(torrentFile.toPath());
    return parse(data);
  }

  /**
   * Parse .torrent data from bytes.
   *
   * @param data the bencode data
   * @return parsed TorrentFile object
   * @throws BencodeException if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static TorrentFile parse(byte[] data) throws BencodeException {
    BencodeParser parser = new BencodeParser(data);
    Object parsed = parser.parse();

    if (!(parsed instanceof Map)) {
      throw new BencodeException("Torrent file must be a dictionary");
    }

    Map<String, Object> rootDict = (Map<String, Object>) parsed;

    // Extract info dictionary
    if (!rootDict.containsKey("info")) {
      throw new BencodeException("Missing 'info' dictionary");
    }

    Object infoObj = rootDict.get("info");
    if (!(infoObj instanceof Map)) {
      throw new BencodeException("'info' must be a dictionary");
    }
    Map<String, Object> infoDict = (Map<String, Object>) infoObj;

    // Compute info hash from the exact bytes
    final byte[] infoHash = computeInfoHash(data);

    // Extract name
    if (!infoDict.containsKey("name")) {
      throw new BencodeException("Missing 'name' in info dictionary");
    }
    byte[] nameRaw = (byte[]) infoDict.get("name");
    String name = new String(nameRaw, StandardCharsets.UTF_8);

    // Extract piece length
    if (!infoDict.containsKey("piece length")) {
      throw new BencodeException("Missing 'piece length' in info dictionary");
    }
    int pieceLength = ((Long) infoDict.get("piece length")).intValue();

    // Extract pieces
    if (!infoDict.containsKey("pieces")) {
      throw new BencodeException("Missing 'pieces' in info dictionary");
    }
    byte[] piecesData = (byte[]) infoDict.get("pieces");
    if (piecesData.length % 20 != 0) {
      throw new BencodeException("Pieces length must be multiple of 20");
    }
    List<byte[]> pieces = new ArrayList<>();
    for (int i = 0; i < piecesData.length; i += 20) {
      byte[] piece = new byte[20];
      System.arraycopy(piecesData, i, piece, 0, 20);
      pieces.add(piece);
    }

    // Extract files and total length
    List<FileEntry> files = new ArrayList<>();
    long totalLength;

    if (infoDict.containsKey("files")) {
      // Multi-file torrent
      List<Object> filesList = (List<Object>) infoDict.get("files");
      totalLength = 0;
      for (Object fileObj : filesList) {
        Map<String, Object> fileMap = (Map<String, Object>) fileObj;
        long fileLength = (Long) fileMap.get("length");
        totalLength += fileLength;

        List<Object> pathList = (List<Object>) fileMap.get("path");
        List<String> pathComponents = new ArrayList<>();
        List<byte[]> pathComponentsRaw = new ArrayList<>();
        for (Object pathObj : pathList) {
          byte[] pathBytes = (byte[]) pathObj;
          pathComponentsRaw.add(pathBytes);
          pathComponents.add(new String(pathBytes, StandardCharsets.UTF_8));
        }
        files.add(new FileEntry(pathComponents, pathComponentsRaw, fileLength));
      }
    } else {
      // Single-file torrent
      if (!infoDict.containsKey("length")) {
        throw new BencodeException("Missing 'length' for single-file torrent");
      }
      totalLength = (Long) infoDict.get("length");
      List<String> pathComponents = Collections.singletonList(name);
      List<byte[]> pathComponentsRaw = Collections.singletonList(nameRaw);
      files.add(new FileEntry(pathComponents, pathComponentsRaw, totalLength));
    }

    // Extract announce
    URI primaryAnnounce = null;
    if (rootDict.containsKey("announce")) {
      byte[] announceBytes = (byte[]) rootDict.get("announce");
      String announceStr = new String(announceBytes, StandardCharsets.UTF_8);
      try {
        primaryAnnounce = new URI(announceStr);
      } catch (URISyntaxException e) {
        // Keep as null if invalid
      }
    }

    // Extract announce-list
    List<URI> announceList = new ArrayList<>();
    if (rootDict.containsKey("announce-list")) {
      List<Object> tiers = (List<Object>) rootDict.get("announce-list");
      for (Object tierObj : tiers) {
        List<Object> tier = (List<Object>) tierObj;
        for (Object urlObj : tier) {
          byte[] urlBytes = (byte[]) urlObj;
          String urlStr = new String(urlBytes, StandardCharsets.UTF_8);
          try {
            announceList.add(new URI(urlStr));
          } catch (URISyntaxException e) {
            // Skip invalid URIs
          }
        }
      }
    }
    if (announceList.isEmpty() && primaryAnnounce != null) {
      announceList.add(primaryAnnounce);
    }

    return new TorrentFile(
        rootDict,
        infoDict,
        infoHash,
        name,
        nameRaw,
        totalLength,
        pieceLength,
        pieces,
        files,
        primaryAnnounce,
        announceList);
  }

  /**
   * Parse raw info dictionary from BEP-9 metadata exchange.
   * The metadata from peers is just the raw info dictionary bytes,
   * so we need to wrap it properly before parsing.
   *
   * @param infoDictBytes the raw info dictionary bytes
   * @param trackers      list of tracker URIs from the magnet link
   * @return parsed TorrentFile object
   * @throws BencodeException if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static TorrentFile parseInfoDict(byte[] infoDictBytes, List<URI> trackers)
      throws BencodeException {
    // Parse the info dictionary
    BencodeParser infoParser = new BencodeParser(infoDictBytes);
    Object parsedInfo = infoParser.parse();

    if (!(parsedInfo instanceof Map)) {
      throw new BencodeException("Info dictionary must be a dictionary");
    }

    Map<String, Object> infoDict = (Map<String, Object>) parsedInfo;

    // Compute info hash from the raw bytes
    byte[] infoHash;
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      infoHash = sha1.digest(infoDictBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new BencodeException("SHA-1 not available", e);
    }

    // Extract name
    if (!infoDict.containsKey("name")) {
      throw new BencodeException("Missing 'name' in info dictionary");
    }
    byte[] nameRaw = (byte[]) infoDict.get("name");
    String name = new String(nameRaw, StandardCharsets.UTF_8);

    // Extract piece length
    if (!infoDict.containsKey("piece length")) {
      throw new BencodeException("Missing 'piece length' in info dictionary");
    }
    int pieceLength = ((Long) infoDict.get("piece length")).intValue();

    // Extract pieces
    if (!infoDict.containsKey("pieces")) {
      throw new BencodeException("Missing 'pieces' in info dictionary");
    }
    byte[] piecesRaw = (byte[]) infoDict.get("pieces");
    if (piecesRaw.length % 20 != 0) {
      throw new BencodeException("Pieces length must be multiple of 20");
    }

    List<byte[]> pieces = new ArrayList<>();
    for (int i = 0; i < piecesRaw.length; i += 20) {
      byte[] hash = new byte[20];
      System.arraycopy(piecesRaw, i, hash, 0, 20);
      pieces.add(hash);
    }

    // Extract files
    List<FileEntry> files = new ArrayList<>();
    long totalLength = 0;

    if (infoDict.containsKey("files")) {
      // Multi-file torrent
      List<Object> filesList = (List<Object>) infoDict.get("files");
      for (Object fileObj : filesList) {
        Map<String, Object> fileMap = (Map<String, Object>) fileObj;
        long fileLength = (Long) fileMap.get("length");
        totalLength += fileLength;

        List<Object> pathList = (List<Object>) fileMap.get("path");
        List<String> pathComponents = new ArrayList<>();
        List<byte[]> pathComponentsRaw = new ArrayList<>();
        for (Object pathObj : pathList) {
          byte[] pathBytes = (byte[]) pathObj;
          pathComponentsRaw.add(pathBytes);
          pathComponents.add(new String(pathBytes, StandardCharsets.UTF_8));
        }
        files.add(new FileEntry(pathComponents, pathComponentsRaw, fileLength));
      }
    } else {
      // Single-file torrent
      if (!infoDict.containsKey("length")) {
        throw new BencodeException("Missing 'length' for single-file torrent");
      }
      totalLength = (Long) infoDict.get("length");
      List<String> pathComponents = Collections.singletonList(name);
      List<byte[]> pathComponentsRaw = Collections.singletonList(nameRaw);
      files.add(new FileEntry(pathComponents, pathComponentsRaw, totalLength));
    }

    // Create a minimal root dictionary with info and trackers
    Map<String, Object> rootDict = new java.util.LinkedHashMap<>();
    rootDict.put("info", infoDict);

    // Add trackers to root dict
    List<URI> announceList = new ArrayList<>(trackers);
    URI primaryAnnounce = announceList.isEmpty() ? null : announceList.get(0);

    if (primaryAnnounce != null) {
      rootDict.put("announce", primaryAnnounce.toString().getBytes(StandardCharsets.UTF_8));
    }

    return new TorrentFile(
        rootDict,
        infoDict,
        infoHash,
        name,
        nameRaw,
        totalLength,
        pieceLength,
        pieces,
        files,
        primaryAnnounce,
        announceList);
  }

  private static byte[] computeInfoHash(byte[] data) throws BencodeException {
    // Find the exact bytes of the info dictionary
    BencodeParser parser = new BencodeParser(data);
    int[] range = parser.findDictionaryValueRange("info");

    if (range == null) {
      throw new BencodeException("Could not find 'info' dictionary");
    }

    byte[] infoBytes = new byte[range[1] - range[0]];
    System.arraycopy(data, range[0], infoBytes, 0, infoBytes.length);

    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      return sha1.digest(infoBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new BencodeException("SHA-1 not available", e);
    }
  }

  /**
   * Get the info dictionary.
   *
   * @return the info dictionary
   */
  public Map<String, Object> infoMap() {
    return infoDict;
  }

  /**
   * Get the 20-byte SHA-1 info hash.
   *
   * @return the info hash
   */
  public byte[] infoHash() {
    return infoHash.clone();
  }

  /**
   * Get the info hash as a hex string.
   *
   * @return hex string representation
   */
  public String infoHashHex() {
    StringBuilder sb = new StringBuilder();
    for (byte b : infoHash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /**
   * Get the torrent name.
   *
   * @return the name
   */
  public String name() {
    return name;
  }

  /**
   * Get the torrent name as raw bytes.
   *
   * @return raw name bytes
   */
  public byte[] nameRaw() {
    return nameRaw.clone();
  }

  /**
   * Get the total length of all files.
   *
   * @return total length in bytes
   */
  public long totalLength() {
    return totalLength;
  }

  /**
   * Get the piece length.
   *
   * @return piece length in bytes
   */
  public int pieceLength() {
    return pieceLength;
  }

  /**
   * Get the list of piece hashes.
   *
   * @return list of 20-byte piece hashes
   */
  public List<byte[]> pieces() {
    return Collections.unmodifiableList(pieces);
  }

  /**
   * Get the number of pieces.
   *
   * @return piece count
   */
  public int pieceCount() {
    return pieces.size();
  }

  /**
   * Get the list of files.
   *
   * @return list of file entries
   */
  public List<FileEntry> files() {
    return Collections.unmodifiableList(files);
  }

  /**
   * Check if this is a single-file torrent.
   *
   * @return true if single-file
   */
  public boolean isSingleFile() {
    return !infoDict.containsKey("files");
  }

  /**
   * Check if this is a private torrent.
   * Private torrents should NOT use DHT or PEX for peer discovery.
   *
   * @return true if private flag is set to 1
   */
  public boolean isPrivate() {
    if (infoDict.containsKey("private")) {
      Object privateVal = infoDict.get("private");
      if (privateVal instanceof Long) {
        return ((Long) privateVal) == 1;
      }
    }
    return false;
  }

  /**
   * Get the primary announce URL.
   *
   * @return primary announce URI or null
   */
  public URI primaryAnnounce() {
    return primaryAnnounce;
  }

  /**
   * Get all announce URLs.
   *
   * @return list of announce URIs
   */
  public List<URI> announceList() {
    return Collections.unmodifiableList(announceList);
  }

  /**
   * Get the root dictionary.
   *
   * @return the root dictionary
   */
  public Map<String, Object> rootDict() {
    return rootDict;
  }
}
