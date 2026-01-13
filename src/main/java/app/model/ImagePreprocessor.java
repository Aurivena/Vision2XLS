package app.model;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImagePreprocessor {
    private static final int TARGET_SIZE = 512;

    public static Path resize(Path path) throws IOException {
        BufferedImage original = ImageIO.read(path.toFile());

        int width = original.getWidth();
        int height = original.getHeight();

        if (width > height) {
            height = TARGET_SIZE;
            width = (int) ((double) original.getWidth() / original.getHeight() * height);
        } else {
            width = TARGET_SIZE;
            height = (int) ((double) original.getHeight() / original.getWidth() * width);
        }

        Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        resized.getGraphics().drawImage(scaled, 0, 0, null);

        Path newFile = Files.createTempFile("resized_" + path.getFileName(), ".png");
        ImageIO.write(resized, "png", newFile.toFile());
        return newFile;
    }
}
