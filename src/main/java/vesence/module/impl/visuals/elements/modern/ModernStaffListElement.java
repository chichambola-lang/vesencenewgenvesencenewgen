package vesence.module.impl.visuals.elements.modern;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.RichTextUtil;
import vesence.utils.staff.StaffStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class ModernStaffListElement extends HudElement {

    private static final float HEADER_H = 43f, ROW_H = 43f, ROW_ADVANCE = 50f, HEADER_GAP = 8f;
    private static final float MIN_WIDTH = 150f, TITLE_SIZE = 31f, NAME_SIZE = 29f, ICON_SIZE = 20;

    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{3,16}$");
    private static final Pattern PREFIX_MATCHES = Pattern.compile(
            ".*(mod|der|adm|help|wne|\u0445\u0435\u043B\u043F|\u0430\u0434\u043C|\u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0430|\u043A\u0443\u0440\u0430|own|taf|curat|dev|supp|yt|\u0441\u043E\u0442\u0440\u0443\u0434).*");

    private static final int ONLINE_COLOR = ColorUtil.getColor(134, 255, 174);
    private static final int SPEC_COLOR = ColorUtil.getColor(255, 190, 80);
    private static final int VANISH_COLOR = ColorUtil.getColor(254, 68, 68);

    private enum State { ONLINE, SPECTATOR, VANISH }

    private record StaffInfo(String nick, String coloredName, State state) {}

    private static final class RowState {
        String nick, name, value;
        int accent;
        float dim;
        boolean active;
        final Animation2 anim = new Animation2();
        final Animation2 slot = new Animation2();
        RowState() { anim.set(0.0); slot.set(0.0); }
    }

    private final Map<String, RowState> rows = new LinkedHashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH, boundsH = HEADER_H;

    public ModernStaffListElement() {
        super("Staff List", 10f, 400f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        boolean chatOpen = MinecraftClient.getInstance().currentScreen instanceof ChatScreen;

        for (RowState st : rows.values()) st.active = false;
        for (StaffInfo info : collectStaff()) {
            RowState st = rows.computeIfAbsent(info.nick(), k -> new RowState());
            st.nick = info.nick();
            st.name = info.coloredName();
            st.value = stateText(info.state());
            st.accent = stateColor(info.state());
            st.dim = info.state() == State.VANISH ? 0.9f : 1f;
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

        float titleW = r.measureText(FontRegistry.MONTSERRAT, "Staff", TITLE_SIZE).width;
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

        drawHudPanel(r, x, y, panelW, HEADER_H, alpha);
        r.text(FontRegistry.MONTSERRAT, x + 13, y + 28.5f, TITLE_SIZE, "Staff",
                ColorUtil.replAlpha(-1, (int) (255 * alpha)), -0.15f);
        r.textRight(FontRegistry.VESENCE, x + panelW - 10, y + 30, 32, "C", ColorUtil.theme((int) (255 * alpha)));

        float curY = y + HEADER_H + HEADER_GAP;
        for (RowState st : rows.values()) {
            float rowAnim = (float) st.anim.get();
            if (rowAnim < 0.01f) continue;

            float rowW = rowWidth(r, st);
            float rowAlpha = rowAnim * alpha * st.dim;
            float leftEdge = rightSide ? (x + panelW - rowW) : x;
            float rowX = leftEdge + (rightSide ? 1f : -1f) * (1f - rowAnim) * 12f;
            float rowTop = curY;

            drawHudPanel(r, rowX, rowTop, rowW, ROW_H, rowAlpha);

            float nameStart = rowX + 50f;
            r.rect(rowX + 41, rowTop + 14, 1, 18, ColorUtil.replAlpha(-1, (int) (25 * rowAlpha)));
            drawHead(r, st.nick, rowX + 11, rowTop + (ROW_H - ICON_SIZE) / 2f, ICON_SIZE, rowAlpha);

            r.pushAlpha(rowAlpha);
            r.text(FontRegistry.MONTSERRAT, nameStart, rowTop + 29, NAME_SIZE, st.name,
                    ColorUtil.replAlpha(-1, 255), -0.1f);
            r.popAlpha();

            float nameW = r.measureText(FontRegistry.MONTSERRAT, ColorFormat.strip(st.name), NAME_SIZE, -0.1f).width;
            float valueW = r.measureText(FontRegistry.MONTSERRAT, st.value, NAME_SIZE).width;
            float boxX = nameStart + nameW + 25f, boxW = valueW + 15f;
            r.rect(boxX - 14, rowTop + 14, 1, 18, ColorUtil.replAlpha(-1, (int) (25 * rowAlpha)));
            r.rect(boxX, rowTop + 9, boxW, 26, 5, ColorUtil.replAlpha(st.accent, (int) (30 * rowAlpha)));
            r.rectOutline(boxX, rowTop + 9, boxW, 26, 6, ColorUtil.replAlpha(st.accent, (int) (35 * rowAlpha)), 1);
            r.textRight(FontRegistry.MONTSERRAT, boxX + boxW - 7, rowTop + 28, NAME_SIZE, st.value,
                    ColorUtil.replAlpha(st.accent, (int) (255 * rowAlpha)));

            curY += (float) st.slot.get();
        }

        boundsW = panelW;
        boundsH = slotSum < 0.5f ? HEADER_H : (HEADER_H + HEADER_GAP + slotSum + (ROW_H - ROW_ADVANCE));
    }

    private float rowWidth(Renderer2D r, RowState st) {
        float nameW = r.measureText(FontRegistry.MONTSERRAT, ColorFormat.strip(st.name), NAME_SIZE, -0.1f).width;
        float valueW = r.measureText(FontRegistry.MONTSERRAT, st.value, NAME_SIZE).width;
        return 55f + nameW + 25f + valueW + 15f + 9f;
    }

    private void drawHead(Renderer2D r, String name, float px, float py, float size, float alpha) {
        float round = size * 0.22f;
        int glId = skinGlId(name);
        r.pushAlpha(alpha);
        if (glId != 0) {
            r.drawRgbaTextureWithUVRoundedNearest(glId, px, py, size, size,
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, round);
            r.drawRgbaTextureWithUVRoundedNearest(glId, px, py, size, size,
                    40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, round);
        } else {
            r.rect(px, py, size, size, round, ColorUtil.getColor(255, 40));
        }
        r.popAlpha();
    }

    private int skinGlId(String name) {
        try {
            ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
            if (nh == null) return 0;
            PlayerListEntry entry = nh.getPlayerListEntry(name);
            if (entry == null) return 0;
            SkinTextures tex = entry.getSkinTextures();
            if (tex == null) return 0;
            Identifier skinId = tex.body().texturePath();
            AbstractTexture at = MinecraftClient.getInstance().getTextureManager().getTexture(skinId);
            if (at == null) return 0;
            GpuTextureView view = at.getGlTextureView();
            if (view == null) return 0;
            GpuTexture gpuTex = view.texture();
            if (gpuTex instanceof GlTexture glTex) return glTex.getGlId();
        } catch (Exception ignored) {}
        return 0;
    }

    private static String cleanName(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            if (!isSymbol(cp)) sb.appendCodePoint(cp);
            i += n;
        }
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    private static boolean isSymbol(int cp) {
        if (cp >= 0xE000 && cp <= 0xF8FF) return true;
        if (cp >= 0x2190 && cp <= 0x2BFF) return true;
        if (cp >= 0x1F000) return true;
        return false;
    }

    private List<StaffInfo> collectStaff() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<StaffInfo> result = new ArrayList<>();
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (mc.world == null || mc.player == null || nh == null) return result;

        String self = mc.player.getName().getString();
        Scoreboard sb = mc.world.getScoreboard();

        List<Team> teams = new ArrayList<>(sb.getTeams());
        teams.sort(Comparator.comparing(Team::getName));

        LinkedHashMap<String, StaffInfo> byName = new LinkedHashMap<>();
        for (Team team : teams) {
            String prefixStripped = team.getPrefix().getString().trim();
            String lowerPrefix = prefixStripped.toLowerCase(Locale.ROOT);
            boolean staffPrefix = PREFIX_MATCHES.matcher(lowerPrefix).matches();
            String prefixColored = RichTextUtil.toColorFormat(team.getPrefix(), 0xFFFFFF, 255);

            for (String name : team.getPlayerList()) {
                if (!NAME_PATTERN.matcher(name).matches() || name.equals(self)) continue;

                PlayerListEntry ple = nh.getPlayerListEntry(name);
                String namePart = ColorFormat.color(255, 255, 255) + name;
                String colored = cleanName(prefixColored.isEmpty() ? namePart : prefixColored + " " + namePart);
                if (ple != null) {
                    if (!(staffPrefix || StaffStorage.isStaff(name))) continue;
                    State state = ple.getGameMode() == GameMode.SPECTATOR ? State.SPECTATOR : State.ONLINE;
                    byName.put(name, new StaffInfo(name, colored, state));
                } else if (!prefixStripped.isEmpty()) {
                    byName.put(name, new StaffInfo(name, colored, State.VANISH));
                }
            }
        }

        result.addAll(byName.values());
        result.sort(Comparator.comparingInt(s -> s.state().ordinal()));
        return result;
    }

    private String stateText(State state) {
        return switch (state) {
            case ONLINE -> "Online";
            case SPECTATOR -> "Spec";
            case VANISH -> "Vanish";
        };
    }

    private int stateColor(State state) {
        return switch (state) {
            case ONLINE -> ONLINE_COLOR;
            case SPECTATOR -> SPEC_COLOR;
            case VANISH -> VANISH_COLOR;
        };
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getHeight(Renderer2D r, FontObject font) { return boundsH; }
}
