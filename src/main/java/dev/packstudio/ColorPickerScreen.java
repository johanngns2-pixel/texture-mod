package dev.packstudio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.Click;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.function.IntConsumer;

/** Farbauswahl: SV-Feld + Farbtonleiste + Hex. Rechts die Palette: anklicken = waehlen, ziehen = umsortieren, Rechtsklick = loeschen. */
public class ColorPickerScreen extends Screen {
    private static final int SQ = 128, CELLS = 32, SW = 18, GAP = 4, COLS = 6;

    private final Screen parent;
    private final IntConsumer onDone;
    private float h, s, v;
    private TextFieldWidget fHex;
    private boolean dragSV, dragH;
    private int dragIdx = -1, dragX, dragY;
    private boolean dragMoved;
    private int sqX, sqY, hueX, palX, palY;

    public ColorPickerScreen(Screen parent, int rgb, IntConsumer onDone) {
        super(Text.literal("Farbe waehlen"));
        this.parent = parent;
        this.onDone = onDone;
        setRgb(rgb);
        Palette.load();
    }

    private void setRgb(int rgb) {
        float[] f = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        h = f[0]; s = f[1]; v = f[2];
    }

    private int rgb() { return Color.HSBtoRGB(h, s, v) & 0xFFFFFF; }

    @Override
    protected void init() {
        sqX = width / 2 - 170; sqY = height / 2 - 80;
        hueX = sqX + SQ + 10;
        palX = hueX + 40; palY = sqY + 20;
        fHex = new TextFieldWidget(textRenderer, sqX, sqY + SQ + 30, 70, 16, Text.literal("Hex"));
        fHex.setText(String.format("#%06X", rgb()));
        fHex.setChangedListener(t -> {
            if (fHex.isFocused()) try { setRgb(Integer.parseInt(t.replace("#", "").trim(), 16)); } catch (Exception ignored) {}
        });
        addDrawableChild(fHex);
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Palette"), b -> {
            int c = rgb(); if (!Palette.colors.contains(c)) { Palette.colors.add(c); Palette.save(); }
        }).dimensions(sqX + 76, sqY + SQ + 28, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Uebernehmen"), b -> { onDone.accept(rgb()); close(); })
                .dimensions(sqX, sqY + SQ + 56, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"), b -> close())
                .dimensions(sqX + 106, sqY + SQ + 56, 80, 20).build());
    }

    private void syncHex() { if (fHex != null && !fHex.isFocused()) fHex.setText(String.format("#%06X", rgb())); }

    private int[] swatchPos(int i) { return new int[]{palX + (i % COLS) * (SW + GAP), palY + (i / COLS) * (SW + GAP)}; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xE00B0B10);
        ctx.drawText(textRenderer, "Farbe waehlen", sqX, sqY - 16, 0xFFFFFFFF, true);
        int cs = SQ / CELLS;
        for (int cx = 0; cx < CELLS; cx++) for (int cy = 0; cy < CELLS; cy++) {
            int c = Color.HSBtoRGB(h, cx / (float) (CELLS - 1), 1f - cy / (float) (CELLS - 1));
            ctx.fill(sqX + cx * cs, sqY + cy * cs, sqX + cx * cs + cs, sqY + cy * cs + cs, 0xFF000000 | c);
        }
        int mxp = sqX + (int) (s * (SQ - 1)), myp = sqY + (int) ((1 - v) * (SQ - 1));
        ctx.fill(mxp - 3, myp - 3, mxp + 4, myp + 4, 0xFFFFFFFF);
        ctx.fill(mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFF000000 | rgb());
        for (int y = 0; y < SQ; y++)
            ctx.fill(hueX, sqY + y, hueX + 14, sqY + y + 1, 0xFF000000 | Color.HSBtoRGB(y / (float) (SQ - 1), 1f, 1f));
        int hy = sqY + (int) (h * (SQ - 1));
        ctx.fill(hueX - 2, hy - 1, hueX + 16, hy + 2, 0xFFFFFFFF);
        ctx.fill(sqX + 142, sqY - 18, sqX + 158 + 0, sqY - 2, 0xFF000000 | rgb());

        ctx.drawText(textRenderer, "Palette (ziehen = sortieren, Rechtsklick = weg)", palX - 2, palY - 14, 0xFFAAAAAA, false);
        for (int i = 0; i < Palette.colors.size(); i++) {
            if (i == dragIdx && dragMoved) continue;
            int[] p = swatchPos(i);
            ctx.fill(p[0] - 1, p[1] - 1, p[0] + SW + 1, p[1] + SW + 1, Palette.colors.get(i) == rgb() ? 0xFFFFFFFF : 0xFF404050);
            ctx.fill(p[0], p[1], p[0] + SW, p[1] + SW, 0xFF000000 | Palette.colors.get(i));
        }
        if (dragIdx >= 0 && dragMoved) {
            int tgt = slotAt(dragX, dragY);
            if (tgt >= 0) { int[] p = swatchPos(tgt); ctx.fill(p[0] - 3, p[1] - 3, p[0] + SW + 3, p[1] + SW + 3, 0x80FFFFFF); }
            ctx.fill(dragX - SW / 2, dragY - SW / 2, dragX + SW / 2, dragY + SW / 2, 0xFF000000 | Palette.colors.get(dragIdx));
        }
        super.render(ctx, mx, my, delta);
    }

    private int slotAt(double x, double y) {
        int c = (int) Math.floor((x - palX) / (SW + GAP)), r = (int) Math.floor((y - palY) / (SW + GAP));
        if (c < 0 || c >= COLS || r < 0) return -1;
        int i = r * COLS + c;
        return i < Palette.colors.size() ? i : -1;
    }

    private void pick(double x, double y) {
        if (dragSV) {
            s = (float) Math.max(0, Math.min(1, (x - sqX) / (SQ - 1)));
            v = 1f - (float) Math.max(0, Math.min(1, (y - sqY) / (SQ - 1)));
        } else if (dragH) {
            h = (float) Math.max(0, Math.min(1, (y - sqY) / (SQ - 1)));
        }
        syncHex();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double x = click.x(), y = click.y();
        if (x >= sqX && x < sqX + SQ && y >= sqY && y < sqY + SQ) { dragSV = true; fHex.setFocused(false); pick(x, y); return true; }
        if (x >= hueX && x < hueX + 14 && y >= sqY && y < sqY + SQ) { dragH = true; fHex.setFocused(false); pick(x, y); return true; }
        int i = slotAt(x, y);
        if (i >= 0) {
            if (click.button() == 1) { Palette.colors.remove(i); Palette.save(); }
            else { dragIdx = i; dragMoved = false; dragX = (int) x; dragY = (int) y; }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragSV || dragH) { pick(click.x(), click.y()); return true; }
        if (dragIdx >= 0) { dragMoved = true; dragX = (int) click.x(); dragY = (int) click.y(); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragIdx >= 0) {
            if (dragMoved) {
                int t = slotAt(click.x(), click.y());
                if (t >= 0 && t != dragIdx) {
                    int c = Palette.colors.remove(dragIdx);
                    Palette.colors.add(t, c);
                    Palette.save();
                }
            } else { setRgb(Palette.colors.get(dragIdx)); fHex.setFocused(false); syncHex(); }
            dragIdx = -1; dragMoved = false;
            return true;
        }
        dragSV = dragH = false;
        return super.mouseReleased(click);
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
