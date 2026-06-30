package vesence.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.session.Session;
import org.lwjgl.glfw.GLFW;
import vesence.mixin.MinecraftClientAccessor;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.cfg.ConfigManager;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

@Environment(EnvType.CLIENT)
public class AccountSwitcherWindow {

    private static final float PANEL_W_FRAC = 0.40f;
    private static final float PANEL_MIN_W  = 360f;
    private static final float PAD          = 28;
    private static final float CARD_H       = 70;
    private static final float CARD_GAP     = 10;
    private static final float AVATAR       = 38;
    private static final float CORNER       = 24;
    private static final float CARD_CORNER  = 12;

    private boolean windowOpen = false;
    private final Animation2 slideAnim = new Animation2();
    private float drawAlpha = 0f;
    private float slide = 0f;

    private boolean russian = false;

    public void setRussian(boolean russian) { this.russian = russian; }

    private String t(String ru, String eng) { return russian ? ru : eng; }

    private final Animation2 emptyAnim = new Animation2();

    private final List<AccountEntry> entries = new ArrayList<>();
    private int selectedAccount = -1;

    private final StringBuilder nickInput   = new StringBuilder();
    private final StringBuilder serverInput = new StringBuilder();
    private int  focusedField = 0;
    private long cursorStart  = 0;

    private final Animation2 closeHoverAnim  = new Animation2();
    private final Animation2 createHoverAnim = new Animation2();
    private final Animation2 nickFocusAnim   = new Animation2();
    private final Animation2 serverFocusAnim = new Animation2();
    private final ScrollUtil scroll = new ScrollUtil();

    private float closeX, closeY, closeW, closeH;
    private float nickFieldX, nickFieldY, nickFieldW, nickFieldH;
    private float serverFieldX, serverFieldY, serverFieldW, serverFieldH;
    private float createX, createY, createW, createH;
    private float listX, listY, listW, listH;

    private static class AccountEntry {
        final String nick;
        final String server;
        final String addedDate;
        final Animation2 alphaAnim = new Animation2();
        final Animation2 xAnim = new Animation2();
        final Animation2 hoverAnim = new Animation2();
        final Animation2 delHoverAnim = new Animation2();
        float displayY = 0;
        boolean removing = false;

        AccountEntry(String nick, String server, String addedDate) {
            this.nick = nick;
            this.server = server;
            this.addedDate = addedDate;
        }
    }

    private static final String ACCOUNTS_FILE = "accounts.json";

    public AccountSwitcherWindow() {
        cursorStart = System.currentTimeMillis();
        loadAccounts();
    }

    public boolean isOpen() { return windowOpen; }

    public void open() {
        windowOpen = true;
        slideAnim.run(1.0, 0.45, Easings.CUBIC_OUT);
        focusedField = 0;
        cursorStart = System.currentTimeMillis();
    }

    public void close() {
        windowOpen = false;
        slideAnim.run(0.0, 0.35, Easings.CUBIC_IN);
    }

    public String getSelectedAccount() {
        if (selectedAccount >= 0 && selectedAccount < entries.size())
            return entries.get(selectedAccount).nick;
        return null;
    }

    private int serverColor(String server) {
        if (server == null) return ColorUtil.getColor(255, 255, 255, 255);
        String s = server.toLowerCase();
        if (s.contains("fun"))   return ColorUtil.getColor(255, 95, 110, 255);
        if (s.contains("spooky")) return ColorUtil.getColor(255, 120, 120, 255);
        if (s.contains("holy"))  return ColorUtil.getColor(92, 200, 255, 255);
        if (s.contains("really")) return ColorUtil.getColor(255, 171, 92, 255);
        if (s.contains("grim") || s.contains("green")) return ColorUtil.getColor(120, 230, 140, 255);
        return ColorUtil.getColor(124, 176, 255, 255);
    }

    public void render(Renderer2D r, int sw, int sh, float mx, float my) {
        slideAnim.update();
        slide = (float) slideAnim.get();
        drawAlpha = slide;
        if (drawAlpha <= 0.001f) {
            cleanupRemoved();
            return;
        }

        float panelW = Math.max(PANEL_MIN_W, sw * PANEL_W_FRAC);
        float baseX  = sw - panelW;
        float panelX = baseX + (1f - slide) * panelW;
        float panelY = 0;
        float panelH = sh;

        r.pushAlpha(drawAlpha);

        r.blurSquircle(panelX, panelY - CORNER, panelW + CORNER, panelH + CORNER * 2, 15, 7,
                BorderRadius.all(CORNER, 0, 0, CORNER), 1f);
        r.drawSquircle(panelX, panelY - CORNER, panelW + CORNER, panelH + CORNER * 2, 7,
                BorderRadius.all(CORNER, 0, 0, CORNER), ColorUtil.getColor(0, 0, 0, 145));

        float cx = panelX + PAD + 5;
        float headY = PAD + 10;
        int[] headGrad = new int[]{ColorUtil.getColor(255, 255), ColorUtil.getColor(255, 211, 251, 255)};
        r.text(FontRegistry.SF_SEMI, cx, headY + 28, 85,
                t("Все ваши аккаунты\nхранятся здесь.", "All your accounts\nare saved here."), headGrad, 0, -1.5f);

        r.textRight(FontRegistry.SF_SEMI, cx + panelW - PAD - 30, headY + 25, 62,
                t("Сейчас: ", "Current: ") + MinecraftClient.getInstance().getSession().getUsername(), ColorUtil.getColor(255,174, 247, 100), -1.5f);

        closeW = 100; closeH = 30;
        closeX = panelX + panelW - closeW - PAD;
        closeY = PAD + 8;
        boolean closeHov = mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH;
        closeHoverAnim.update();
        closeHoverAnim.run(closeHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float chT = (float) closeHoverAnim.get();
        int closeBg = ColorUtil.overCol(ColorUtil.getColor(120, 35, 45, 160), ColorUtil.getColor(170, 45, 55, 210), chT);

        float bottomBlockH = 160;
        listX = cx;
        listY = headY + 110;
        listW = panelW - PAD * 2;
        listH = sh - listY - bottomBlockH - PAD;

        cleanupRemoved();
        renderAccountList(r, mx, my);
        renderBottomBlock(r, sw, sh, mx, my, panelX, panelW, bottomBlockH);

        r.popAlpha();
    }

    private void drawAvatar(Renderer2D r, float x, float y, float size, String fallbackLetter) {
        float round = size / 2f;
        r.rect(x, y, size, size, round, ColorUtil.getColor(124, 176, 255, 55));
        r.textCenter(FontRegistry.SF_MEDIUM, x + size / 2f, y + size / 2f + 8, 26,
                fallbackLetter == null || fallbackLetter.isEmpty() ? "?" : fallbackLetter.substring(0, 1).toUpperCase(),
                ColorUtil.getColor(220, 235, 255, 255));
    }

    private void renderAccountList(Renderer2D r, float mx, float my) {
        float totalH = entries.size() * (CARD_H + CARD_GAP) + 35;
        scroll.setMax(totalH, listH);
        scroll.setEnabled(totalH > listH);
        scroll.update();
        float sOff = scroll.getScroll();

        emptyAnim.update();
        emptyAnim.run(entries.isEmpty() ? 1.0 : 0.0, 0.35, Easings.CUBIC_OUT);
        float emptyA = (float) emptyAnim.get();
        if (emptyA > 0.01f) {
            r.pushAlpha(emptyA);
            r.textCenter(FontRegistry.SF_SEMI, listX + listW / 2f, listY + listH / 2f - 65, 55,
                    ColorFormat.color(255, 255, 255) + t("Созданные аккаунты\nне найдены.", "No created accounts\nwere found."),
                    new int[]{ColorUtil.getColor(255,174, 247), ColorUtil.getColor(255,174, 247, 120)});
            r.popAlpha();
        }

        r.pushClipRect(listX, listY, listW, listH - 40);
        r.getTransformStack().pushTranslation(0f, sOff);

        for (int i = 0; i < entries.size(); i++) {
            AccountEntry e = entries.get(i);
            e.alphaAnim.update();
            e.xAnim.update();
            float targetY = listY + i * (CARD_H + CARD_GAP);
            e.displayY += (targetY - e.displayY) * 0.2f;
            float eY = e.displayY;
            float ea = Math.max(0, Math.min(1, (float) e.alphaAnim.get()));
            float xOff = (float) e.xAnim.get();
            if (ea < 0.01f) continue;

            boolean hover = mx >= listX && mx <= listX + listW && my >= eY + sOff && my <= eY + sOff + CARD_H;
            boolean sel = i == selectedAccount;
            e.hoverAnim.update();
            e.hoverAnim.run(sel ? 1.0 : hover ? 0.55 : 0.0, 0.2, Easings.CUBIC_OUT);
            float blend = (float) e.hoverAnim.get();

            r.pushAlpha(ea);
            r.getTransformStack().pushTranslation(xOff, 0);

            float avS = AVATAR;
            float avX = listX;
            float avY = eY + (CARD_H - avS) / 2f;

            float blockX = avX;
            float blockW = listX + listW - blockX;
            int cardBg = ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 12), ColorUtil.getColor(255, 255, 255, 24), blend);
            r.rect(blockX, eY, blockW, CARD_H, CARD_CORNER, cardBg);

            float txX = blockX + 16;
            float nickY = eY + 34;
            r.pushClipRect(blockX, eY, blockW - 8, CARD_H);
            r.text(FontRegistry.SF_SEMI, txX, nickY, 35, e.nick, ColorUtil.getColor(245, 245, 248, 255));
            float nickW = r.measureText(FontRegistry.SF_SEMI, e.nick, 35).width;
            if (e.server != null && !e.server.isEmpty()) {
                r.text(FontRegistry.SF_MEDIUM, txX + nickW + 8, nickY, 30, e.server.toUpperCase(), serverColor(e.server));
            }
            r.text(FontRegistry.SF_MEDIUM, txX, eY + 52, 29, e.addedDate, ColorUtil.getColor(150, 150, 165, 190));
            r.popClipRect();

            float delS = 45;
            float delX = listX + listW - delS - 13;
            float delY = eY + 14;
            boolean delHov = mx >= delX && mx <= delX + delS && my >= delY + sOff && my <= delY + sOff + delS;
            e.delHoverAnim.update();
            e.delHoverAnim.run(delHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
            float dhT = (float) e.delHoverAnim.get();
            int delBg = ColorUtil.overCol(ColorUtil.getColor(150, 45, 55, 45), ColorUtil.getColor(195, 55, 65, 220), dhT);
            int delOut = ColorUtil.overCol(ColorUtil.getColor(150, 45, 55, 65), ColorUtil.getColor(195, 55, 65, 255), dhT);
            r.rect(delX, delY, delS, delS, CARD_CORNER, delBg);
            r.rectOutline(delX, delY, delS, delS, CARD_CORNER, delOut, 1);
            r.textCenter(FontRegistry.VESENCE, delX + delS / 2f + 0.5f, delY + delS / 2f + 5, 24, "X",
                    ColorUtil.getColor(255, 225, 227, 255));

            r.popTransform();
            r.popAlpha();
        }

        r.getTransformStack().pop();
        r.popClipRect();
    }

    private void renderBottomBlock(Renderer2D r, int sw, int sh, float mx, float my,
                                   float panelX, float panelW, float blockH) {
        float bx = panelX + PAD;
        float bw = panelW - PAD * 2;

        float createH = 38;
        float createGap = 10;
        float formH = blockH - createH - createGap;
        float formY = sh - PAD - createH - createGap - formH - 15;

        r.blurSquircle(bx, formY, bw, formH, 15, 7, BorderRadius.all(CARD_CORNER), 1f);
        r.rect(bx, formY, bw, formH, CARD_CORNER, ColorUtil.getColor(255, 255, 255, 14));

        r.text(FontRegistry.SF_MEDIUM, bx + 16, formY + 33, 35, t("О вашем аккаунте...", "About your account..."), ColorUtil.getColor(255, 255, 255, 255));

        float lineX = bx + 16;
        float lineW = bw - 32;
        float nickLineY = formY + 38 + 9;
        float serverLineY = formY + 38 + 26 + 9;
        nickFieldX = lineX; nickFieldY = nickLineY; nickFieldW = lineW; nickFieldH = 26;
        serverFieldX = lineX; serverFieldY = serverLineY; serverFieldW = lineW; serverFieldH = 26;

        nickFocusAnim.update();
        nickFocusAnim.run(focusedField == 1 ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        serverFocusAnim.update();
        serverFocusAnim.run(focusedField == 2 ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);

        drawFieldText(r, lineX, nickLineY + 19, nickInput.toString(), t("Введите ник...", "Type nick here."), focusedField == 1);
        drawFieldText(r, lineX, serverLineY + 19, serverInput.toString(), t("Введите сервер...", "Type server here."), focusedField == 2);

        createW = bw;
        createX = bx;
        createY = sh - PAD - createH - 5;

        this.createH = createH + 10;
        boolean createHov = mx >= createX && mx <= createX + createW && my >= createY && my <= createY + this.createH;
        createHoverAnim.update();
        createHoverAnim.run(createHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float crT = (float) createHoverAnim.get();
        r.blurSquircle(createX, createY, createW, createH + 10, 15, 7, BorderRadius.all(CARD_CORNER), 1f);
        r.rect(createX, createY, createW, createH + 10, CARD_CORNER,
                ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 14), ColorUtil.getColor(255,174, 247, 100), crT));
        r.text(FontRegistry.SF_MEDIUM, createX + 16, createY + createH / 2f + 12, 33, t("Создать новый профиль аккаунта..", "Create a new account profile.."),
                ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 125), ColorUtil.getColor(255, 255, 255, 255), crT));
        float ckS = 18;
        float ckX = createX + createW - ckS - 11;
        float ckY = createY + (createH - ckS) / 2f;
        r.textCenter(FontRegistry.VESENCE, ckX + ckS / 2f - 3, ckY + ckS / 2f + 10, 31, "Y", ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 125), ColorUtil.getColor(255, 255, 255, 255), crT));
    }

    private void drawFieldText(Renderer2D r, float x, float baselineY, String text, String placeholder, boolean focused) {
        boolean blink = ((System.currentTimeMillis() - cursorStart) / 500) % 2 == 0;
        if (text.isEmpty() && !focused) {
            r.text(FontRegistry.SF_MEDIUM, x, baselineY, 30, placeholder, ColorUtil.getColor(255, 255, 255, 125));
        } else {
            String shown = focused && blink ? text + "|" : text;
            r.text(FontRegistry.SF_MEDIUM, x, baselineY, 30, shown, ColorUtil.getColor(255, 255, 255, 125));
        }
    }

    public boolean click(float mx, float my, int sw, int sh) {
        if (!windowOpen) return false;
        if (drawAlpha < 0.9f) return true;

        if (mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH) {
            close();
            return true;
        }
        if (mx >= nickFieldX && mx <= nickFieldX + nickFieldW && my >= nickFieldY && my <= nickFieldY + nickFieldH) {
            focusedField = 1; cursorStart = System.currentTimeMillis(); return true;
        }
        if (mx >= serverFieldX && mx <= serverFieldX + serverFieldW && my >= serverFieldY && my <= serverFieldY + serverFieldH) {
            focusedField = 2; cursorStart = System.currentTimeMillis(); return true;
        }
        if (mx >= createX && mx <= createX + createW && my >= createY && my <= createY + createH) {
            addAccount(); return true;
        }

        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            float sOff = scroll.getScroll();
            for (int i = 0; i < entries.size(); i++) {
                AccountEntry e = entries.get(i);
                if (e.removing) continue;
                float eY = e.displayY + sOff;

                float delS = 45;
                float delX = listX + listW - delS - 13;
                float delY = eY + 14;
                if (mx >= delX && mx <= delX + delS && my >= delY && my <= delY + delS) {
                    removeEntry(i);
                    return true;
                }
                if (my >= eY && my <= eY + CARD_H) {
                    selectedAccount = i; saveAccounts(); applySession(); return true;
                }
            }
            return true;
        }
        focusedField = 0;
        return true;
    }

    public boolean rightClick(float mx, float my, int sw, int sh) {
        if (!windowOpen) return false;
        if (drawAlpha < 0.9f) return true;
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            float sOff = scroll.getScroll();
            for (int i = 0; i < entries.size(); i++) {
                AccountEntry e = entries.get(i);
                if (e.removing) continue;
                float eY = e.displayY + sOff;
                if (my >= eY && my <= eY + CARD_H) {
                    removeEntry(i);
                    return true;
                }
            }
        }
        return true;
    }

    private void removeEntry(int idx) {
        if (idx < 0 || idx >= entries.size()) return;
        AccountEntry e = entries.get(idx);
        e.removing = true;
        e.alphaAnim.run(0.0, 0.25, Easings.CUBIC_OUT);
        e.xAnim.run(40.0, 0.25, Easings.CUBIC_OUT);
        if (selectedAccount == idx) selectedAccount = -1;
        else if (selectedAccount > idx) selectedAccount--;
        scroll.reset();
        saveAccounts();
    }

    public void release() {}

    public boolean keyPressed(int key) {
        if (!windowOpen || focusedField == 0) return false;
        StringBuilder buf = focusedField == 1 ? nickInput : serverInput;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (focusedField == 1 && !nickInput.toString().trim().isEmpty()) {
                focusedField = 2; cursorStart = System.currentTimeMillis();
            } else { addAccount(); }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            focusedField = focusedField == 1 ? 2 : 1; cursorStart = System.currentTimeMillis(); return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && buf.length() > 0) {
            buf.deleteCharAt(buf.length() - 1); cursorStart = System.currentTimeMillis(); return true;
        }
        return false;
    }

    public boolean charTyped(CharInput input) {
        if (!windowOpen || focusedField == 0) return false;
        StringBuilder buf = focusedField == 1 ? nickInput : serverInput;
        int maxLen = focusedField == 1 ? 24 : 40;
        int cp = input.codepoint();
        if (cp >= 32 && cp < 127 && buf.length() < maxLen) {
            buf.append((char) cp); cursorStart = System.currentTimeMillis(); return true;
        }
        return false;
    }

    public boolean shouldCloseOnEsc() {
        if (windowOpen) { close(); return false; }
        return true;
    }

    public void scroll(double delta) {
        if (!windowOpen) return;
        scroll.handleScroll(delta * 2.5);
    }

    private void addAccount() {
        String nick = nickInput.toString().trim();
        if (nick.isEmpty()) return;
        boolean exists = false;
        for (AccountEntry e : entries) {
            if (e.nick.equals(nick) && !e.removing) { exists = true; break; }
        }
        if (!exists) {
            AccountEntry entry = new AccountEntry(nick, serverInput.toString().trim(),
                    new SimpleDateFormat("dd.MM.yy - HH:mm").format(new Date()));
            entry.alphaAnim.set(0.0);
            entry.alphaAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
            entry.xAnim.set(40.0);
            entry.xAnim.run(0.0, 0.3, Easings.CUBIC_OUT);
            entry.displayY = entries.size() * (CARD_H + CARD_GAP);
            entries.add(entry);
            selectedAccount = entries.size() - 1;
            saveAccounts();
            applySession();
        }
        nickInput.setLength(0);
        serverInput.setLength(0);
        focusedField = 1;
        cursorStart = System.currentTimeMillis();
    }

    private void saveAccounts() {
        try {
            JsonArray arr = new JsonArray();
            for (AccountEntry entry : entries) {
                if (entry.removing) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("nick", entry.nick);
                obj.addProperty("purpose", entry.server);
                obj.addProperty("date", entry.addedDate);
                arr.add(obj);
            }
            JsonObject root = new JsonObject();
            root.add("accounts", arr);
            root.addProperty("selected", selectedAccount);
            File f = new File(ConfigManager.configDirectory, ACCOUNTS_FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
        } catch (Exception ignored) {}
    }

    private void loadAccounts() {
        try {
            File f = new File(ConfigManager.configDirectory, ACCOUNTS_FILE);
            if (!f.exists()) return;
            try (FileReader reader = new FileReader(f)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("accounts")) {
                    for (JsonElement el : root.getAsJsonArray("accounts")) {
                        String nick = "", server = "", date = "";
                        if (el.isJsonObject()) {
                            JsonObject o = el.getAsJsonObject();
                            nick   = o.has("nick")    ? o.get("nick").getAsString()    : "";
                            server = o.has("purpose") ? o.get("purpose").getAsString() : "";
                            date   = o.has("date")    ? o.get("date").getAsString()    : "";
                        } else {
                            nick = el.getAsString();
                        }
                        AccountEntry entry = new AccountEntry(nick, server, date);
                        entry.alphaAnim.set(1.0);
                        entry.xAnim.set(0.0);
                        entry.displayY = entries.size() * (CARD_H + CARD_GAP);
                        entries.add(entry);
                    }
                }
                if (root.has("selected")) {
                    selectedAccount = root.get("selected").getAsInt();
                    applySession();
                }
            }
        } catch (Exception ignored) {}
    }

    private void cleanupRemoved() {
        entries.removeIf(e -> e.removing && (float) e.alphaAnim.get() < 0.01f);
    }

    private void applySession() {
        String nick = getSelectedAccount();
        if (nick == null || nick.isEmpty()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Session session = new Session(nick, UUID.randomUUID(), "", java.util.Optional.empty(), java.util.Optional.empty());
            ((MinecraftClientAccessor) mc).setSession(session);
        } catch (Exception ignored) {}
    }
}
