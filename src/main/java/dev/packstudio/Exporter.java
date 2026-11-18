package dev.packstudio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Rendert das Showcase-Raster (Packs = Zeilen, Items = Spalten) und schickt die Frames an ffmpeg. */
public class Exporter {
    public static final int SECONDS = 20, FPS = 30;

    public static Path export(List<Pack> packs, List<String> items, String title, Consumer<String> status) throws Exception {
        // Texturen laden
        Anim[][] anims = new Anim[packs.size()][items.size()];
        int[] avg = new int[packs.size()];
        for (int p = 0; p < packs.size(); p++) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int i = 0; i < items.size(); i++) {
                Anim a = Anim.load(packs.get(p), items.get(i));
                anims[p][i] = a;
                if (a != null) { r += (a.avgRgb >> 16) & 255; g += (a.avgRgb >> 8) & 255; b += a.avgRgb & 255; n++; }
            }
            avg[p] = n == 0 ? 0x888888 : ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        }

        int n = Math.max(1, items.size());
        int labelW = 300, pad = 20, gap = 12, top = 70;
        int cell = Math.min(96, (1920 - labelW - pad * 2) / n - gap);
        cell = Math.max(32, cell);
        int scale = Math.max(1, (cell - 12) / 16);
        int W = labelW + pad + n * (cell + gap) + pad;
        int H = top + packs.size() * (cell + gap) + pad;
        W += W & 1; H += H & 1;

        Files.createDirectories(PackStudio.exportDir());
        Path out = PackStudio.exportDir().resolve("showcase_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".mp4");

        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", W + "x" + H, "-r", String.valueOf(FPS), "-i", "-",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "16", "-pix_fmt", "yuv420p", out.toString());
        pb.redirectErrorStream(true);
        Process proc;
        try { proc = pb.start(); }
        catch (IOException e) { throw new IOException("ffmpeg nicht gefunden - bitte ffmpeg installieren und in den PATH packen."); }
        // ffmpeg-Ausgabe leerlesen, sonst blockiert die Pipe
        StringBuilder log = new StringBuilder();
        Thread drain = new Thread(() -> { try (InputStream in = proc.getInputStream()) { in.transferTo(new OutputStream() {
            public void write(int b) { if (log.length() < 2000) log.append((char) b); } }); } catch (IOException ignored) {} });
        drain.setDaemon(true); drain.start();

        BufferedImage canvas = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = ((DataBufferByte) canvas.getRaster().getDataBuffer()).getData();
        Font fBig = new Font(Font.MONOSPACED, Font.BOLD, 26), fMid = new Font(Font.MONOSPACED, Font.BOLD, 22),
                fSmall = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        int total = SECONDS * FPS;

        try (OutputStream os = new BufferedOutputStream(proc.getOutputStream(), 1 << 20)) {
            for (int f = 0; f < total; f++) {
                Graphics2D g = canvas.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.setColor(new Color(0x0A0A10)); g.fillRect(0, 0, W, H);
                g.setColor(Color.WHITE); g.setFont(fBig);
                g.drawString(title.toUpperCase(), pad, 44);

                double tick = f * 20.0 / FPS;
                for (int p = 0; p < packs.size(); p++) {
                    int y = top + p * (cell + gap);
                    g.setColor(new Color(avg[p])); g.fillRect(pad, y + 14, 36, 36);
                    g.setColor(new Color(0x555560)); g.drawRect(pad, y + 14, 36, 36);
                    g.setColor(Color.WHITE); g.setFont(fMid);
                    g.drawString(shorten(packs.get(p).name.toUpperCase(), 17), pad + 52, y + 34);
                    g.setColor(new Color(0x9A9AA6)); g.setFont(fSmall);
                    g.drawString(String.format("#%06X", avg[p]), pad + 52, y + 54);
                    for (int i = 0; i < items.size(); i++) {
                        int x = labelW + pad + i * (cell + gap);
                        g.setColor(new Color(0x0D0D14)); g.fillRect(x, y, cell, cell);
                        g.setColor(new Color(0x32323C)); g.drawRect(x, y, cell, cell);
                        Anim a = anims[p][i];
                        if (a == null) continue;
                        BufferedImage im = a.at(tick);
                        int sz = scale * 16;
                        g.drawImage(im, x + (cell - sz) / 2, y + (cell - sz) / 2, sz, sz, null);
                    }
                }
                g.dispose();
                os.write(data);
                if (f % 30 == 0) status.accept("Rendere Video... " + (f * 100 / total) + "%");
            }
        }
        int code = proc.waitFor();
        if (code != 0) throw new IOException("ffmpeg Fehler: " + log);
        return out;
    }

    private static String shorten(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "."; }
}
