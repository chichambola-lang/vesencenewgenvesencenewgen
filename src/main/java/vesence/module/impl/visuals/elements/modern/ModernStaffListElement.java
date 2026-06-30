package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.staff.StaffStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ModernStaffListElement extends HudElement {

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

    private static final int ONLINE_COLOR = ColorUtil.getColor(134, 255, 174);
    private static final int OFFLINE_COLOR = ColorUtil.getColor(255, 109, 109);

    private static final String[] STAFF_KEYWORDS = {
            "moder", "admin", "helper", "owner", "co-owner", "manager", "senior",
            "moderator", "administrator", "support", "develop", "builder",
            "curator", "trial", "head", "lead", "staff", "youtuber", "youtube",
            "partner", "sponsor", "mvp", "vip", "premium", "legend", "hero",
            "guard", "operator", "supervisor", "consultant"
    };

    private final Map<String, Animation2> playerAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 heightAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    private final Map<String, StaffInfo> knownStaff = new HashMap<>();

    private record StaffInfo(String nick, String privilege, boolean online) {}

    public ModernStaffListElement() {
        super("Staff List", 10f, 400f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private boolean isStaff(String displayText) {
        if (displayText == null) return false;
        String lower = displayText.toLowerCase();
        for (String kw : STAFF_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String extractPrivilege(String displayText) {
        if (displayText == null) return "";
        String lower = displayText.toLowerCase();
        for (String kw : STAFF_KEYWORDS) {
            int idx = lower.indexOf(kw);
            if (idx != -1) {
                int start = idx;
                while (start > 0 && displayText.charAt(start - 1) != ' ' && displayText.charAt(start - 1) != '[' && displayText.charAt(start - 1) != '&') {
                    start--;
                }
                int end = idx + kw.length();
                while (end < displayText.length() && displayText.charAt(end) != ' ' && displayText.charAt(end) != ']' && displayText.charAt(end) != '\u00a7') {
                    end++;
                }
                String privilege = displayText.substring(start, end);
                privilege = privilege.replaceAll("[\\[\\]&§]", "").trim();
                if (!privilege.isEmpty()) {
                    return privilege.substring(0, 1).toUpperCase() + privilege.substring(1).toLowerCase();
                }
            }
        }
        return "";
    }

    private List<StaffInfo> collectStaff() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return new ArrayList<>(knownStaff.values());

        Set<String> current = new HashSet<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String nick = entry.getProfile().name();
            Text displayName = entry.getDisplayName();
            String displayText = displayName != null ? displayName.getString() : nick;

            if (!isStaff(displayText) && !isStaff(nick) && !StaffStorage.isStaff(nick)) continue;

            current.add(nick);
            String privilege = extractPrivilege(displayText);
            if (privilege.isEmpty()) privilege = extractPrivilege(nick);
            if (privilege.isEmpty()) privilege = "Staff";

            knownStaff.put(nick, new StaffInfo(nick, privilege, true));
        }

        for (Map.Entry<String, StaffInfo> e : new HashMap<>(knownStaff).entrySet()) {
            if (!current.contains(e.getKey())) {
                StaffInfo old = e.getValue();
                knownStaff.put(e.getKey(), new StaffInfo(old.nick(), old.privilege(), false));
            }
        }

        List<StaffInfo> result = new ArrayList<>(knownStaff.values());
        result.sort((a, b) -> Boolean.compare(b.online(), a.online()));
        return result;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        List<StaffInfo> staff = collectStaff();

        float totalSlotHeight = 0f;
        for (StaffInfo entry : staff) {
            String key = entry.nick();
            Animation2 anim = playerAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(entry.online() ? 1.0 : 0.35, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
            totalSlotHeight += slot.get();
        }
        for (Map.Entry<String, Animation2> e : playerAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = staff.stream().anyMatch(s -> s.nick().equals(key));
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

        boolean hasAny = !staff.isEmpty();
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

        String headerText = "Staff";

        float maxLineW = 0f;
        for (StaffInfo entry : staff) {
            String label = entry.nick() + " [" + entry.privilege() + "]";
            float nameW = renderer.measureText(font, label, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + 12 + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, headerText, HEADER_FONT_SIZE).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        drawHudPanel(renderer, x, y, rectW, rectH, globalAlpha);

        int theme = Renderer2D.ColorUtil.getClientColor1();
        renderer.rect(x, y + 40, rectW, 1.25f, ColorUtil.replAlpha(-1, (int) (12 * heightAnim.get())));

        float curY = y + PADDING_V + 12;
        renderer.text(FontRegistry.VESENCE, x + 11, curY + 3.5f, 42, "C", ColorUtil.replAlpha(theme, globalAlpha));
        renderer.text(FontRegistry.SF_MEDIUM, x + 39, curY + 3, 30.5f, headerText, ColorUtil.getColor(255, globalAlpha));

        curY += HEADER_HEIGHT;

        renderer.pushClipRect(x, y, rectW, rectH);
        for (StaffInfo entry : staff) {
            String key = entry.nick();
            Animation2 anim = playerAnims.get(key);
            Animation2 slot = slotAnims.get(key);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String label = entry.nick() + " [" + entry.privilege() + "]";

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int circleColorBase = entry.online() ? ONLINE_COLOR : OFFLINE_COLOR;
            int circleColor = ColorUtil.replAlpha(circleColorBase, modTextAlpha);

            renderer.text(FontRegistry.SF_MEDIUM, x + 11 + modAlpha * 15 - 15, curY + 22, 29.5f, label, nameColor, -0.1f);

            float circleX = x + rectW - 24;
            float circleY = curY + 10;
            renderer.rect(circleX, circleY, 12, 12, 6, circleColor);

            curY += (float) slot.get();
        }
        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = staff.stream().anyMatch(s -> s.nick().equals(key));
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
        List<StaffInfo> staff = collectStaff();
        float maxLineW = 0f;
        for (StaffInfo entry : staff) {
            String label = entry.nick() + " [" + entry.privilege() + "]";
            float nameW = renderer.measureText(FontRegistry.SF_MEDIUM, label, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + 12 + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Staff", HEADER_FONT_SIZE).width;
        float headerW = headerTextW + 55f;
        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        return contentW + PADDING_H * 2f + 15;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        List<StaffInfo> staff = collectStaff();
        float totalSlotHeight = 0;
        boolean hasAny = false;
        for (StaffInfo entry : staff) {
            Animation2 slot = slotAnims.get(entry.nick());
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
