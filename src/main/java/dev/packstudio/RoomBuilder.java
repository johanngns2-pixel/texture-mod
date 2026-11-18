package dev.packstudio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayDeque;
import java.util.Deque;

/** Baut den Showcase-Raum vor dem Spieler per Befehlen (Singleplayer mit Cheats / OP noetig). */
public class RoomBuilder {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static final int PER_TICK = 3;
    // Raum in lokalen Koordinaten: a = rechts, f = vorwaerts, y = hoch (0 = Spielerfuesse)
    private static final int A = 8, F0 = 1, F1 = 13, Y0 = -1, Y1 = 5;

    public static void tick(MinecraftClient client) {
        if (client.player == null || QUEUE.isEmpty()) return;
        for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) client.player.networkHandler.sendChatCommand(QUEUE.poll());
    }

    public static String build(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null) return "Nicht in einer Welt.";
        int q = ((int) Math.floor(p.getYaw() * 4 / 360.0 + 0.5)) & 3; // 0 S, 1 W, 2 N, 3 O
        int[] fx = {0, -1, 0, 1}, fz = {1, 0, -1, 0};
        int[] frameFacing = {2, 5, 3, 4}; // Frame schaut zum Spieler zurueck (0 down 1 up 2 N 3 S 4 W 5 E)
        int ox = (int) Math.floor(p.getX()), oy = (int) Math.floor(p.getY()), oz = (int) Math.floor(p.getZ());
        Loc l = new Loc(ox, oy, oz, fx[q], fz[q]);
        float standYaw = ((q + 2) & 3) * 90f;
        QUEUE.clear();

        // alte Frames/Stands im Raum entfernen
        int[] c1 = l.w(-A, F0), c2 = l.w(A, F1);
        int minX = Math.min(c1[0], c2[0]), maxX = Math.max(c1[0], c2[0]);
        int minZ = Math.min(c1[1], c2[1]), maxZ = Math.max(c1[1], c2[1]);
        String vol = "x=" + minX + ",y=" + (oy + Y0) + ",z=" + minZ + ",dx=" + (maxX - minX + 1) + ",dy=" + (Y1 - Y0 + 1) + ",dz=" + (maxZ - minZ + 1);
        QUEUE.add("kill @e[type=minecraft:item_frame," + vol + "]");
        QUEUE.add("kill @e[type=minecraft:armor_stand," + vol + "]");
        // Huelle + Boden + Tuer + Licht
        QUEUE.add(fill(l, -A, Y0, F0, A, Y1, F1, "minecraft:black_concrete hollow"));
        QUEUE.add(fill(l, -A + 1, Y0, F0 + 1, A - 1, Y0, F1 - 1, "minecraft:polished_diorite"));
        QUEUE.add(fill(l, -1, 0, F0, 1, 2, F0, "minecraft:air"));
        for (int f : new int[]{3, 7, 11}) QUEUE.add(fill(l, -6, Y1, f, 6, Y1, f, "minecraft:sea_lantern"));
        // Pedestals
        for (int i = 0; i < RoomConfig.STANDS; i++) {
            int a = (i - 2) * 3;
            QUEUE.add(fill(l, a - 1, 0, 7 - 1, a + 1, 0, 7 + 1, "minecraft:smooth_quartz_slab"));
        }
        // Item Frames an der Rueckwand
        for (int r = 0; r < RoomConfig.FROWS; r++) for (int c = 0; c < RoomConfig.FCOLS; c++) {
            String id = RoomConfig.frames[r][c];
            int a = (c - 3) * 2, y = 1 + r * 2;
            int[] w = l.w(a, F1 - 1);
            String item = id == null ? "" : ",Item:{id:\"" + id + "\",count:1}";
            QUEUE.add("summon minecraft:item_frame " + (w[0] + 0.5) + " " + (oy + y) + " " + (w[1] + 0.5)
                    + " {Facing:" + frameFacing[q] + "b,Invulnerable:1b" + item + "}");
        }
        // Armor Stands
        for (int i = 0; i < RoomConfig.STANDS; i++) {
            int a = (i - 2) * 3;
            int[] w = l.w(a, 7);
            StringBuilder eq = new StringBuilder();
            for (int s = 0; s < RoomConfig.SLOTS; s++) {
                String id = RoomConfig.stands[i][s];
                if (id == null) continue;
                if (eq.length() > 0) eq.append(',');
                eq.append(RoomConfig.SLOT_NAMES[s]).append(":{id:\"").append(id).append("\",count:1}");
            }
            QUEUE.add("summon minecraft:armor_stand " + (w[0] + 0.5) + " " + (oy + 0.5) + " " + (w[1] + 0.5)
                    + " {ShowArms:1b,Invulnerable:1b,Rotation:[" + standYaw + "f,0f],equipment:{" + eq + "}}");
        }
        return "Raum wird gebaut (" + QUEUE.size() + " Befehle) - Cheats/OP noetig.";
    }

    private static String fill(Loc l, int a1, int y1, int f1, int a2, int y2, int f2, String block) {
        int[] p = l.w(a1, f1), q = l.w(a2, f2);
        return "fill " + p[0] + " " + (l.oy + y1) + " " + p[1] + " " + q[0] + " " + (l.oy + y2) + " " + q[1] + " " + block;
    }

    private record Loc(int ox, int oy, int oz, int fx, int fz) {
        /** a = rechts, f = vorwaerts -> Welt-x/z. Rechts = (-fz, fx). */
        int[] w(int a, int f) { return new int[]{ox + f * fx + a * (-fz), oz + f * fz + a * fx}; }
    }
}
