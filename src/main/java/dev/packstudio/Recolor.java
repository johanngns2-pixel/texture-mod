package dev.packstudio;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.*;

/** Faerbt alle PNGs eines Packs auf eine Zielfarbe um (Helligkeit/Details bleiben, Farbton wird verschoben). Schreibt immer eine Kopie. */
public class Recolor {
    public static Path run(Pack src, int rgb, double strength) throws IOException {
        Path outDir = src.path.getParent().resolve(src.name + "_edited");
        float[] target = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        if (src.zip) {
            try (ZipFile z = new ZipFile(src.path.toFile())) {
                for (java.util.Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
                    ZipEntry ze = e.nextElement();
                    if (ze.isDirectory()) continue;
                    Path dst = outDir.resolve(ze.getName()).normalize();
                    if (!dst.startsWith(outDir)) continue; // zip-slip Schutz
                    Files.createDirectories(dst.getParent());
                    try (InputStream in = z.getInputStream(ze)) { handle(ze.getName(), in.readAllBytes(), dst, target, strength); }
                }
            }
        } else {
            try (Stream<Path> s = Files.walk(src.path)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    Path rel = src.path.relativize(p);
                    Path dst = outDir.resolve(rel.toString());
                    Files.createDirectories(dst.getParent());
                    handle(rel.toString().replace('\\', '/'), Files.readAllBytes(p), dst, target, strength);
                }
            }
        }
        return outDir;
    }

    private static void handle(String name, byte[] data, Path dst, float[] t, double k) throws IOException {
        if (name.toLowerCase().endsWith(".png") && name.startsWith("assets/")) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) {
                BufferedImage o = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int a = argb >>> 24;
                    if (a == 0) { o.setRGB(x, y, 0); continue; }
                    float[] hsb = Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
                    float s = hsb[1] < 0.06f ? Math.min(1f, t[1] * 0.6f) : Math.min(1f, hsb[1] * (0.4f + t[1]));
                    int shifted = Color.HSBtoRGB(t[0], s, hsb[2]);
                    int r = lerp((argb >> 16) & 255, (shifted >> 16) & 255, k);
                    int g = lerp((argb >> 8) & 255, (shifted >> 8) & 255, k);
                    int b = lerp(argb & 255, shifted & 255, k);
                    o.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(o, "png", bo);
                Files.write(dst, bo.toByteArray());
                return;
            }
        }
        Files.write(dst, data);
    }

    private static int lerp(int a, int b, double k) { return (int) Math.round(a + (b - a) * k); }
}
