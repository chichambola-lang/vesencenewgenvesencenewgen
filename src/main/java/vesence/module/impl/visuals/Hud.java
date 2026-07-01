package vesence.module.impl.visuals;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.*;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.elements.*;
import vesence.module.impl.visuals.elements.modern.*;
import vesence.utils.notifications.Notifications;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;

import java.util.ArrayList;
import java.util.List;

@IModule(name = "Hud", description = "Выводит разную информацию на экран", category = Category.VISUALS, bind = 0)
public class Hud extends Module {

    public final MultiBooleanSetting elements = new MultiBooleanSetting("Элементы",
            new BooleanSetting("Watermark", true),
            new BooleanSetting("Informations", true),
            new BooleanSetting("Keybinds", true),
            new BooleanSetting("Target Hud", true),
            new BooleanSetting("Potions List", true),
            new BooleanSetting("Cooldowns", true),
            new BooleanSetting("Staff List", true),
            new BooleanSetting("Hud Binds Assist", true),
            new BooleanSetting("Notifications", true)
    );

    private final BooleanSetting mentionAlert = Notifications.mentionAlert;
    public static final BooleanSetting contour = new BooleanSetting("Контур", false);

    private final List<HudElement> defaultElements = new ArrayList<>();
    private final List<HudElement> modernElements = new ArrayList<>();
    private boolean prevLeftDown  = false;

    private static final float GRID_SIZE = 10f;
    private final Animation2 gridAnim = new Animation2();

    private boolean switchInit = false;
    private boolean lastModern = false;
    private long switchStart = 0L;
    private static final float SWITCH_DURATION = 0.4f;
    private static final float SWITCH_STAGGER = 0.05f;

    public Hud() {
        this.hiddenFromGui = true;

        defaultElements.add(new WatermarkElement());
        defaultElements.add(new CoordsElement());
        defaultElements.add(new KeybindsElement());
        defaultElements.add(new TargetHudElement());
        defaultElements.add(new PotionsElement());
        defaultElements.add(new CooldownsElement());
        defaultElements.add(new StaffListElement());
        defaultElements.add(new AssistElement());
        defaultElements.add(new NotificationsElement());

        modernElements.add(new ModernWatermarkElement());
        modernElements.add(new ModernCoordsElement());
        modernElements.add(new ModernKeybindsElement());
        modernElements.add(new ModernTargetHudElement());
        modernElements.add(new ModernPotionsElement());
        modernElements.add(new ModernCooldownsElement());
        modernElements.add(new ModernStaffListElement());
        modernElements.add(new ModernAssistElement());
        modernElements.add(new ModernNotificationsElement());

        for (HudElement modern : modernElements) {
            for (HudElement def : defaultElements) {
                if (def.name.equals(modern.name)) {
                    modern.scaleSetting = def.scaleSetting;
                    break;
                }
            }
        }

        this.addSettings(elements);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!this.enable || !mentionAlert.get() || mc.player == null) return;
            String text = message.getString();
            String myName = mc.player.getName().getString();
            if (text.contains(myName)) {
                Notifications.addMention();
            }
        });
    }

    public List<HudElement> getHudElements() {
        return ClickGui.isModern() ? modernElements : defaultElements;
    }

    public List<HudElement> getAllHudElements() {
        List<HudElement> all = new ArrayList<>(defaultElements);
        all.addAll(modernElements);
        return all;
    }

    public void syncInactivePositions() {
        List<HudElement> active = getHudElements();
        List<HudElement> inactive = ClickGui.isModern() ? defaultElements : modernElements;
        for (HudElement a : active) {
            for (HudElement b : inactive) {
                if (b.name.equals(a.name)) {
                    b.x = a.x;
                    b.y = a.y;
                    b.targetX = a.targetX;
                    b.targetY = a.targetY;
                    b.targetInitialized = a.targetInitialized;
                    break;
                }
            }
        }
    }

    public boolean isElementEnabled(String name) {
        return elements.get(name);
    }

    public void setElementEnabled(String name, boolean enabled) {
        for (BooleanSetting bs : elements.settings) {
            if (bs.name.equals(name)) {
                bs.set(enabled);
                return;
            }
        }
    }

    public List<String> getElementNames() {
        List<String> names = new ArrayList<>();
        for (BooleanSetting bs : elements.settings) {
            names.add(bs.name);
        }
        return names;
    }

    @EventInit
    public void onRender(EventScreen event) {
        if (!this.enable) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean isGuiOpen  = mc.currentScreen != null;
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        double mouseX = 0, mouseY = 0;
        boolean leftDown  = false;

        if (isGuiOpen) {
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), mx, my);
            leftDown  = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)  == GLFW.GLFW_PRESS;

            double ratioX = (double) event.viewportWidth()  / mc.getWindow().getWidth();
            double ratioY = (double) event.viewportHeight() / mc.getWindow().getHeight();
            mouseX = mx[0] * ratioX;
            mouseY = my[0] * ratioY;
        }

        Renderer2D renderer = event.renderer();
        FontObject  font    = event.defaultFont();

        boolean modernNow = ClickGui.isModern();
        if (!switchInit) {
            switchInit = true;
            lastModern = modernNow;
        } else if (modernNow != lastModern) {
            lastModern = modernNow;
            switchStart = System.currentTimeMillis();
        }
        float switchElapsed = (System.currentTimeMillis() - switchStart) / 1000f;

        boolean anyDragging = false;
        if (isChatOpen) {
            for (HudElement element : getHudElements()) {
                if (element.isDragging()) { anyDragging = true; break; }
            }
        }
        gridAnim.update();
        gridAnim.run(anyDragging ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float gridT = (float) gridAnim.get();
        if (gridT > 0.005f) {
            renderGrid(renderer, event.viewportWidth(), event.viewportHeight(), gridT);
        }

        boolean leftJustPressed  = leftDown  && !prevLeftDown;

        boolean anyElementDragging = false;
        for (HudElement element : getHudElements()) {
            if (element.isDragging()) { anyElementDragging = true; break; }
        }

        if (isChatOpen && elements.get("Notifications")) {
            Notifications.showExample();
        } else {
            Notifications.hideExample();
        }

        if (isChatOpen && leftDown && leftJustPressed && !anyElementDragging) {
            List<HudElement> list = getHudElements();
            for (int i = list.size() - 1; i >= 0; i--) {
                HudElement el = list.get(i);
                if (!elements.get(el.name)) continue;
                if (el.scaleSetting.sliding || el.isDragging()) continue;
                if (el.isHovered(mouseX, mouseY, renderer, font)) {
                    el.onMousePress(mouseX, mouseY, renderer, font);
                    if (el.isDragging()) {
                        anyElementDragging = true;
                        list.remove(i);
                        list.add(el);
                    }
                    break;
                }
            }
        }

        List<HudElement> renderList = getHudElements();
        for (int idx = 0; idx < renderList.size(); idx++) {
            HudElement element = renderList.get(idx);
            boolean enabled = elements.get(element.name);

            element.alphaAnim.update();
            element.alphaAnim.run(enabled ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            float elemAlpha = (float) element.alphaAnim.get();
            if (elemAlpha < 0.005f) continue;

            float switchProg = Math.max(0f, Math.min(1f, (switchElapsed - idx * SWITCH_STAGGER) / SWITCH_DURATION));
            float switchFade = 1f - (float) Math.pow(1f - switchProg, 3f);
            float switchScale = 0.55f + 0.45f * easeOutBack(switchProg);
            float switchSlide = (1f - switchFade) * 26f;
            float switchTilt = (1f - switchFade) * ((idx & 1) == 0 ? 7f : -7f);
            element.y += switchSlide;

            float scale;
            float rawScale = element.scaleSetting.get().floatValue();
            if (element.scaleSetting.sliding) {
                element.scaleAnim.set(rawScale);
                scale = rawScale;
            } else {
                element.scaleAnim.update();
                element.scaleAnim.run(rawScale, 0.25, Easings.SINE_OUT, true);
                scale = (float) element.scaleAnim.get();
            }
            renderer.pushScale(scale, element.x, element.y);
            renderer.pushAlpha(elemAlpha * switchFade);

            element.updateInteraction(mouseX, mouseY, renderer, font, element.isDragging());
            float expand = element.getExpand() * switchScale;
            boolean transforming = Math.abs(expand - 1f) > 0.0005f || Math.abs(switchTilt) > 0.02f;

            float cw = element.getWidth(renderer, font);
            float ch = element.getHeight(renderer, font);
            float centerXfb = element.x + cw / 2f;
            float centerYfb = element.y + ch / 2f;

            float guiScale = (float) vesence.utils.other.Mathf.getScaleFactor();
            org.joml.Matrix3x2fStack ctxMatrices = event.drawContext().getMatrices();
            if (transforming) {
                renderer.pushScale(expand, expand, centerXfb, centerYfb);
                renderer.pushRotationAround(switchTilt, centerXfb, centerYfb);

                ctxMatrices.pushMatrix();
                float cgx = centerXfb / guiScale;
                float cgy = centerYfb / guiScale;
                ctxMatrices.translate(cgx, cgy);
                ctxMatrices.rotate((float) Math.toRadians(switchTilt));
                ctxMatrices.scale(expand, expand);
                ctxMatrices.translate(-cgx, -cgy);
            }
            element.render(renderer, font, event.viewportWidth(), event.viewportHeight(), event.drawContext());

            element.renderHoverOutline(renderer, font);
            if (transforming) {
                ctxMatrices.popMatrix();
                renderer.popRotation();
                renderer.popScale();
            }
            renderer.popAlpha();
            renderer.popScale();
            element.y -= switchSlide;

            if (isChatOpen) {
                if (!leftDown) {
                    element.onMouseRelease();
                }
                element.onMouseMove(mouseX, mouseY, renderer, font,
                        event.viewportWidth(), event.viewportHeight());
            } else {
                if (element.isDragging()) element.onMouseRelease();
                element.onMouseMove(mouseX, mouseY, renderer, font,
                        event.viewportWidth(), event.viewportHeight());
            }
        }

        if (isGuiOpen) {
            prevLeftDown  = leftDown;
        }

        syncInactivePositions();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private void renderGrid(Renderer2D renderer, int screenW, int screenH, float animT) {
        int lineColor = ColorUtil.getColor(255, 255, 255, (int) (18 * animT));
        float thickness = 0.5f;

        for (float gx = 0; gx <= screenW; gx += GRID_SIZE) {
            renderer.rect(gx, 0, thickness, screenH, lineColor);
        }

        for (float gy = 0; gy <= screenH; gy += GRID_SIZE) {
            renderer.rect(0, gy, screenW, thickness, lineColor);
        }
    }

    public boolean onKeyTyped(char c) {
        return false;
    }
}
