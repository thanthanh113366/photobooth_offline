package vn.hmtruong.photobooth;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Writes an RGB PNG one scanline at a time without holding the full image in memory. */
final class LosslessPngWriter {
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] IHDR = {'I', 'H', 'D', 'R'};
    private static final byte[] IDAT = {'I', 'D', 'A', 'T'};
    private static final byte[] IEND = {'I', 'E', 'N', 'D'};

    private final OutputStream output;
    private final IdatChunks chunks;
    private final DeflaterOutputStream compressed;
    private final byte[] row;
    private final int width;
    private final int height;
    private int writtenRows;
    private boolean finished;

    LosslessPngWriter(OutputStream output, int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > (Integer.MAX_VALUE - 1) / 3)
            throw new IOException("Kích thước PNG không được Android hỗ trợ.");
        this.output = output;
        this.width = width;
        this.height = height;
        this.row = new byte[1 + width * 3];
        output.write(SIGNATURE);
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = 8; // 8 bit per color channel.
        header[9] = 2; // RGB; the composition already has a white background.
        writeChunk(output, IHDR, header, header.length);
        chunks = new IdatChunks(output);
        compressed = new DeflaterOutputStream(chunks, new Deflater(Deflater.DEFAULT_COMPRESSION), 65536);
    }

    void writeRow(int[] argb) throws IOException {
        if (finished || writtenRows >= height || argb.length < width)
            throw new IOException("Số dòng PNG không hợp lệ.");
        row[0] = 0; // PNG filter None: pixel values remain exact.
        int offset = 1;
        for (int x = 0; x < width; x++) {
            int pixel = argb[x];
            row[offset++] = (byte) (pixel >>> 16);
            row[offset++] = (byte) (pixel >>> 8);
            row[offset++] = (byte) pixel;
        }
        compressed.write(row);
        writtenRows++;
    }

    void finish() throws IOException {
        if (writtenRows != height) throw new IOException("Ảnh PNG chưa được ghi đủ dòng.");
        if (finished) return;
        compressed.finish();
        chunks.flushChunk();
        writeChunk(output, IEND, new byte[0], 0);
        output.flush();
        finished = true;
        compressed.close();
    }

    void abort() {
        if (finished) return;
        try { compressed.close(); } catch (IOException ignored) { }
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void writeInt(OutputStream output, int value) throws IOException {
        output.write(value >>> 24);
        output.write(value >>> 16);
        output.write(value >>> 8);
        output.write(value);
    }

    private static void writeChunk(OutputStream output, byte[] type, byte[] data, int length) throws IOException {
        writeInt(output, length);
        output.write(type);
        output.write(data, 0, length);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data, 0, length);
        writeInt(output, (int) crc.getValue());
    }

    private static final class IdatChunks extends OutputStream {
        private final OutputStream output;
        private final byte[] data = new byte[65536];
        private int used;

        IdatChunks(OutputStream output) { this.output = output; }

        @Override public void write(int value) throws IOException {
            data[used++] = (byte) value;
            if (used == data.length) flushChunk();
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            while (length > 0) {
                int count = Math.min(length, data.length - used);
                System.arraycopy(bytes, offset, data, used, count);
                used += count;
                offset += count;
                length -= count;
                if (used == data.length) flushChunk();
            }
        }

        void flushChunk() throws IOException {
            if (used == 0) return;
            writeChunk(output, IDAT, data, used);
            used = 0;
        }

        @Override public void close() throws IOException { flushChunk(); }
    }
}
