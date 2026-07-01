package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vesence.Vesence;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.impl.misc.Assist;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;
import org.lwjgl.glfw.GLFW;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class ModernAssistElement extends HudElement {

    private static final float PANEL_SIZE = 80;
    private static final float PANEL_GAP = 8;
    private static final float PANEL_PADDING = 4;
    private static final float FONT_SIZE = 30;
    private static final int WHITE_COLOR = 0xFFFFFFFF;

    public ModernAssistElement() {
        super("Hud Binds Assist", 10f, 40f);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Assist assist = Vesence.get.manager.get(Assist.class);
        if (assist == null || !assist.enable) return;

        List<BoundItem> boundItems = getBoundItemsFromAssist(mc, assist);
        if (boundItems.isEmpty()) return;

        float curX = x;
        float elemScale = getScale();

        for (BoundItem item : boundItems) {
            float pw = panelWidth(renderer, item);
            drawHudPanel(renderer, curX, y, pw, 40, 1f);

            float guiScale = (float) vesence.utils.other.Mathf.getScaleFactor();

            float panelCenterX = curX + 20;
            float panelCenterY = y + 21;
            float scaledCenterX = this.x + (panelCenterX - this.x) * elemScale;
            float scaledCenterY = this.y + (panelCenterY - this.y) * elemScale;
            float centerX = scaledCenterX / guiScale;
            float centerY = scaledCenterY / guiScale;
            float itemScale = 0.8f * elemScale;

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(centerX, centerY);
            ctx.getMatrices().scale(itemScale, itemScale);
            ctx.drawItem(item.stack, -8, -8);
            ctx.getMatrices().popMatrix();

            String countStr = String.valueOf(item.totalCount);
            float countTextH = renderer.measureText(FontRegistry.MONTSERRAT, countStr, FONT_SIZE).height;
            float countX = curX + 6;
            float countY = y + 6 + countTextH * 0.7f;
            renderer.text(FontRegistry.MONTSERRAT, countX, countY, FONT_SIZE, countStr, WHITE_COLOR);

            float bindW = renderer.measureText(FontRegistry.MONTSERRAT, item.bindKey, FONT_SIZE).width;
            float bindTextH = renderer.measureText(FontRegistry.MONTSERRAT, item.bindKey, FONT_SIZE).height;
            float bindX = curX + pw - bindW - 6;
            float bindY = y + PANEL_SIZE - 12 - bindTextH * 0.3f;
            renderer.text(FontRegistry.MONTSERRAT, bindX, bindY, FONT_SIZE, item.bindKey, WHITE_COLOR);

            curX += pw + PANEL_GAP;
        }
    }

    private float panelWidth(Renderer2D renderer, BoundItem item) {
        float countW = renderer.measureText(FontRegistry.MONTSERRAT, String.valueOf(item.totalCount), FONT_SIZE).width;
        float bindW = renderer.measureText(FontRegistry.MONTSERRAT, item.bindKey, FONT_SIZE).width;
        float leftW = Math.max(34f, 6f + countW);
        return leftW + 8f + bindW + 8f;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return PANEL_SIZE;

        Assist assist = Vesence.get.manager.get(Assist.class);
        if (assist == null || !assist.enable) return PANEL_SIZE;

        List<BoundItem> boundItems = getBoundItemsFromAssist(mc, assist);
        if (boundItems.isEmpty()) return PANEL_SIZE;

        float total = 0f;
        for (BoundItem item : boundItems) {
            total += panelWidth(renderer, item) + PANEL_GAP;
        }
        return Math.max(0f, total - PANEL_GAP);
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return 40;
    }

    private List<BoundItem> getBoundItemsFromAssist(MinecraftClient mc, Assist assist) {
        List<BoundItem> items = new ArrayList<>();

        if (assist.mode.is("FunTime")) {
            addBoundItem(items, mc, assist.windChargeBind, Items.WIND_CHARGE, null);
            addBoundItem(items, mc, assist.ftDisorientationBind, Items.ENDER_EYE, "дезориентация");
            addBoundItem(items, mc, assist.ftTrapBind, Items.NETHERITE_SCRAP, "трапка");
            addBoundItem(items, mc, assist.ftDustBind, Items.SUGAR, "явная");
            addBoundItem(items, mc, assist.ftCrossbowBind, Items.CROSSBOW, null);
            addBoundItem(items, mc, assist.ftPlastBind, Items.DRIED_KELP, "пласт");
            addBoundItem(items, mc, assist.ftAuraBind, Items.PHANTOM_MEMBRANE, "божья");
            addBoundItem(items, mc, assist.ftSnowballBind, Items.SNOWBALL, "снежок");
            addBoundItem(items, mc, assist.ftSmerchBind, Items.FIRE_CHARGE, "смерч");
        } else if (assist.mode.is("HolyWorld")) {
            addBoundItem(items, mc, assist.windChargeBind, Items.WIND_CHARGE, null);
            addBoundItem(items, mc, assist.hwExplosiveTrapBind, Items.PRISMARINE_SHARD, null);
            addBoundItem(items, mc, assist.hwStanBind, Items.NETHER_STAR, null);
            addBoundItem(items, mc, assist.hwExplosiveThingBind, Items.FIRE_CHARGE, null);
            addBoundItem(items, mc, assist.hwTrapBind, Items.POPPED_CHORUS_FRUIT, null);
            addBoundItem(items, mc, assist.hwSnowballBind, Items.SNOWBALL, null);
        }

        return items;
    }

    private void addBoundItem(List<BoundItem> items, MinecraftClient mc, BindSettings bind, Item item, String nameSubstring) {
        if (bind.key == -1) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) return;

        int slot = findItemSlot(mc, nameSubstring, item);
        if (slot == -1) return;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) return;

        int totalCount = countItemsInInventory(mc, nameSubstring, item);
        if (totalCount == 0) return;

        String keyName = getKeyName(bind.key);
        String bindKey = isMouseButtonName(keyName) ? keyName : keyName + " + \u041C";

        items.add(new BoundItem(stack, bindKey, totalCount));
    }

    private boolean isMouseButtonName(String name) {
        return name.equals("LMB") || name.equals("RMB") || name.equals("MMB");
    }

    private int findItemSlot(MinecraftClient mc, String nameSubstring, Item fallbackItem) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    return i;
                }
            }

            if (fallbackItem != null && stack.isOf(fallbackItem)) {
                return i;
            }
        }
        return -1;
    }

    private int countItemsInInventory(MinecraftClient mc, String nameSubstring, Item fallbackItem) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            boolean matches = false;
            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    matches = true;
                }
            }

            if (!matches && fallbackItem != null && stack.isOf(fallbackItem)) {
                matches = true;
            }

            if (matches) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
    }

    private String decodeMojibake(String value) {
        try {
            return new String(value.getBytes(java.nio.charset.Charset.forName("windows-1251")), java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private String getKeyName(int keyCode) {
        if (keyCode == -1) return "";
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null) {
            return name.toUpperCase();
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE: return "SPC";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "ALT";
            case GLFW.GLFW_MOUSE_BUTTON_1: return "LMB";
            case GLFW.GLFW_MOUSE_BUTTON_2: return "RMB";
            case GLFW.GLFW_MOUSE_BUTTON_3: return "MMB";
            default: return "KEY" + keyCode;
        }
    }

    private static class BoundItem {
        final ItemStack stack;
        final String bindKey;
        final int totalCount;

        BoundItem(ItemStack stack, String bindKey, int totalCount) {
            this.stack = stack;
            this.bindKey = bindKey;
            this.totalCount = totalCount;
        }
    }
}
