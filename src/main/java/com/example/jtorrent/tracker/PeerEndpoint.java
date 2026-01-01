package com.example.jtorrent.tracker;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * Peer endpoint from tracker.
 */
public class PeerEndpoint {

  private final InetSocketAddress address;
  private final byte[] peerId;

  public PeerEndpoint(InetSocketAddress address) {
    this.address = address;
    this.peerId = null;
  }

  public PeerEndpoint(InetSocketAddress address, byte[] peerId) {
    this.address = address;
    this.peerId = peerId != null ? peerId.clone() : null;
  }

  public InetSocketAddress address() {
    return address;
  }

  public byte[] peerId() {
    return peerId != null ? peerId.clone() : null;
  }

  public boolean hasPeerId() {
    return peerId != null;
  }

  public String peerIdHex() {
    if (peerId == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (byte b : peerId) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    if (peerId != null) {
      return address.getAddress().getHostAddress() + ":" + address.getPort()
          + " [" + peerIdHex() + "]";
    }
    return address.getAddress().getHostAddress() + ":" + address.getPort();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PeerEndpoint other = (PeerEndpoint) obj;
    return address.equals(other.address) && Arrays.equals(peerId, other.peerId);
  }

  @Override
  public int hashCode() {
    int result = address.hashCode();
    result = 31 * result + Arrays.hashCode(peerId);
    return result;
  }
}
