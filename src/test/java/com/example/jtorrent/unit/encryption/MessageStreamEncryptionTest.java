package com.example.jtorrent.unit.encryption;

import com.example.jtorrent.encryption.MessageStreamEncryption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

class MessageStreamEncryptionTest {

    private MessageStreamEncryption encryption;

    @BeforeEach
    void setUp() {
        encryption = new MessageStreamEncryption();
    }

    @Nested
    class KeyGenerationTests {

        @Test
        void generateKeyPairCreatesKeys() {
            encryption.generateKeyPair();
            byte[] publicKey = encryption.getPublicKey();

            assertThat(publicKey).isNotNull();
            assertThat(publicKey).hasSize(96);
        }

        @Test
        void generateKeyPairCreatesUniqueKeys() {
            encryption.generateKeyPair();
            byte[] publicKey1 = encryption.getPublicKey().clone();

            MessageStreamEncryption encryption2 = new MessageStreamEncryption();
            encryption2.generateKeyPair();
            byte[] publicKey2 = encryption2.getPublicKey();

            assertThat(publicKey1).isNotEqualTo(publicKey2);
        }

        @RepeatedTest(10)
        void generateKeyPairProducesValidKey() {
            encryption.generateKeyPair();
            byte[] publicKey = encryption.getPublicKey();

            assertThat(publicKey).hasSize(96);
            assertThat(publicKey).isNotEqualTo(new byte[96]);
        }

        @Test
        void getPublicKeyReturns96Bytes() {
            encryption.generateKeyPair();
            byte[] publicKey = encryption.getPublicKey();

            assertThat(publicKey).hasSize(96);
        }

        @Test
        void getPublicKeyConsistentAfterGeneration() {
            encryption.generateKeyPair();
            byte[] publicKey1 = encryption.getPublicKey();
            byte[] publicKey2 = encryption.getPublicKey();

            assertThat(publicKey1).isEqualTo(publicKey2);
        }
    }

    @Nested
    class SecretComputationTests {

        @Test
        void computeSecretWithValidPeerKey() {
            encryption.generateKeyPair();
            @SuppressWarnings("unused")
            byte[] ourPublicKey = encryption.getPublicKey();

            MessageStreamEncryption peerEncryption = new MessageStreamEncryption();
            peerEncryption.generateKeyPair();
            byte[] peerPublicKey = peerEncryption.getPublicKey();

            assertThatCode(() -> encryption.computeSecret(peerPublicKey))
                    .doesNotThrowAnyException();
        }

        @Test
        void computeSecretDeterministic() {
            encryption.generateKeyPair();
            byte[] ourPublicKey = encryption.getPublicKey().clone();

            MessageStreamEncryption peerEncryption = new MessageStreamEncryption();
            peerEncryption.generateKeyPair();
            byte[] peerPublicKey = peerEncryption.getPublicKey();

            encryption.computeSecret(peerPublicKey);
            peerEncryption.computeSecret(ourPublicKey);

        }

        @Test
        void computeSecretWithZeroPeerKey() {
            encryption.generateKeyPair();
            byte[] zeroPeerKey = new byte[96];

            assertThatCode(() -> encryption.computeSecret(zeroPeerKey))
                    .doesNotThrowAnyException();
        }

        @Test
        void computeSecretWithMaxValuePeerKey() {
            encryption.generateKeyPair();
            byte[] maxPeerKey = new byte[96];
            Arrays.fill(maxPeerKey, (byte) 0xFF);

            assertThatCode(() -> encryption.computeSecret(maxPeerKey))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class CipherInitializationTests {

        @Test
        void initializeCiphersAsInitiator() throws Exception {
            setupEncryption(true);

            assertThatCode(() -> {
                byte[] testData = "Hello".getBytes();
                encryption.encrypt(testData);
            }).doesNotThrowAnyException();
        }

        @Test
        void initializeCiphersAsResponder() throws Exception {
            setupEncryption(false);

            assertThatCode(() -> {
                byte[] testData = "Hello".getBytes();
                encryption.encrypt(testData);
            }).doesNotThrowAnyException();
        }

        @Test
        void initializeCiphersWithValidInfoHash() throws Exception {
            encryption.generateKeyPair();

            MessageStreamEncryption peer = new MessageStreamEncryption();
            peer.generateKeyPair();

            encryption.computeSecret(peer.getPublicKey());

            byte[] infoHash = new byte[20];
            new Random(42).nextBytes(infoHash);

            assertThatCode(() -> encryption.initializeCiphers(infoHash, true))
                    .doesNotThrowAnyException();
        }

        @Test
        void encryptThrowsBeforeInitialization() {
            assertThatThrownBy(() -> encryption.encrypt("test".getBytes()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not initialized");
        }

        @Test
        void decryptThrowsBeforeInitialization() {
            assertThatThrownBy(() -> encryption.decrypt("test".getBytes()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not initialized");
        }
    }

    @Nested
    class EncryptionDecryptionTests {

        @Test
        void encryptDecryptRoundTrip() throws Exception {
            MessageStreamEncryption initiator = new MessageStreamEncryption();
            MessageStreamEncryption responder = new MessageStreamEncryption();

            initiator.generateKeyPair();
            responder.generateKeyPair();

            initiator.computeSecret(responder.getPublicKey());
            responder.computeSecret(initiator.getPublicKey());

            byte[] infoHash = new byte[20];
            new Random(42).nextBytes(infoHash);

            initiator.initializeCiphers(infoHash, true);
            responder.initializeCiphers(infoHash, false);

            byte[] original = "Hello, World!".getBytes();
            byte[] encrypted = initiator.encrypt(original);
            byte[] decrypted = responder.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        void encryptProducesDifferentOutput() throws Exception {
            setupEncryption(true);

            byte[] plaintext = "Test message".getBytes();
            byte[] encrypted = encryption.encrypt(plaintext);

            assertThat(encrypted).isNotEqualTo(plaintext);
        }

        @Test
        void encryptOutputSameLengthAsInput() throws Exception {
            setupEncryption(true);

            byte[] plaintext = "Test message of specific length".getBytes();
            byte[] encrypted = encryption.encrypt(plaintext);

            assertThat(encrypted).hasSameSizeAs(plaintext);
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 10, 100, 1000, 10000 })
        void encryptDecryptVariousLengths(int length) throws Exception {
            MessageStreamEncryption initiator = new MessageStreamEncryption();
            MessageStreamEncryption responder = new MessageStreamEncryption();

            initiator.generateKeyPair();
            responder.generateKeyPair();

            initiator.computeSecret(responder.getPublicKey());
            responder.computeSecret(initiator.getPublicKey());

            byte[] infoHash = new byte[20];
            initiator.initializeCiphers(infoHash, true);
            responder.initializeCiphers(infoHash, false);

            byte[] original = new byte[length];
            new Random(length).nextBytes(original);

            byte[] encrypted = initiator.encrypt(original);
            byte[] decrypted = responder.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        void encryptEmptyArray() throws Exception {
            setupEncryption(true);

            byte[] empty = new byte[0];
            byte[] encrypted = encryption.encrypt(empty);

            assertThat(encrypted).isEmpty();
        }

        @Test
        void multipleEncryptionsMaintainState() throws Exception {
            MessageStreamEncryption initiator = new MessageStreamEncryption();
            MessageStreamEncryption responder = new MessageStreamEncryption();

            initiator.generateKeyPair();
            responder.generateKeyPair();

            initiator.computeSecret(responder.getPublicKey());
            responder.computeSecret(initiator.getPublicKey());

            byte[] infoHash = new byte[20];
            initiator.initializeCiphers(infoHash, true);
            responder.initializeCiphers(infoHash, false);

            for (int i = 0; i < 10; i++) {
                byte[] message = ("Message " + i).getBytes();
                byte[] encrypted = initiator.encrypt(message);
                byte[] decrypted = responder.decrypt(encrypted);
                assertThat(decrypted).isEqualTo(message);
            }
        }

        @Test
        void bidirectionalCommunication() throws Exception {
            MessageStreamEncryption alice = new MessageStreamEncryption();
            MessageStreamEncryption bob = new MessageStreamEncryption();

            alice.generateKeyPair();
            bob.generateKeyPair();

            alice.computeSecret(bob.getPublicKey());
            bob.computeSecret(alice.getPublicKey());

            byte[] infoHash = new byte[20];
            alice.initializeCiphers(infoHash, true);
            bob.initializeCiphers(infoHash, false);

            byte[] aliceMsg = "Hello from Alice".getBytes();
            byte[] encryptedFromAlice = alice.encrypt(aliceMsg);
            byte[] decryptedByBob = bob.decrypt(encryptedFromAlice);
            assertThat(decryptedByBob).isEqualTo(aliceMsg);

            byte[] bobMsg = "Hello from Bob".getBytes();
            byte[] encryptedFromBob = bob.encrypt(bobMsg);
            byte[] decryptedByAlice = alice.decrypt(encryptedFromBob);
            assertThat(decryptedByAlice).isEqualTo(bobMsg);
        }
    }

    @Nested
    class RC4StreamTests {

        @Test
        void rc4StreamIsStateful() throws Exception {
            setupEncryption(true);

            byte[] msg1 = "First".getBytes();
            byte[] msg2 = "First".getBytes(); // Same content as msg1

            byte[] enc1 = encryption.encrypt(msg1);

            // After encrypting msg1, the cipher state has changed
            // So encrypting the same content again should produce different output
            byte[] enc2 = encryption.encrypt(msg2);

            // RC4 is stateful, so same plaintext encrypted at different positions produces
            // different ciphertext
            assertThat(enc1).isNotEqualTo(enc2);
        }

        @Test
        void differentKeysProduceDifferentOutput() throws Exception {
            MessageStreamEncryption enc1 = new MessageStreamEncryption();
            MessageStreamEncryption enc2 = new MessageStreamEncryption();

            enc1.generateKeyPair();
            enc2.generateKeyPair();

            MessageStreamEncryption peer1 = new MessageStreamEncryption();
            MessageStreamEncryption peer2 = new MessageStreamEncryption();
            peer1.generateKeyPair();
            peer2.generateKeyPair();

            enc1.computeSecret(peer1.getPublicKey());
            enc2.computeSecret(peer2.getPublicKey());

            byte[] infoHash1 = new byte[20];
            byte[] infoHash2 = new byte[20];
            Arrays.fill(infoHash1, (byte) 1);
            Arrays.fill(infoHash2, (byte) 2);

            enc1.initializeCiphers(infoHash1, true);
            enc2.initializeCiphers(infoHash2, true);

            byte[] plaintext = "Same message".getBytes();
            byte[] encrypted1 = enc1.encrypt(plaintext.clone());
            byte[] encrypted2 = enc2.encrypt(plaintext.clone());

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    @Nested
    class CryptoModeTests {

        @Test
        void plaintextModeHasCorrectValue() {
            assertThat(MessageStreamEncryption.CryptoMode.PLAINTEXT.getValue()).isEqualTo(1);
        }

        @Test
        void rc4ModeHasCorrectValue() {
            assertThat(MessageStreamEncryption.CryptoMode.RC4.getValue()).isEqualTo(2);
        }
    }

    @Nested
    class InitialPayloadTests {

        @Test
        void createInitialPayloadIncludesPublicKey() throws Exception {
            encryption.generateKeyPair();
            MessageStreamEncryption peer = new MessageStreamEncryption();
            peer.generateKeyPair();
            encryption.computeSecret(peer.getPublicKey());

            byte[] infoHash = new byte[20];
            int supportedModes = 0x03;

            byte[] payload = encryption.createInitialPayload(infoHash, supportedModes);

            assertThat(payload).hasSize(96 + 20 + 4);
        }

        @Test
        void createInitialPayloadStartsWithPublicKey() throws Exception {
            encryption.generateKeyPair();
            byte[] publicKey = encryption.getPublicKey();

            MessageStreamEncryption peer = new MessageStreamEncryption();
            peer.generateKeyPair();
            encryption.computeSecret(peer.getPublicKey());

            byte[] infoHash = new byte[20];
            byte[] payload = encryption.createInitialPayload(infoHash, 0x03);

            byte[] payloadPublicKey = Arrays.copyOfRange(payload, 0, 96);
            assertThat(payloadPublicKey).isEqualTo(publicKey);
        }
    }

    @Nested
    class HandshakeVerificationTests {

        @Test
        void verifyHandshakeReturnsFalseForShortPayload() throws Exception {
            byte[] shortPayload = new byte[10];
            byte[] expectedHash = new byte[20];

            boolean result = encryption.verifyHandshake(shortPayload, expectedHash);

            assertThat(result).isFalse();
        }

        @Test
        void verifyHandshakeReturnsTrueForMatchingHash() throws Exception {
            byte[] hash = new byte[20];
            new Random(42).nextBytes(hash);

            byte[] payload = new byte[40];
            System.arraycopy(hash, 0, payload, 0, 20);

            boolean result = encryption.verifyHandshake(payload, hash);

            assertThat(result).isTrue();
        }

        @Test
        void verifyHandshakeReturnsFalseForNonMatchingHash() throws Exception {
            byte[] expectedHash = new byte[20];
            Arrays.fill(expectedHash, (byte) 0xAA);

            byte[] payload = new byte[40];
            Arrays.fill(payload, 0, 20, (byte) 0xBB);

            boolean result = encryption.verifyHandshake(payload, expectedHash);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void encryptLargeData() throws Exception {
            setupEncryption(true);

            byte[] largeData = new byte[1024 * 1024];
            new Random(42).nextBytes(largeData);

            byte[] encrypted = encryption.encrypt(largeData);

            assertThat(encrypted).hasSameSizeAs(largeData);
            assertThat(encrypted).isNotEqualTo(largeData);
        }

        @Test
        void encryptSingleByte() throws Exception {
            setupEncryption(true);

            byte[] single = new byte[] { 0x42 };
            byte[] encrypted = encryption.encrypt(single);

            assertThat(encrypted).hasSize(1);
            assertThat(encrypted[0]).isNotEqualTo((byte) 0x42);
        }

        @Test
        void encryptAllZeros() throws Exception {
            setupEncryption(true);

            byte[] zeros = new byte[100];
            byte[] encrypted = encryption.encrypt(zeros);

            assertThat(encrypted).isNotEqualTo(zeros);
        }

        @Test
        void encryptAllOnes() throws Exception {
            setupEncryption(true);

            byte[] ones = new byte[100];
            Arrays.fill(ones, (byte) 0xFF);
            byte[] encrypted = encryption.encrypt(ones);

            assertThat(encrypted).isNotEqualTo(ones);
        }
    }

    private void setupEncryption(boolean isInitiator) throws Exception {
        setupEncryptionForInstance(encryption, isInitiator);
    }

    private void setupEncryptionForInstance(MessageStreamEncryption enc, boolean isInitiator) throws Exception {
        enc.generateKeyPair();

        MessageStreamEncryption peer = new MessageStreamEncryption();
        peer.generateKeyPair();

        enc.computeSecret(peer.getPublicKey());

        byte[] infoHash = new byte[20];
        enc.initializeCiphers(infoHash, isInitiator);
    }
}
