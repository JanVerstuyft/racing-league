package be.jabapage.racingleague.f1telemetry.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class ImageColorExtractor {

    /**
     * Analyzes image border pixels to find a dominant solid background color.
     * Returns a Hex color string (e.g. "#FFFFFF" or "#E80020") or null if
     * background is transparent, too complex, or cannot be determined.
     */
    public static String extractBackgroundColor(byte[] logoBytes) {
        if (logoBytes == null || logoBytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(logoBytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w < 2 || h < 2) {
                return null;
            }

            // Sample points along the border (edges)
            List<int[]> borderColors = new ArrayList<>();
            // Top and bottom edges
            for (int x = 0; x < w; x += Math.max(1, w / 10)) {
                borderColors.add(getPixelRGBA(img.getRGB(x, 0)));
                borderColors.add(getPixelRGBA(img.getRGB(x, h - 1)));
            }
            // Left and right edges
            for (int y = 0; y < h; y += Math.max(1, h / 10)) {
                borderColors.add(getPixelRGBA(img.getRGB(0, y)));
                borderColors.add(getPixelRGBA(img.getRGB(w - 1, y)));
            }

            // Filter out transparent pixels (alpha < 150)
            List<int[]> solidColors = new ArrayList<>();
            for (int[] rgba : borderColors) {
                if (rgba[3] >= 150) {
                    solidColors.add(rgba);
                }
            }

            if (solidColors.isEmpty()) {
                // Background is transparent
                return null;
            }

            // Group close colors together (distance threshold < 15 in RGB space)
            Map<String, Integer> colorCounts = new HashMap<>();
            for (int[] c : solidColors) {
                String hex = String.format("#%02x%02x%02x", c[0], c[1], c[2]);
                colorCounts.put(hex, colorCounts.getOrDefault(hex, 0) + 1);
            }

            String dominantHex = null;
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    dominantHex = entry.getKey();
                }
            }

            // Only consider it a background if it represents a significant portion of the border (e.g. > 50%)
            if (dominantHex != null && maxCount >= borderColors.size() / 2) {
                return dominantHex;
            }
        } catch (Exception e) {
            // Ignore color extraction failure
        }
        return null;
    }

    private static int[] getPixelRGBA(int rgb) {
        int alpha = (rgb >> 24) & 0xff;
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return new int[]{red, green, blue, alpha};
    }
}
