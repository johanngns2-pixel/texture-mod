package dev.packstudio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

/** Ein Resourcepack (Ordner oder ZIP). Nur lesen; Schreiben macht Recolor/Screen. */
public class Pack {
    public final String name;
    public final Path path;
    public final boolean zip;
    private static final String ITEMS = "assets/minecraft/textures/item/";

    public Pack(Path path) {
        this.path = path;
        this.zip = Files.isRegularFile(path);
        String n = path.getFileName().toString();
        this.name = zip && n.toLowerCase().endsWith(".zip") ? n.substring(0, n.length() - 4) : n;
    }

    public static List<Pack> scan(Path dir) {
        List<Pack> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.sorted().forEach(p -> {
                String fn = p.getFileName().toString().toLowerCase();
                if (Files.isDirectory(p) || fn.endsWith(".zip")) out.add(new Pack(p));
            });
        } catch (IOException ignored) {}
        return out;
    }

    /** Namen aller Item-Texturen ohne .png, z.B. "diamond_sword". */
    public List<String> itemNames() {
        List<String> r = new ArrayList<>();
        try {
            if (zip) {
                try (ZipFile z = new ZipFile(path.toFile())) {
                    for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
                        String n = e.nextElement().getName().replace('\\', '/');
                        if (n.startsWith(ITEMS) && n.endsWith(".png") && n.indexOf('/', ITEMS.length()) < 0)
                            r.add(n.substring(ITEMS.length(), n.length() - 4));
                    }
                }
            } else {
                Path d = path.resolve(ITEMS);
                if (Files.isDirectory(d)) try (Stream<Path> s = Files.list(d)) {
                    s.forEach(p -> {
                        String f = p.getFileName().toString();
                        if (f.endsWith(".png")) r.add(f.substring(0, f.length() - 4));
                    });
                }
            }
        } catch (IOException ignored) {}
        Collections.sort(r);
        return r;
    }

    public byte[] read(String rel) {
        try {
            if (zip) {
                try (ZipFile z = new ZipFile(path.toFile())) {
                    ZipEntry e = z.getEntry(rel);
                    if (e == null) return null;
                    try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); }
                }
            }
            Path p = path.resolve(rel);
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) { return null; }
    }

    public byte[] itemPng(String item) { return read(ITEMS + item + ".png"); }
    public byte[] itemMeta(String item) { return read(ITEMS + item + ".png.mcmeta"); }
    public byte[] packMeta() { return read("pack.mcmeta"); }

    @Override public String toString() { return name; }
}
