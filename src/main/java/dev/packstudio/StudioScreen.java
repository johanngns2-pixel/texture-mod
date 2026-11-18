package dev.packstudio;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StudioScreen extends Screen {
    private static final int SIDE = 110, ROW = 12;
    private static final Set<String> PVP = new LinkedHashSet<>(Arrays.asList(
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "netherite_sword",
            "netherite_axe", "netherite_pickaxe", "diamond_helmet", "diamond_chestplate", "diamond_leggings",
            "diamond_boots", "diamond_sword", "diamond_axe", "mace", "totem_of_undying", "golden_apple",
            "enchanted_golden_apple", "ender_pearl", "wind_charge", "end_crystal", "shield", "trident", "bow",
            "crossbow", "arrow", "experience_bottle", "firework_rocket", "elytra", "cobweb", "respawn_anchor",
            "glowstone", "obsidian", "diamond_spear", "netherite_spear"));

    private final Screen parent;
    private int tab = 0; // 0 Showcase, 1 Editor

    private List<Pack> packs = new ArrayList<>();
    private final Set<Integer> selPacks = new LinkedHashSet<>();
    private final Set<String> selItems = new LinkedHashSet<>();
    private List<String> allItems = new ArrayList<>();
    private int packScroll, itemScroll;
    private volatile String status = "";

    private TextFieldWidget fTitle, fFilter, fDesc, fFormat, fColor, fStrength;
    private int editPack = -1;

    public StudioScreen(Screen parent) {
        super(Text.literal("Pack Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (packs.isEmpty()) packs = Pack.scan(PackStudio.packsDir());
        int cx = SIDE + 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("Showcase"), b -> { tab = 0; rebuild(); })
                .dimensions(6, 24, SIDE - 12, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pack-Editor"), b -> { tab = 1; rebuild(); })
                .dimensions(6, 48, SIDE - 12, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Showcase-Raum"), b -> client.setScreen(new RoomScreen(this)))
                .dimensions(6, 72, SIDE - 12, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schliessen"), b -> close())
                .dimensions(6, height - 26, SIDE - 12, 20).build());

        if (tab == 0) {
            String t = fTitle != null ? fTitle.getText() : "Pack Showcase";
            fTitle = new TextFieldWidget(textRenderer, cx + 150, 24, 180, 16, Text.literal("Titel"));
            fTitle.setText(t); addDrawableChild(fTitle);
            String fl = fFilter != null ? fFilter.getText() : "";
            fFilter = new TextFieldWidget(textRenderer, cx + 150, 48, 180, 16, Text.literal("Filter"));
            fFilter.setText(fl); fFilter.setChangedListener(s -> itemScroll = 0); addDrawableChild(fFilter);

            int by = height - 46;
            addDrawableChild(ButtonWidget.builder(Text.literal("PvP-Preset"), b -> selItems.addAll(PVP))
                    .dimensions(cx, by, 80, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Alle (gefiltert)"), b -> selItems.addAll(filtered()))
                    .dimensions(cx + 84, by, 90, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Keine"), b -> selItems.clear())
                    .dimensions(cx + 178, by, 50, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Video exportieren (20s)"), b -> startExport())
                    .dimensions(cx + 232, by, 150, 18).build());
        } else {
            int x = cx + 160;
            fDesc = new TextFieldWidget(textRenderer, x, 40, 220, 16, Text.literal("Beschreibung"));
            fDesc.setMaxLength(256); addDrawableChild(fDesc);
            fFormat = new TextFieldWidget(textRenderer, x, 76, 60, 16, Text.literal("pack_format"));
            addDrawableChild(fFormat);
            addDrawableChild(ButtonWidget.builder(Text.literal("pack.mcmeta speichern"), b -> saveMeta())
                    .dimensions(x, 98, 150, 18).build());
            String col = fColor != null ? fColor.getText() : "#9B30FF";
            fColor = new TextFieldWidget(textRenderer, x, 150, 70, 16, Text.literal("Farbe"));
            fColor.setText(col); addDrawableChild(fColor);
            addDrawableChild(ButtonWidget.builder(Text.literal("Farbwaehler"), b -> client.setScreen(
                    new ColorPickerScreen(this, parseColor(), c -> fColor.setText(String.format("#%06X", c)))))
                    .dimensions(x + 182, 150, 80, 16).build());
            fStrength = new TextFieldWidget(textRenderer, x + 80, 150, 40, 16, Text.literal("Staerke"));
            fStrength.setText("80"); addDrawableChild(fStrength);
            addDrawableChild(ButtonWidget.builder(Text.literal("Umfaerben (als Kopie)"), b -> startRecolor())
                    .dimensions(x, 172, 150, 18).build());
            if (editPack >= 0) loadMeta();
        }
    }

    private void rebuild() { clearChildren(); init(); }

    private List<String> filtered() {
        String f = fFilter == null ? "" : fFilter.getText().toLowerCase().trim();
        List<String> r = new ArrayList<>();
        for (String s : allItems) if (f.isEmpty() || s.contains(f)) r.add(s);
        return r;
    }

    private void refreshItems() {
        TreeSet<String> all = new TreeSet<>();
        for (int i : selPacks) all.addAll(packs.get(i).itemNames());
        allItems = new ArrayList<>(all);
        selItems.retainAll(all);
    }

    // ---------- Showcase ----------
    private void startExport() {
        if (selPacks.isEmpty() || selItems.isEmpty()) { status = "Waehl mindestens 1 Pack und 1 Item aus."; return; }
        List<Pack> ps = new ArrayList<>(); for (int i : selPacks) ps.add(packs.get(i));
        List<String> its = new ArrayList<>(selItems);
        String title = fTitle.getText();
        status = "Starte Export...";
        Thread t = new Thread(() -> {
            try {
                Path out = Exporter.export(ps, its, title, s -> status = s);
                status = "Fertig: " + out;
            } catch (Exception e) { status = "Fehler: " + e.getMessage(); }
        }, "packstudio-export");
        t.setDaemon(true); t.start();
    }

    // ---------- Editor ----------
    private void loadMeta() {
        Pack p = packs.get(editPack);
        fDesc.setText(""); fFormat.setText("");
        byte[] m = p.packMeta();
        if (m == null) { status = "Keine pack.mcmeta gefunden."; return; }
        try {
            JsonObject pk = JsonParser.parseString(new String(m, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
            if (pk.has("description")) {
                JsonElement d = pk.get("description");
                fDesc.setText(d.isJsonPrimitive() ? d.getAsString() : d.toString());
            }
            if (pk.has("pack_format")) fFormat.setText(pk.get("pack_format").getAsString());
        } catch (Exception e) { status = "mcmeta lesen fehlgeschlagen: " + e.getMessage(); }
    }

    private void saveMeta() {
        if (editPack < 0) { status = "Erst ein Pack waehlen."; return; }
        Pack p = packs.get(editPack);
        if (p.zip) { status = "ZIP kann nicht direkt bearbeitet werden - erst 'Umfaerben' (Kopie) oder entpacken."; return; }
        try {
            Path f = p.path.resolve("pack.mcmeta");
            JsonObject root = Files.exists(f)
                    ? JsonParser.parseString(Files.readString(f)).getAsJsonObject() : new JsonObject();
            Files.copy(f, p.path.resolve("pack.mcmeta.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            JsonObject pk = root.has("pack") ? root.getAsJsonObject("pack") : new JsonObject();
            pk.addProperty("description", fDesc.getText());
            try { pk.addProperty("pack_format", Integer.parseInt(fFormat.getText().trim())); } catch (NumberFormatException ignored) {}
            root.add("pack", pk);
            Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            status = "pack.mcmeta gespeichert (Backup: pack.mcmeta.bak)";
        } catch (Exception e) { status = "Fehler: " + e.getMessage(); }
    }

    private void startRecolor() {
        if (editPack < 0) { status = "Erst ein Pack waehlen."; return; }
        int rgb; double k;
        try {
            rgb = Integer.parseInt(fColor.getText().replace("#", "").trim(), 16);
            k = Math.max(0, Math.min(100, Double.parseDouble(fStrength.getText().trim()))) / 100.0;
        } catch (NumberFormatException e) { status = "Farbe als Hex (z.B. #9B30FF), Staerke 0-100."; return; }
        Pack p = packs.get(editPack);
        status = "Umfaerbe " + p.name + " ...";
        Thread t = new Thread(() -> {
            try { status = "Fertig: " + Recolor.run(p, rgb, k); }
            catch (Exception e) { status = "Fehler: " + e.getMessage(); }
        }, "packstudio-recolor");
        t.setDaemon(true); t.start();
    }

    // ---------- Rendering ----------
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xFF0B0B10);
        ctx.fill(0, 0, SIDE, height, 0xFF14141C);
        ctx.drawText(textRenderer, "PACK STUDIO", 8, 8, 0xFFFFFFFF, true);
        int x0 = SIDE + 10;

        // Packliste (beide Tabs)
        int listH = height - 90;
        ctx.drawText(textRenderer, tab == 0 ? "Packs (Mehrfach)" : "Pack waehlen", x0, 12, 0xFFAAAAAA, false);
        ctx.fill(x0, 24, x0 + 140, 24 + listH, 0xFF101018);
        for (int r = 0, i = packScroll; i < packs.size() && r * ROW + ROW <= listH; i++, r++) {
            boolean on = tab == 0 ? selPacks.contains(i) : i == editPack;
            int y = 24 + r * ROW;
            if (on) ctx.fill(x0, y, x0 + 140, y + ROW, 0xFF3A2A66);
            String n = packs.get(i).name; if (n.length() > 22) n = n.substring(0, 21) + ".";
            ctx.drawText(textRenderer, n, x0 + 3, y + 2, on ? 0xFFFFFFFF : 0xFFBBBBBB, false);
        }
        if (packs.isEmpty()) ctx.drawText(textRenderer, "(resourcepacks leer)", x0 + 3, 28, 0xFF888888, false);

        if (tab == 0) {
            ctx.drawText(textRenderer, "Titel", x0 + 150, 13, 0xFFAAAAAA, false);
            ctx.drawText(textRenderer, "Item-Filter", x0 + 150, 37, 0xFFAAAAAA, false);
            int ix = x0 + 150, iy = 72, iw = 230, ih = height - 72 - 54;
            ctx.fill(ix, iy, ix + iw, iy + ih, 0xFF101018);
            List<String> list = filtered();
            for (int r = 0, i = itemScroll; i < list.size() && (r + 1) * ROW <= ih; i++, r++) {
                String s = list.get(i); boolean on = selItems.contains(s);
                int y = iy + r * ROW;
                if (on) ctx.fill(ix, y, ix + iw, y + ROW, 0xFF3A2A66);
                ctx.drawText(textRenderer, (on ? "[x] " : "[ ] ") + s, ix + 3, y + 2, on ? 0xFFFFFFFF : 0xFFBBBBBB, false);
            }
            ctx.drawText(textRenderer, selPacks.size() + " Packs, " + selItems.size() + " Items", ix, iy + ih + 4, 0xFF888888, false);
        } else {
            int x = x0 + 160;
            ctx.drawText(textRenderer, "pack.mcmeta", x, 12, 0xFFFFFFFF, true);
            ctx.drawText(textRenderer, "Beschreibung", x, 30, 0xFFAAAAAA, false);
            ctx.drawText(textRenderer, "pack_format", x, 66, 0xFFAAAAAA, false);
            ctx.drawText(textRenderer, "Farben aendern", x, 126, 0xFFFFFFFF, true);
            ctx.drawText(textRenderer, "Hex-Farbe        Staerke %", x, 138, 0xFFAAAAAA, false);
            ctx.fill(x + 160, 150, x + 176, 166, 0xFF000000 | parseColor());
        }
        ctx.drawText(textRenderer, status, x0, height - 14, 0xFFFFD27F, false);
        super.render(ctx, mx, my, delta);
    }

    private int parseColor() {
        try { return Integer.parseInt(fColor.getText().replace("#", "").trim(), 16) & 0xFFFFFF; } catch (Exception e) { return 0x444444; }
    }

    // ---------- Input ----------
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        int x0 = SIDE + 10, listH = height - 90;
        if (mx >= x0 && mx < x0 + 140 && my >= 24 && my < 24 + listH) {
            int i = packScroll + (int) ((my - 24) / ROW);
            if (i >= 0 && i < packs.size()) {
                if (tab == 0) { if (!selPacks.remove(i)) selPacks.add(i); refreshItems(); }
                else { editPack = i; loadMeta(); }
            }
            return true;
        }
        if (tab == 0) {
            int ix = x0 + 150, iy = 72, iw = 230, ih = height - 72 - 54;
            if (mx >= ix && mx < ix + iw && my >= iy && my < iy + ih) {
                List<String> list = filtered();
                int i = itemScroll + (int) ((my - iy) / ROW);
                if (i >= 0 && i < list.size() && !selItems.remove(list.get(i))) selItems.add(list.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int x0 = SIDE + 10;
        int d = v > 0 ? -2 : 2;
        if (mx < x0 + 145) packScroll = Math.max(0, Math.min(Math.max(0, packs.size() - 3), packScroll + d));
        else if (tab == 0) itemScroll = Math.max(0, Math.min(Math.max(0, filtered().size() - 3), itemScroll + d));
        return true;
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
