package dev.packstudio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.Click;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

/** Belegung des Showcase-Raums: Slot anklicken, dann rechts ein beliebiges Item waehlen. Rechtsklick auf Slot = leeren. */
public class RoomScreen extends Screen {
    private static final int SLOT = 22, ROW = 18;
    private final Screen parent;
    private TextFieldWidget fFilter;
    private final List<String> all = new ArrayList<>();
    private List<String> shown = new ArrayList<>();
    private int scroll;
    private int selKind = -1, selA, selB; // 0 = Frame, 1 = Stand
    private String status = "";

    public RoomScreen(Screen parent) {
        super(Text.literal("Showcase-Raum"));
        this.parent = parent;
        RoomConfig.load();
        TreeSet<String> ids = new TreeSet<>();
        for (Identifier id : Registries.ITEM.getIds()) if (!id.getPath().equals("air")) ids.add(id.toString());
        all.addAll(ids);
        shown = all;
    }

    private int listX() { return width - 230; }

    @Override
    protected void init() {
        fFilter = new TextFieldWidget(textRenderer, listX(), 24, 220, 16, Text.literal("Filter"));
        fFilter.setChangedListener(t -> {
            String f = t.toLowerCase().trim();
            shown = new ArrayList<>();
            for (String s : all) if (f.isEmpty() || s.contains(f)) shown.add(s);
            scroll = 0;
        });
        addDrawableChild(fFilter);
        addDrawableChild(ButtonWidget.builder(Text.literal("Raum bauen (vor dir)"), b ->
                status = RoomBuilder.build(MinecraftClient.getInstance())).dimensions(10, height - 28, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Alles leeren"), b -> {
            for (String[] r : RoomConfig.frames) Arrays.fill(r, null);
            for (String[] r : RoomConfig.stands) Arrays.fill(r, null);
            RoomConfig.save();
        }).dimensions(166, height - 28, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurueck"), b -> close()).dimensions(262, height - 28, 70, 20).build());
    }

    private int frameX(int c) { return 10 + c * (SLOT + 4); }
    private int frameY(int r) { return 40 + (RoomConfig.FROWS - 1 - r) * (SLOT + 4); }
    private int standX(int s) { return 10 + s * (SLOT + 4); }
    private int standY(int i) { return 130 + i * (SLOT + 4); }

    private void drawSlot(DrawContext ctx, int x, int y, String id, boolean sel) {
        ctx.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, sel ? 0xFFFFFFFF : 0xFF404050);
        ctx.fill(x, y, x + SLOT, y + SLOT, 0xFF14141C);
        if (id != null) ctx.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(id))), x + 3, y + 3);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xFF0B0B10);
        ctx.drawText(textRenderer, "Showcase-Raum", 10, 8, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Item Frames (Rueckwand) - Slot waehlen, dann rechts Item klicken", 10, 28 - 2, 0xFFAAAAAA, false);
        for (int r = 0; r < RoomConfig.FROWS; r++) for (int c = 0; c < RoomConfig.FCOLS; c++)
            drawSlot(ctx, frameX(c), frameY(r), RoomConfig.frames[r][c], selKind == 0 && selA == r && selB == c);
        ctx.drawText(textRenderer, "Armor Stands:  Helm | Brust | Hose | Schuhe | Hand | Zweithand", 10, 118, 0xFFAAAAAA, false);
        for (int i = 0; i < RoomConfig.STANDS; i++) {
            for (int s = 0; s < RoomConfig.SLOTS; s++)
                drawSlot(ctx, standX(s), standY(i), RoomConfig.stands[i][s], selKind == 1 && selA == i && selB == s);
            ctx.drawText(textRenderer, "Stand " + (i + 1), standX(RoomConfig.SLOTS) + 4, standY(i) + 7, 0xFF888888, false);
        }
        int lx = listX(), ly = 44, lh = height - 44 - 40;
        ctx.fill(lx, ly, lx + 220, ly + lh, 0xFF101018);
        for (int r = 0, i = scroll; i < shown.size() && (r + 1) * ROW <= lh; i++, r++) {
            String id = shown.get(i);
            int y = ly + r * ROW;
            if (mx >= lx && mx < lx + 220 && my >= y && my < y + ROW) ctx.fill(lx, y, lx + 220, y + ROW, 0xFF3A2A66);
            ctx.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(id))), lx + 1, y + 1);
            ctx.drawText(textRenderer, id.startsWith("minecraft:") ? id.substring(10) : id, lx + 20, y + 5, 0xFFDDDDDD, false);
        }
        ctx.drawText(textRenderer, status, 10, height - 42, 0xFFFFD27F, false);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double x = click.x(), y = click.y();
        boolean right = click.button() == 1;
        for (int r = 0; r < RoomConfig.FROWS; r++) for (int c = 0; c < RoomConfig.FCOLS; c++)
            if (in(x, y, frameX(c), frameY(r))) {
                if (right) { RoomConfig.frames[r][c] = null; RoomConfig.save(); }
                else { selKind = 0; selA = r; selB = c; }
                return true;
            }
        for (int i = 0; i < RoomConfig.STANDS; i++) for (int s = 0; s < RoomConfig.SLOTS; s++)
            if (in(x, y, standX(s), standY(i))) {
                if (right) { RoomConfig.stands[i][s] = null; RoomConfig.save(); }
                else { selKind = 1; selA = i; selB = s; }
                return true;
            }
        int lx = listX(), ly = 44, lh = height - 44 - 40;
        if (x >= lx && x < lx + 220 && y >= ly && y < ly + lh) {
            int i = scroll + (int) ((y - ly) / ROW);
            if (i >= 0 && i < shown.size() && selKind >= 0) {
                if (selKind == 0) RoomConfig.frames[selA][selB] = shown.get(i);
                else RoomConfig.stands[selA][selB] = shown.get(i);
                RoomConfig.save();
            }
            return true;
        }
        return false;
    }

    private boolean in(double x, double y, int sx, int sy) { return x >= sx && x < sx + SLOT && y >= sy && y < sy + SLOT; }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - 3), scroll + (v > 0 ? -3 : 3)));
        return true;
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
