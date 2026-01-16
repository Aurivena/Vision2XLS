package app.model;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImagePreprocessor {
    public static File convertPdfToImage(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 130, ImageType.RGB);

            File tempFile = File.createTempFile("pdf_proc_", ".jpg");
            tempFile.deleteOnExit();

            ImageIO.write(image, "jpg", tempFile);
            return tempFile;
        }
    }
}
