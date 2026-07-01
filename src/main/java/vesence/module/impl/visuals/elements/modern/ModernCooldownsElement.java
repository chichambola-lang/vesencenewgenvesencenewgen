package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import vesence.mixin.ItemCooldownManagerAccessor;
import vesence.mixin.ItemCooldownManagerEntryAccessor;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ModernCooldownsElement extends HudElement {

    private static final float HEADER_H = 43f, ROW_H = 43f, ROW_ADVANCE = 50f, HEADER_GAP = 8f;
    private static final float MIN_WIDTH = 150f, TITLE_SIZE = 31f, NAME_SIZE = 29f, ICON_SIZE = 24f;

    private static final class RowState {
        String name, value;
        ItemStack stack;
        boolean active;
        final Animation2 anim = new Animation2();
        final Animation2 slot = new Animation2();
        RowState() { anim.set(0.0); slot.set(0.0); }
    }

    private record CooldownEntry(String key, String displayName, ItemStack stack, float progress, int remainingTicks) {}

    private final Map<String, RowState> rows = new LinkedHashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH, boundsH = HEADER_H;

    public ModernCooldownsElement() {
        super("Cooldowns", 10f, 320f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        for (RowState st : rows.values()) st.active = false;
        for (CooldownEntry entry : collectCooldowns(mc)) {
            RowState st = rows.computeIfAbsent(entry.key(), k -> new RowState());
            st.name = entry.displayName();
            st.value = formatCooldown(entry.progress(), entry.remainingTicks());
            st.stack = entry.stack();
            st.active = true;
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

        float titleW = r.measureText(FontRegistry.MONTSERRAT, "Cooldowns", TITLE_SIZE).width;
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
        r.text(FontRegistry.MONTSERRAT, x + 13, y + 28.5f, TITLE_SIZE, "Cooldowns",
                ColorUtil.replAlpha(-1, (int) (255 * alpha)), -0.15f);
        r.textRight(FontRegistry.VESENCE, x + panelW - 10, y + 30, 32, "D", ColorUtil.theme((int) (255 * alpha)));

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
            drawItemIcon(ctx, st.stack, rowX + 12, rowTop + (ROW_H - ICON_SIZE) / 2f, ICON_SIZE, rowAlpha);

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

    private void drawItemIcon(DrawContext ctx, ItemStack stack, float px, float py, float size, float alpha) {
        if (stack == null || stack.isEmpty() || alpha <= 0.01f) return;
        float elemScale = getScale();
        float guiScale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
        float sx = this.x + (px - this.x) * elemScale;
        float sy = this.y + (py - this.y) * elemScale;
        float drawSize = size * elemScale;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        float cx = (sx + drawSize / 2f) / guiScale;
        float cy = (sy + drawSize / 2f) / guiScale;
        m.translate(cx, cy);
        float sc = (drawSize / guiScale) / 16f * alpha;
        m.scale(sc, sc);
        m.translate(-8f, -8f);
        ctx.drawItem(stack, 0, 0);
        m.popMatrix();
    }

    private List<CooldownEntry> collectCooldowns(MinecraftClient mc) {
        if (mc.player == null) return List.of();
        List<CooldownEntry> result = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();

        ItemCooldownManager cdm = mc.player.getItemCooldownManager();

        Map<Identifier, ?> entriesMap = null;
        int currentTick = 0;
        try {
            ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) cdm;
            entriesMap = accessor.getEntries();
            currentTick = accessor.getTick();
        } catch (ClassCastException ignored) {}

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (seen.contains(item)) continue;

            float progress = cdm.getCooldownProgress(stack, 0.0f);
            if (progress <= 0f) continue;

            seen.add(item);
            int remainingTicks = entriesMap != null ? getRemainingTicks(stack, entriesMap, currentTick) : 0;
            result.add(new CooldownEntry(item.toString(), stack.getName().getString(), stack.copy(), progress, remainingTicks));
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty()) {
            Item item = offHand.getItem();
            if (!seen.contains(item)) {
                float progress = cdm.getCooldownProgress(offHand, 0.0f);
                if (progress > 0f) {
                    int remainingTicks = entriesMap != null ? getRemainingTicks(offHand, entriesMap, currentTick) : 0;
                    result.add(new CooldownEntry(item.toString(), offHand.getName().getString(), offHand.copy(), progress, remainingTicks));
                }
            }
        }
        return result;
    }

    private int getRemainingTicks(ItemStack stack, Map<Identifier, ?> entries, int currentTick) {
        try {
            Identifier itemId = stack.getItem().getRegistryEntry().getKey().map(k -> k.getValue()).orElse(null);
            if (itemId == null) return 0;
            Object entry = entries.get(itemId);
            if (entry instanceof ItemCooldownManagerEntryAccessor entryAcc) {
                return entryAcc.invokeEndTick() - currentTick;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatCooldown(float progress, int remainingTicks) {
        if (remainingTicks > 0) {
            int totalSec = remainingTicks / 20;
            int min = totalSec / 60;
            int sec = totalSec % 60;
            if (min > 0) return min + "\u043C " + String.format("%02d", sec) + "\u0441";
            return sec + "\u0441";
        }
        return String.format("%.0f%%", progress * 100);
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getHeight(Renderer2D r, FontObject font) { return boundsH; }
}
