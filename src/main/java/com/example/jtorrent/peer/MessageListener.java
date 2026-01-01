package com.example.jtorrent.peer;

/**
 * Listener for incoming peer messages.
 */
public interface MessageListener {

  /**
   * Called when a message is received.
   *
   * @param message the received message
   */
  void onMessage(Message message);

  /**
   * Called when the connection is closed.
   *
   * @param reason the reason for closing (may be null)
   */
  void onDisconnect(String reason);

  /**
   * Called when the handshake is complete.
   *
   * @param remotePeerId the remote peer's ID
   */
  void onHandshakeComplete(byte[] remotePeerId);
}
