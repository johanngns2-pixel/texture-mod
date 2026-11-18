package dev.packstudio;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Gespeicherte Farbpalette (Reihenfolge frei aenderbar). */
public class Palette {
    public static final List<Integer> colors = new ArrayList<>();
    private static boolean loaded;

    private static Path file() { return PackStudio.exportDir().getParent().resolve("palette.json"); }

    public static void load() {
        if (loaded) return;
        loaded = true;
        colors.clear();
        try {
            Path f = file();
            if (Files.exists(f)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(f)).getAsJsonArray())
                    colors.add(Integer.parseInt(e.getAsString().replace("#", ""), 16) & 0xFFFFFF);
            }
        } catch (Exception ignored) {}
        if (colors.isEmpty()) colors.addAll(Arrays.asList(0x9B30FF, 0xFF3030, 0xFF9A1F, 0xFFE62E,
                0x3CDC5A, 0x25D5E0, 0x2F6BFF, 0xFF4FB8, 0xFFFFFF, 0x808080, 0x202020));
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            JsonArray a = new JsonArray();
            for (int c : colors) a.add(String.format("#%06X", c));
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(a));
        } catch (Exception ignored) {}
    }
}
