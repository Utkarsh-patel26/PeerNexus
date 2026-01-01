package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.peer.Message;
import com.example.jtorrent.peer.MessageListener;
import com.example.jtorrent.peer.PeerConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handler for testing peer connections and message exchange.
 */
public final class PeerTestHandler implements CommandHandler {

  private static final byte[] INFO_HASH = new byte[20];
  private static final byte[] PEER_ID_A = "-JT0001-AAAAAAAAAAAA".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PEER_ID_B = "-JT0001-BBBBBBBBBBBB".getBytes(StandardCharsets.US_ASCII);
  private static final int TIMEOUT_MS = 5000;
  private static final int BLOCK_SIZE = 16384;

  @Override
  public void execute(CliArgs args) {
    System.out.println("Peer Connection Test");
    System.out.println("====================");
    System.out.println();

    try (ServerSocket server = new ServerSocket(0)) {
      int port = server.getLocalPort();
      System.out.println("Server listening on port: " + port);
      System.out.println();

      Thread serverThread = startServerThread(server);
      runClientConnection(port);

      serverThread.join(TIMEOUT_MS);

      System.out.println();
      System.out.println("Test completed successfully!");

    } catch (Exception e) {
      System.err.println("Peer test failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static Thread startServerThread(ServerSocket server) {
    Thread serverThread = new Thread(() -> {
      try (Socket accepted = server.accept()) {
        runServerPeer(accepted);
      } catch (Exception e) {
        System.err.println("[Peer B] Error: " + e.getMessage());
      }
    });
    serverThread.start();
    return serverThread;
  }

  private static void runServerPeer(Socket socket) throws Exception {
    PeerConnection serverPeer = new PeerConnection(INFO_HASH, PEER_ID_B);
    serverPeer.addListener(createMessageListener("Peer B"));

    serverPeer.accept(socket, TIMEOUT_MS);
    System.out.println("[Peer B] Accepted connection");

    serverPeer.handshake();
    System.out.println("[Peer B] Handshake complete");

    Message request = serverPeer.receive();
    System.out.println("[Peer B] Processing request...");

    int[] params = request.requestParams();
    byte[] blockData = generateBlockData(params[2]);
    serverPeer.send(Message.piece(params[0], params[1], blockData));
    System.out.println("[Peer B] Sent PIECE response");

    serverPeer.receive(); // Receive HAVE
    serverPeer.close();
  }

  private static void runClientConnection(int port) throws Exception {
    try (PeerConnection client = new PeerConnection(INFO_HASH, PEER_ID_A)) {
      client.addListener(createMessageListener("Peer A"));

      System.out.println("[Peer A] Connecting to localhost:" + port);
      client.connect(new InetSocketAddress("localhost", port), TIMEOUT_MS);
      System.out.println("[Peer A] Connected");

      client.handshake();
      System.out.println("[Peer A] Handshake complete");
      System.out.println();

      exchangeMessages(client);
    }
  }

  private static void exchangeMessages(PeerConnection client) throws Exception {
    System.out.println("Message Exchange:");
    System.out.println("-----------------");

    Message request = Message.request(0, 0, BLOCK_SIZE);
    System.out.println("[Peer A] Sending: " + request);
    client.send(request);

    Message piece = client.receive();
    System.out.println("[Peer A] Block size: " + piece.pieceBlock().length + " bytes");

    Message have = Message.have(42);
    System.out.println("[Peer A] Sending: " + have);
    client.send(have);
  }

  private static byte[] generateBlockData(int length) {
    byte[] data = new byte[length];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 256);
    }
    return data;
  }

  private static MessageListener createMessageListener(String peerName) {
    return new MessageListener() {
      @Override
      public void onMessage(Message message) {
        System.out.println("[" + peerName + "] Received: " + message);
      }

      @Override
      public void onDisconnect(String reason) {
        System.out.println("[" + peerName + "] Disconnected: " + reason);
      }

      @Override
      public void onHandshakeComplete(byte[] remotePeerId) {
        System.out.println("[" + peerName + "] Handshake complete with peer: "
            + new String(remotePeerId, StandardCharsets.US_ASCII));
      }
    };
  }
}
