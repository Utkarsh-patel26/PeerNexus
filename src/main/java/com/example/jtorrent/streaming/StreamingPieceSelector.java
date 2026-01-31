package com.example.jtorrent.streaming;

import com.example.jtorrent.parser.FileEntry;
import com.example.jtorrent.parser.TorrentFile;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceState;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class StreamingPieceSelector {

    private static final int HIGH_PRIORITY_PIECES = 20;
    private static final int BUFFER_PIECES = 50;

    private final PieceManager pieceManager;
    private final TorrentFile torrentFile;
    @SuppressWarnings("unused") // Stored for reference, used in calculations
    private final int fileIndex;
    @SuppressWarnings("unused") // Reserved for buffer management
    private final long bufferSize;
    private final int firstPiece;
    private final int lastPiece;
    private final long fileOffset;
    private final long fileLength;
    private final AtomicLong currentPosition = new AtomicLong(0);
    private final BitSet prioritizedPieces;

    public StreamingPieceSelector(PieceManager pieceManager, TorrentFile torrentFile, int fileIndex, long bufferSize) {
        this.pieceManager = pieceManager;
        this.torrentFile = torrentFile;
        this.fileIndex = fileIndex;
        this.bufferSize = bufferSize;
        this.prioritizedPieces = new BitSet(pieceManager.getPieceCount());

        FileEntry file = torrentFile.files().get(fileIndex);
        this.fileLength = file.length();
        this.fileOffset = calculateFileOffset(fileIndex);
        this.firstPiece = (int) (fileOffset / torrentFile.pieceLength());
        this.lastPiece = (int) ((fileOffset + fileLength - 1) / torrentFile.pieceLength());

        prioritizeInitialPieces();
    }

    private long calculateFileOffset(int targetIndex) {
        long offset = 0;
        List<FileEntry> files = torrentFile.files();
        for (int i = 0; i < targetIndex && i < files.size(); i++) {
            offset += files.get(i).length();
        }
        return offset;
    }

    private void prioritizeInitialPieces() {
        int piecesToPrioritize = Math.min(HIGH_PRIORITY_PIECES, lastPiece - firstPiece + 1);
        for (int i = 0; i < piecesToPrioritize; i++) {
            prioritizedPieces.set(firstPiece + i);
        }

        if (lastPiece > firstPiece + piecesToPrioritize) {
            prioritizedPieces.set(lastPiece);
        }
    }

    public void setCurrentPosition(long position) {
        currentPosition.set(position);
        updatePriorities();
    }

    private void updatePriorities() {
        long pos = currentPosition.get();
        int currentPiece = getFilePieceIndex(pos);

        prioritizedPieces.clear();

        int bufferPieces = (int) Math.min(BUFFER_PIECES, bufferSize / torrentFile.pieceLength());

        for (int i = 0; i < bufferPieces && currentPiece + i <= lastPiece; i++) {
            prioritizedPieces.set(currentPiece + i);
        }
    }

    public int getFilePieceIndex(long positionInFile) {
        long globalPosition = fileOffset + positionInFile;
        return (int) (globalPosition / torrentFile.pieceLength());
    }

    public List<Integer> selectPieces(BitSet peerBitfield, int maxPieces) {
        List<Integer> selected = new ArrayList<>();

        for (int i = prioritizedPieces.nextSetBit(0); i >= 0
                && selected.size() < maxPieces; i = prioritizedPieces.nextSetBit(i + 1)) {
            if (peerBitfield.get(i) && pieceManager.getPieceState(i) != PieceState.COMPLETE) {
                selected.add(i);
            }
        }

        if (selected.size() < maxPieces) {
            for (int i = firstPiece; i <= lastPiece && selected.size() < maxPieces; i++) {
                if (!prioritizedPieces.get(i) && peerBitfield.get(i) &&
                        pieceManager.getPieceState(i) != PieceState.COMPLETE) {
                    selected.add(i);
                }
            }
        }

        return selected;
    }

    public boolean isPrioritized(int pieceIndex) {
        return prioritizedPieces.get(pieceIndex);
    }

    public int getFirstPiece() {
        return firstPiece;
    }

    public int getLastPiece() {
        return lastPiece;
    }

    public double getBufferProgress() {
        int pos = getFilePieceIndex(currentPosition.get());
        int bufferEnd = Math.min(pos + BUFFER_PIECES, lastPiece);
        int total = bufferEnd - pos + 1;
        int complete = 0;

        for (int i = pos; i <= bufferEnd; i++) {
            if (pieceManager.getPieceState(i) == PieceState.COMPLETE) {
                complete++;
            }
        }

        return total > 0 ? (double) complete / total * 100.0 : 0.0;
    }

    public boolean isBufferReady() {
        int pos = getFilePieceIndex(currentPosition.get());
        int minBuffer = Math.min(5, lastPiece - pos + 1);

        for (int i = 0; i < minBuffer; i++) {
            if (pieceManager.getPieceState(pos + i) != PieceState.COMPLETE) {
                return false;
            }
        }
        return true;
    }
}
