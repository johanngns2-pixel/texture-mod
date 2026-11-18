package dev.packstudio;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;

/** Geladene Item-Textur inkl. Animation (Vanilla-mcmeta-Format, 1 Tick = 50 ms). */
public class Anim {
    public final List<BufferedImage> frames = new ArrayList<>();
    public final List<int[]> sequence = new ArrayList<>(); // {frameIndex, ticks}
    public int totalTicks = 1;
    public int avgRgb = 0x888888;

    public static Anim load(Pack p, String item) {
        try {
            byte[] png = p.itemPng(item);
            if (png == null) return null;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return null;
            Anim a = new Anim();
            int w = img.getWidth(), h = img.getHeight();
            int fw = w, fh = w;
            int defTime = 1;
            JsonArray framesJson = null;
            byte[] meta = p.itemMeta(item);
            if (meta != null && h > w) {
                JsonObject an = JsonParser.parseString(new String(meta, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animation");
                if (an != null) {
                    if (an.has("frametime")) defTime = Math.max(1, an.get("frametime").getAsInt());
                    if (an.has("width")) fw = an.get("width").getAsInt();
                    if (an.has("height")) fh = an.get("height").getAsInt();
                    if (an.has("frames")) framesJson = an.getAsJsonArray("frames");
                } else meta = null;
            } else meta = null;
            if (meta == null) { fw = w; fh = h; }
            int count = Math.max(1, (h / fh) * (w / fw));
            int cols = Math.max(1, w / fw);
            for (int i = 0; i < count; i++)
                a.frames.add(img.getSubimage((i % cols) * fw, (i / cols) * fh, fw, fh));
            if (framesJson != null) {
                for (JsonElement e : framesJson) {
                    if (e.isJsonObject()) {
                        JsonObject o = e.getAsJsonObject();
                        a.sequence.add(new int[]{o.get("index").getAsInt() % count,
                                o.has("time") ? Math.max(1, o.get("time").getAsInt()) : defTime});
                    } else a.sequence.add(new int[]{e.getAsInt() % count, defTime});
                }
            }
            if (a.sequence.isEmpty()) for (int i = 0; i < count; i++) a.sequence.add(new int[]{i, defTime});
            a.totalTicks = 0;
            for (int[] s : a.sequence) a.totalTicks += s[1];

            long r = 0, g = 0, b = 0, n = 0;
            BufferedImage f0 = a.frames.get(0);
            for (int y = 0; y < f0.getHeight(); y++) for (int x = 0; x < f0.getWidth(); x++) {
                int argb = f0.getRGB(x, y);
                if ((argb >>> 24) > 128) { r += (argb >> 16) & 255; g += (argb >> 8) & 255; b += argb & 255; n++; }
            }
            if (n > 0) a.avgRgb = ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
            return a;
        } catch (Exception e) { return null; }
    }

    public BufferedImage at(double tick) {
        int t = (int) (tick % totalTicks);
        for (int[] s : sequence) { if (t < s[1]) return frames.get(s[0]); t -= s[1]; }
        return frames.get(0);
    }
}
