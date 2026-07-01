package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.KeyUtil;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ModernKeybindsElement extends HudElement {

    private static final float HEADER_H = 43f, ROW_H = 43f, ROW_ADVANCE = 50f, HEADER_GAP = 8f;
    private static final float MIN_WIDTH = 150f, TITLE_SIZE = 31f, NAME_SIZE = 29f;

    private static final class RowState {
        String name, value, glyph;
        boolean active;
        final Animation2 anim = new Animation2();
        final Animation2 slot = new Animation2();
        RowState() { anim.set(0.0); slot.set(0.0); }
    }

    private final Map<String, RowState> rows = new LinkedHashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH, boundsH = HEADER_H;

    public ModernKeybindsElement() {
        super("Keybinds", 10f, 80f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        boolean chatOpen = MinecraftClient.getInstance().currentScreen instanceof ChatScreen;

        for (RowState st : rows.values()) st.active = false;
        if (Vesence.get != null && Vesence.get.manager != null) {
            for (Module mod : Vesence.get.manager.getModules()) {
                if (mod.bind == -1 || !mod.enable) continue;
                RowState st = rows.computeIfAbsent(mod.name, k -> new RowState());
                st.name = mod.name;
                st.value = KeyUtil.getKey(mod.bind) + (mod.bind2 != -1 ? " + " + KeyUtil.getKey(mod.bind2) : "");
                st.glyph = mod.category.getIcon();
                st.active = true;
            }
        }

        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, RowState> e : rows.entrySet()) {
            RowState st = e.getValue();
            st.anim.update();
            st.slot.update();
            st.anim.run(st.active ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
            st.slot.run(st.active ? ROW_ADVANCE : 0.0, 0.18, Easings.CUBIC_OUT, true);
            if (!st.active && st.anim.get() < 0.005 && st.slot.get() < 0.5) dead.add(e.getKey());
        }
        for (String k : dead) rows.remove(k);

        boolean shouldShow = chatOpen || !rows.isEmpty();
        visibilityAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        float alpha = (float) visibilityAnim.get();
        if (alpha < 0.005f) return;

        float titleW = r.measureText(FontRegistry.MONTSERRAT, "Keybinds", TITLE_SIZE).width;
        float headerContentW = 13f + titleW + 34f;
        float maxRowW = 0f, slotSum = 0f;
        for (RowState st : rows.values()) {
            float rw = rowWidth(r, st);
            if (st.anim.get() > 0.01f && rw > maxRowW) maxRowW = rw;
            slotSum += (float) st.slot.get();
        }
        float panelTarget = Math.max(MIN_WIDTH, Math.max(headerContentW, maxRowW));
        widthAnim.update();
        widthAnim.run(panelTarget, 0.2, Easings.CUBIC_OUT, true);
        float panelW = (float) widthAnim.get();

        boolean rightSide = (x + panelW / 2f) > screenWidth / 2f;
        int theme = Renderer2D.ColorUtil.getClientColor();

        drawHudPanel(r, x, y, panelW, HEADER_H, alpha);
        r.text(FontRegistry.MONTSERRAT, x + 13, y + 28.5f, TITLE_SIZE, "Keybinds",
                ColorUtil.replAlpha(-1, (int) (255 * alpha)), -0.15f);
        TtfFonts.LOX.drawRightString(r, "d", x + panelW - 11, y + 12, 20, ColorUtil.theme((int) (255 * alpha)));

        float curY = y + HEADER_H + HEADER_GAP;
        for (RowState st : rows.values()) {
            float rowAnim = (float) st.anim.get();
            if (rowAnim < 0.01f) continue;

            float rowW = rowWidth(r, st);
            float rowAlpha = rowAnim * alpha;
            float leftEdge = rightSide ? (x + panelW - rowW) : x;
            float rowX = leftEdge + (rightSide ? 1f : -1f) * (1f - rowAnim) * 12f;
            float rowTop = curY;

            drawHudPanel(r, rowX, rowTop, rowW, ROW_H, rowAlpha);

            float nameStart = rowX + 55f;
            r.rect(rowX + 41, rowTop + 14, 1, 18, ColorUtil.replAlpha(-1, (int) (25 * rowAlpha)));
            r.text(FontRegistry.MON, rowX + 13, rowTop + 31, 35, st.glyph,
                    ColorUtil.replAlpha(theme, (int) (255 * rowAlpha)), -0.1f);
            r.text(FontRegistry.MONTSERRAT, nameStart, rowTop + 29, NAME_SIZE, st.name,
                    ColorUtil.replAlpha(-1, (int) (255 * rowAlpha)), -0.1f);

            float nameW = r.measureText(FontRegistry.MONTSERRAT, st.name, NAME_SIZE, -0.1f).width;
            float valueW = r.measureText(FontRegistry.MONTSERRAT, st.value, NAME_SIZE).width;
            float boxX = nameStart + nameW + 25f, boxW = valueW + 15f;
            r.rect(boxX - 14, rowTop + 14, 1, 18, ColorUtil.replAlpha(-1, (int) (25 * rowAlpha)));
            r.rect(boxX, rowTop + 9, boxW, 26, 5, ColorUtil.replAlpha(theme, (int) (30 * rowAlpha)));
            r.rectOutline(boxX, rowTop + 9, boxW, 26, 6, ColorUtil.replAlpha(theme, (int) (35 * rowAlpha)), 1);
            r.textRight(FontRegistry.MONTSERRAT, boxX + boxW - 7, rowTop + 28, NAME_SIZE, st.value,
                    ColorUtil.replAlpha(theme, (int) (255 * rowAlpha)));

            curY += (float) st.slot.get();
        }

        boundsW = panelW;
        boundsH = slotSum < 0.5f ? HEADER_H : (HEADER_H + HEADER_GAP + slotSum + (ROW_H - ROW_ADVANCE));
    }

    private float rowWidth(Renderer2D r, RowState st) {
        float nameW = r.measureText(FontRegistry.MONTSERRAT, st.name, NAME_SIZE, -0.1f).width;
        float valueW = r.measureText(FontRegistry.MONTSERRAT, st.value, NAME_SIZE).width;
        return 55f + nameW + 25f + valueW + 15f + 9f;
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getHeight(Renderer2D r, FontObject font) { return boundsH; }
}
