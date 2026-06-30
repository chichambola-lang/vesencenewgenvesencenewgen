package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import vesence.mixin.ItemCooldownManagerAccessor;
import vesence.mixin.ItemCooldownManagerEntryAccessor;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class CooldownsElement extends HudElement {

    private static final float PADDING_H        = 13f;
    private static final float PADDING_V        = 12f;
    private static final float FONT_SIZE        = 28f;
    private static final float HEADER_FONT_SIZE = 27f;
    private static final float LINE_HEIGHT      = 20f;
    private static final float HEADER_HEIGHT    = 17f;
    private static final float ICON_GAP         = 6f;
    private static final float ROW_RIGHT_EXTRA_GAP = 5f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final float EXTRA_HEIGHT = 14f;

    private final Map<String, Animation2> itemAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 heightAnim     = new Animation2();
    private final Animation2 widthAnim      = new Animation2();

    public CooldownsElement() {
        super("Cooldowns", 10f, 320f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private record CooldownEntry(String key, String displayName, ItemStack stack, float progress, int remainingTicks) {}

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

            int remainingTicks = 0;
            if (entriesMap != null) {
                remainingTicks = getRemainingTicks(stack, entriesMap, currentTick);
            }

            result.add(new CooldownEntry(item.toString(), stack.getName().getString(), stack.copy(), progress, remainingTicks));
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty()) {
            Item item = offHand.getItem();
            if (!seen.contains(item)) {
                float progress = cdm.getCooldownProgress(offHand, 0.0f);
                if (progress > 0f) {
                    int remainingTicks = 0;
                    if (entriesMap != null) {
                        remainingTicks = getRemainingTicks(offHand, entriesMap, currentTick);
                    }
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
            if (min > 0) return min + "м " + String.format("%02d", sec) + "с";
            return sec + "с";
        }
        return String.format("%.0f%%", progress * 100);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        List<CooldownEntry> entries = collectCooldowns(mc);

        float totalSlotHeight = 0f;
        for (CooldownEntry entry : entries) {
            Animation2 anim = itemAnims.computeIfAbsent(entry.key(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(entry.key(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
            totalSlotHeight += slot.get();
        }

        for (Map.Entry<String, Animation2> e : itemAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = entries.stream().anyMatch(en -> en.key().equals(key));
            if (!stillActive) {
                e.getValue().update();
                e.getValue().run(0.0, 0.15, Easings.CUBIC_OUT);
                Animation2 slot = slotAnims.get(key);
                if (slot != null) {
                    slot.update();
                    slot.run(0.0, 0.15, Easings.CUBIC_OUT);
                    totalSlotHeight += slot.get();
                }
            }
        }

        boolean hasAny     = !entries.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;
        float extraH = hasAny ? EXTRA_HEIGHT : 0f;
        float rectH  = HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f + extraH;

        visibilityAnim.update();
        heightAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        heightAnim.run(HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f > 45 ? 1 : 0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        for (CooldownEntry entry : entries) {
            String name = entry.displayName();
            String dur  = formatCooldown(entry.progress(), entry.remainingTicks());
            float nameW = renderer.measureText(font, name, FONT_SIZE).width;
            float durW  = renderer.measureText(font, dur, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + durW + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Cooldowns", HEADER_FONT_SIZE).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        drawHudPanel(renderer, x, y, rectW, rectH, globalAlpha);

        int theme = Renderer2D.ColorUtil.getClientColor1();
        renderer.rect(x, y + 40, rectW, 1.25f, ColorUtil.replAlpha(-1, (int) (12 * heightAnim.get())));

        float curY = y + PADDING_V + 12;
        renderer.text(FontRegistry.VESENCE, x + 11, curY + 3.5f, 42, "D", ColorUtil.replAlpha(theme, globalAlpha));
        renderer.text(FontRegistry.SF_MEDIUM, x + 39, curY + 3, 30.5f, "Cooldowns", ColorUtil.getColor(255, globalAlpha));

        curY += HEADER_HEIGHT;

        renderer.pushClipRect(x, y, rectW, rectH);
        for (CooldownEntry entry : entries) {
            Animation2 anim = itemAnims.get(entry.key());
            Animation2 slot = slotAnims.get(entry.key());
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String modName = entry.displayName();
            String bindStr = formatCooldown(entry.progress(), entry.remainingTicks());

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int bindColor = (modTextAlpha << 24) | (themeColor & 0x00FFFFFF);

            renderer.text(FontRegistry.SF_MEDIUM, x + 11 + modAlpha * 15 - 15, curY + 22, 29.5f, modName, nameColor, -0.1f);

            float bindX = x + rectW - 12;
            vesence.utils.render.text.AnimatedText.draw(renderer, FontRegistry.SF_MEDIUM,
                  "cd_" + entry.key(), bindStr, bindX - modAlpha * 15 + 15, curY + 22, 29.5f,
                  bindColor, vesence.utils.render.text.AnimatedText.ALIGN_RIGHT);

            curY += (float) slot.get();
        }

        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = entries.stream().anyMatch(en -> en.key().equals(key));
            if (!stillActive) {
                curY += (float) e.getValue().get();
            }
        }
        renderer.popClipRect();
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        float maxLineW = 0f;
        MinecraftClient mc = MinecraftClient.getInstance();
        List<CooldownEntry> entries = collectCooldowns(mc);
        for (CooldownEntry entry : entries) {
            String name = entry.displayName();
            String dur  = formatCooldown(entry.progress(), entry.remainingTicks());
            float nameW = renderer.measureText(FontRegistry.SF_MEDIUM, name, FONT_SIZE).width;
            float durW  = renderer.measureText(FontRegistry.SF_MEDIUM, dur, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + durW + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Cooldowns", HEADER_FONT_SIZE).width;
        float headerW = headerTextW + 55f;
        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        return contentW + PADDING_H * 2f + 15;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<CooldownEntry> entries = collectCooldowns(mc);
        float totalSlotHeight = 0;
        boolean hasAny = false;
        for (CooldownEntry entry : entries) {
            Animation2 slot = slotAnims.get(entry.key());
            if (slot != null) {
                float sh = (float) slot.get();
                totalSlotHeight += sh;
                if (sh > 0.5f) hasAny = true;
            } else {
                totalSlotHeight += LINE_HEIGHT;
                hasAny = true;
            }
        }
        if (!hasAny && totalSlotHeight < 0.5f) return HEADER_HEIGHT + PADDING_V * 2f;
        float extraH = hasAny ? EXTRA_HEIGHT : 0f;
        return HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f + extraH;
    }
}
