package vn.hmtruong.photobooth;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** Runs on the GitHub Actions JDK; verifies the PNG can be decoded pixel-for-pixel. */
public final class PngWriterCheck {
    public static void main(String[] args) throws Exception {
        int[][] expected = {
                {0xFF012345, 0xFFABCDEF, 0xFF000000},
                {0xFFFFFFFF, 0xFF102030, 0xFF445566}
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        LosslessPngWriter writer = new LosslessPngWriter(bytes, 3, 2);
        for (int[] row : expected) writer.writeRow(row);
        writer.finish();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        if (decoded == null || decoded.getWidth() != 3 || decoded.getHeight() != 2)
            throw new AssertionError("PNG dimensions or structure are invalid");
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                if (decoded.getRGB(x, y) != expected[y][x])
                    throw new AssertionError("PNG pixel mismatch at " + x + "," + y);
            }
        }
        ByteArrayOutputStream gradientBytes = new ByteArrayOutputStream();
        LosslessPngWriter gradientWriter = new LosslessPngWriter(gradientBytes, 256, 64);
        int[][] gradient = new int[64][256];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 256; x++) {
                gradient[y][x] = 0xff000000 | ((x + y) & 255) << 16
                        | ((2 * x + y) & 255) << 8 | ((x + 2 * y) & 255);
            }
            gradientWriter.writeRow(gradient[y]);
        }
        gradientWriter.finish();
        BufferedImage gradientDecoded = ImageIO.read(
                new ByteArrayInputStream(gradientBytes.toByteArray()));
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 256; x++) {
                if (gradientDecoded.getRGB(x, y) != gradient[y][x])
                    throw new AssertionError("Filtered PNG pixel mismatch at " + x + "," + y);
            }
        }
        if (gradientBytes.size() >= 256 * 64 * 3 / 4)
            throw new AssertionError("PNG prediction did not reduce the smooth image");
        System.out.println("Lossless PNG scanline check passed");
    }
}
