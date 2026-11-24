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
        System.out.println("Lossless PNG scanline check passed");
    }
}
