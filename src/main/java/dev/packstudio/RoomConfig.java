package dev.packstudio;

import com.google.gson.*;
import java.nio.file.*;

/** Belegung des Showcase-Raums: Item Frames (Rueckwand) und Armor Stands (6 Slots je Stand). */
public class RoomConfig {
    public static final int FCOLS = 7, FROWS = 2, STANDS = 5, SLOTS = 6;
    public static final String[] SLOT_NAMES = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    public static final String[] SLOT_LABELS = {"Helm", "Brust", "Hose", "Schuhe", "Hand", "Zweithand"};
    /** frames[row][col], row 0 = untere Reihe. null = leer. */
    public static final String[][] frames = new String[FROWS][FCOLS];
    public static final String[][] stands = new String[STANDS][SLOTS];
    private static boolean loaded;

    private static Path file() { return PackStudio.exportDir().getParent().resolve("room.json"); }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            read(o.getAsJsonArray("frames"), frames);
            read(o.getAsJsonArray("stands"), stands);
        } catch (Exception ignored) {}
    }

    private static void read(JsonArray a, String[][] t) {
        for (int i = 0; i < t.length && i < a.size(); i++) {
            JsonArray r = a.get(i).getAsJsonArray();
            for (int j = 0; j < t[i].length && j < r.size(); j++) t[i][j] = r.get(j).isJsonNull() ? null : r.get(j).getAsString();
        }
    }

    private static JsonArray write(String[][] t) {
        JsonArray a = new JsonArray();
        for (String[] row : t) { JsonArray r = new JsonArray(); for (String s : row) r.add(s); a.add(r); }
        return a;
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            JsonObject o = new JsonObject();
            o.add("frames", write(frames));
            o.add("stands", write(stands));
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {}
    }
}
