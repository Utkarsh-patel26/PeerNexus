package com.example.jtorrent.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Message Stream Encryption (BEP 7) implementation using RC4.
 * Provides encryption for BitTorrent peer wire protocol.
 */
public class MessageStreamEncryption {

    // Diffie-Hellman parameters (as per BEP 7)
    private static final BigInteger DH_P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
                    "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
                    "4FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563",
            16);

    private static final BigInteger DH_G = BigInteger.valueOf(2);

    private final SecureRandom random = new SecureRandom();
    private BigInteger privateKey;
    private BigInteger publicKey;
    private byte[] secret;
    private RC4State sendCipher;
    private RC4State receiveCipher;

    public enum CryptoMode {
        PLAINTEXT(0x01),
        RC4(0x02);

        private final int value;

        CryptoMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Generate DH keypair for handshake.
     */
    public void generateKeyPair() {
        // Generate random private key (160-bit)
        byte[] privateKeyBytes = new byte[20];
        random.nextBytes(privateKeyBytes);
        this.privateKey = new BigInteger(1, privateKeyBytes);

        // Compute public key: g^a mod p
        this.publicKey = DH_G.modPow(privateKey, DH_P);
    }

    /**
     * Get public key for sending to peer.
     *
     * @return 96-byte public key (padded to 768 bits)
     */
    public byte[] getPublicKey() {
        byte[] keyBytes = publicKey.toByteArray();

        // Pad to 96 bytes if necessary
        if (keyBytes.length < 96) {
            byte[] padded = new byte[96];
            System.arraycopy(keyBytes, 0, padded, 96 - keyBytes.length, keyBytes.length);
            return padded;
        } else if (keyBytes.length > 96) {
            // Remove leading zero byte if present
            return Arrays.copyOfRange(keyBytes, keyBytes.length - 96, keyBytes.length);
        }

        return keyBytes;
    }

    /**
     * Compute shared secret from peer's public key.
     *
     * @param peerPublicKey peer's public key
     */
    public void computeSecret(byte[] peerPublicKey) {
        BigInteger peerKey = new BigInteger(1, peerPublicKey);

        // Compute shared secret: (peer_key)^private_key mod p
        BigInteger sharedSecret = peerKey.modPow(privateKey, DH_P);

        // Convert to bytes (96 bytes)
        byte[] secretBytes = sharedSecret.toByteArray();
        this.secret = new byte[96];

        if (secretBytes.length < 96) {
            System.arraycopy(secretBytes, 0, this.secret, 96 - secretBytes.length, secretBytes.length);
        } else {
            System.arraycopy(secretBytes, secretBytes.length - 96, this.secret, 0, 96);
        }
    }

    /**
     * Initialize encryption ciphers.
     *
     * @param infoHash    torrent info hash
     * @param isInitiator true if we initiated connection
     * @throws Exception if initialization fails
     */
    public void initializeCiphers(byte[] infoHash, boolean isInitiator) throws Exception {
        // Key derivation as per BEP 7
        byte[] key1 = deriveKey(infoHash, isInitiator ? "keyA" : "keyB");
        byte[] key2 = deriveKey(infoHash, isInitiator ? "keyB" : "keyA");

        // Initialize RC4 ciphers
        sendCipher = new RC4State(key1);
        receiveCipher = new RC4State(key2);

        // Discard first 1024 bytes (RC4 initialization)
        byte[] discard = new byte[1024];
        sendCipher.encrypt(discard);
        receiveCipher.decrypt(discard);
    }

    /**
     * Derive encryption key from shared secret.
     */
    private byte[] deriveKey(byte[] infoHash, String keyName) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

        // Hash: S || SKEY || infoHash
        sha1.update(keyName.getBytes("UTF-8"));
        sha1.update(secret);
        sha1.update(infoHash);

        return sha1.digest(); // 20 bytes
    }

    /**
     * Encrypt data for sending.
     *
     * @param plaintext plain data
     * @return encrypted data
     */
    public byte[] encrypt(byte[] plaintext) {
        if (sendCipher == null) {
            throw new IllegalStateException("Ciphers not initialized");
        }
        return sendCipher.encrypt(plaintext);
    }

    /**
     * Decrypt received data.
     *
     * @param ciphertext encrypted data
     * @return plain data
     */
    public byte[] decrypt(byte[] ciphertext) {
        if (receiveCipher == null) {
            throw new IllegalStateException("Ciphers not initialized");
        }
        return receiveCipher.decrypt(ciphertext);
    }

    /**
     * Create initial encrypted handshake payload.
     *
     * @param infoHash       torrent info hash
     * @param supportedModes bitmask of supported crypto modes
     * @return encrypted handshake
     */
    public byte[] createInitialPayload(byte[] infoHash, int supportedModes) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(96 + 20 + 4);

        // Public key (96 bytes)
        buffer.put(getPublicKey());

        // Hash( 'req1', S )
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update("req1".getBytes("UTF-8"));
        sha1.update(secret);
        buffer.put(sha1.digest());

        // Crypto provide (4 bytes)
        buffer.putInt(supportedModes);

        return buffer.array();
    }

    /**
     * Verify received handshake.
     *
     * @param payload      received payload
     * @param expectedHash expected hash value
     * @return true if valid
     */
    public boolean verifyHandshake(byte[] payload, byte[] expectedHash) throws Exception {
        if (payload.length < 20) {
            return false;
        }

        byte[] receivedHash = Arrays.copyOfRange(payload, 0, 20);
        return Arrays.equals(receivedHash, expectedHash);
    }

    /**
     * Simple RC4 cipher implementation.
     */
    private static class RC4State {
        private final int[] state = new int[256];
        private int x = 0;
        private int y = 0;

        public RC4State(byte[] key) {
            // Key scheduling algorithm (KSA)
            for (int i = 0; i < 256; i++) {
                state[i] = i;
            }

            int j = 0;
            for (int i = 0; i < 256; i++) {
                j = (j + state[i] + (key[i % key.length] & 0xFF)) % 256;
                swap(i, j);
            }
        }

        public byte[] encrypt(byte[] data) {
            byte[] result = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ nextByte());
            }
            return result;
        }

        public byte[] decrypt(byte[] data) {
            // RC4 is symmetric
            return encrypt(data);
        }

        private int nextByte() {
            x = (x + 1) % 256;
            y = (y + state[x]) % 256;
            swap(x, y);
            return state[(state[x] + state[y]) % 256];
        }

        private void swap(int i, int j) {
            int temp = state[i];
            state[i] = state[j];
            state[j] = temp;
        }
    }
}
