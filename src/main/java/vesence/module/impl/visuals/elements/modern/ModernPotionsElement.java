package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffectInstance;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ModernPotionsElement extends HudElement {

    private static final float PADDING_H = 13;
    private static final float PADDING_V = 12;
    private static final float FONT_SIZE = 28;
    private static final float HEADER_FONT_SIZE = 27;
    private static final float LINE_HEIGHT = 20;
    private static final float HEADER_HEIGHT = 17;
    private static final float ICON_GAP = 6;
    private static final float ROW_RIGHT_EXTRA_GAP = 5f;
    private static final float EXTRA_HEIGHT = 14;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private final Map<String, Animation2> effectAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 heightAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    public ModernPotionsElement() {
        super("Potions List", 10f, 200f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private String effectName(StatusEffectInstance inst) {
        return inst.getEffectType().value().getName().getString();
    }

    private String formatDuration(StatusEffectInstance inst) {
        if (inst.isInfinite()) return "\u221E";
        int totalSec = inst.getDuration() / 20;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        if (min > 0) return min + "\u043C " + String.format("%02d", sec) + "\u0441";
        return sec + "\u0441";
    }

    private String amplifier(StatusEffectInstance inst) {
        int amp = inst.getAmplifier();
        if (amp <= 0) return "";
        return switch (amp) {
            case 1  -> " 2";
            case 2  -> " 3";
            case 3  -> " 4";
            case 4  -> " 5";
            default -> " " + (amp + 1);
        };
    }

    private List<StatusEffectInstance> sortedEffects(MinecraftClient mc) {
        if (mc.player == null) return List.of();
        Collection<StatusEffectInstance> raw = mc.player.getStatusEffects();
        List<StatusEffectInstance> beneficial = new ArrayList<>();
        List<StatusEffectInstance> neutral = new ArrayList<>();
        List<StatusEffectInstance> harmful = new ArrayList<>();
        for (StatusEffectInstance e : raw) {
            switch (e.getEffectType().value().getCategory()) {
                case BENEFICIAL -> beneficial.add(e);
                case HARMFUL    -> harmful.add(e);
                default         -> neutral.add(e);
            }
        }
        List<StatusEffectInstance> result = new ArrayList<>();
        result.addAll(beneficial);
        result.addAll(neutral);
        result.addAll(harmful);
        return result;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        List<StatusEffectInstance> effects = sortedEffects(mc);

        float totalSlotHeight = 0f;
        for (StatusEffectInstance inst : effects) {
            String key = effectName(inst);
            Animation2 anim = effectAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
            totalSlotHeight += slot.get();
        }

        for (Map.Entry<String, Animation2> entry : effectAnims.entrySet()) {
            String key = entry.getKey();
            boolean stillActive = effects.stream().anyMatch(e -> effectName(e).equals(key));
            if (!stillActive) {
                entry.getValue().update();
                entry.getValue().run(0.0, 0.15, Easings.CUBIC_OUT);
                Animation2 slot = slotAnims.get(key);
                if (slot != null) {
                    slot.update();
                    slot.run(0.0, 0.15, Easings.CUBIC_OUT);
                    totalSlotHeight += slot.get();
                }
            }
        }

        boolean hasAny = !effects.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;
        float extraH = hasAny ? EXTRA_HEIGHT : 0f;
        float rectH = HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f + extraH;

        visibilityAnim.update();
        heightAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        heightAnim.run(HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f > 45 ? 1 : 0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        for (StatusEffectInstance inst : effects) {
            String label = effectName(inst) + amplifier(inst);
            String dur = formatDuration(inst);
            float nameW = renderer.measureText(font, label, FONT_SIZE).width;
            float durW = renderer.measureText(font, dur, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + durW + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Potions", HEADER_FONT_SIZE).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        drawHudPanel(renderer, x, y, rectW, rectH, globalAlpha);

        int theme = Renderer2D.ColorUtil.getClientColor1();
        renderer.rect(x, y + 40, rectW, 1.25f, ColorUtil.replAlpha(-1, (int) (12 * heightAnim.get())));

        float curY = y + PADDING_V + 12;
        renderer.text(FontRegistry.VESENCE, x + 11, curY + 3.5f, 42, "B", ColorUtil.replAlpha(theme, globalAlpha));
        renderer.text(FontRegistry.SF_MEDIUM, x + 39, curY + 3, 30.5f, "Potions", ColorUtil.getColor(255, globalAlpha));

        curY += HEADER_HEIGHT;

        renderer.pushClipRect(x, y, rectW, rectH);
        for (StatusEffectInstance inst : effects) {
            String key = effectName(inst);
            Animation2 anim = effectAnims.get(key);
            Animation2 slot = slotAnims.get(key);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String durStr = formatDuration(inst);

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int durColor = (modTextAlpha << 24) | (themeColor & 0x00FFFFFF);

            renderer.text(FontRegistry.SF_MEDIUM, x + 11 + modAlpha * 15 - 15, curY + 22, 29.5f, effectName(inst) + ColorFormat.color(255,139,139) + amplifier(inst) + ColorFormat.reset(), nameColor, -0.1f);

            float bindX = x + rectW - 12;
            vesence.utils.render.text.AnimatedText.draw(renderer, FontRegistry.SF_MEDIUM,
                  "mpot_" + effectName(inst), durStr, bindX - modAlpha * 15 + 15, curY + 22, 29.5f,
                  durColor, vesence.utils.render.text.AnimatedText.ALIGN_RIGHT);

            curY += (float) slot.get();
        }
        for (Map.Entry<String, Animation2> entry : slotAnims.entrySet()) {
            String key = entry.getKey();
            boolean stillActive = effects.stream().anyMatch(e -> effectName(e).equals(key));
            if (!stillActive) {
                curY += (float) entry.getValue().get();
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
        MinecraftClient mc = MinecraftClient.getInstance();
        List<StatusEffectInstance> effects = sortedEffects(mc);
        float maxLineW = 0f;
        for (StatusEffectInstance inst : effects) {
            String label = effectName(inst) + amplifier(inst);
            String dur = formatDuration(inst);
            float nameW = renderer.measureText(font, label, FONT_SIZE).width;
            float durW = renderer.measureText(font, dur, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + durW + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Potions", HEADER_FONT_SIZE).width;
        float headerW = headerTextW + 55f;
        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        return contentW + PADDING_H * 2f + 15;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<StatusEffectInstance> effects = sortedEffects(mc);
        float totalSlotHeight = 0;
        boolean hasAny = false;
        for (StatusEffectInstance inst : effects) {
            String key = effectName(inst);
            Animation2 slot = slotAnims.get(key);
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
