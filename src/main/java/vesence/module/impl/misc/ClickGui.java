package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.TitleSetting;
import vesence.utils.render.utils.SoundUtil;

@IModule(name = "ClickGui", description = "Открывает ClickGui", category = Category.MISC, bind = GLFW.GLFW_KEY_RIGHT_SHIFT)
@Environment(EnvType.CLIENT)
public class ClickGui extends Module {

    public static final TitleSetting basic = new TitleSetting("Interface");
    public static final BooleanSetting blurHud = new BooleanSetting("Размытие", true);
    public static final SliderSetting blurStrengthHud = new SliderSetting("Сила размытия", 25, 1, 45, 1).hidden(() -> !blurHud.get());
    public static final SliderSetting cornerHud = new SliderSetting("Скругление углов", 10, 1, 45, 1);
    public static final SliderSetting hudAlpha = new SliderSetting("Прозрачность", 0.75, 0.0, 1.0, 0.05);
    public static final ModeSetting hudVariation = new ModeSetting("Вариация интерфейса", "Default", "Default", "Modern");
    public static final BooleanSetting squircleHud = new BooleanSetting("Сквиркл скругление", false);
    public static final SliderSetting squircleCornerHud = new SliderSetting("Сила влияния от сквиркл", 6, 4, 12, 1).hidden(() -> !squircleHud.get());

    public static final TitleSetting gui = new TitleSetting("Click GUI");
    public static final BooleanSetting blurGui = new BooleanSetting("Размытие", true);
    public static final SliderSetting blurStrengthGui = new SliderSetting("Сила размытия", 25, 1, 45, 1).hidden(() -> !blurGui.get());
    public static final SliderSetting cornerGui = new SliderSetting("Скругление углов", 10, 0, 45, 1);
    public static final SliderSetting guiAlpha = new SliderSetting("Прозрачность", 0.75, 0.0, 1.0, 0.05);
    public static final BooleanSetting squircleGui = new BooleanSetting("Сквиркл скругление", false);
    public static final SliderSetting squircleCornerGui = new SliderSetting("Сила влияния от сквиркл", 6, 4, 12, 1).hidden(() -> !squircleGui.get());
    public static final ModeSetting sort = new ModeSetting("Сортировка модулей", "По алфавиту", "По алфавиту", "По длине");
    public static final SliderSetting guiScale = new SliderSetting("Масштаб GUI", 1.0, 0.5, 2.0, 0.05);

    private static final double HUD_CORNER_LIMIT = 15.0;
    private static final double GUI_CORNER_LIMIT = 15.0;

    public static boolean hudCornerOverflow = false;
    public static boolean guiCornerOverflow = false;
    private static long lastHudOverflowSound = 0;
    private static long lastGuiOverflowSound = 0;

    public ClickGui() {
        this.hiddenFromGui = true;
        this.addSettings(
            blurHud, blurStrengthHud, cornerHud, hudAlpha, hudVariation, squircleHud, squircleCornerHud,
            blurGui, blurStrengthGui, cornerGui, guiAlpha, squircleGui, squircleCornerGui, sort, guiScale
        );
    }

    public static void tickLimits() {
        hudCornerOverflow = false;
        if (!squircleHud.get() && cornerHud.current > HUD_CORNER_LIMIT) {
            hudCornerOverflow = true;
            long now = System.currentTimeMillis();
            if (now - lastHudOverflowSound > 300) {
                SoundUtil.playUi("unknowncommand", 0.5F, 200L);
                lastHudOverflowSound = now;
            }
            cornerHud.current = HUD_CORNER_LIMIT;
        }

        guiCornerOverflow = false;
        if (!squircleGui.get() && cornerGui.current > GUI_CORNER_LIMIT) {
            guiCornerOverflow = true;
            long now = System.currentTimeMillis();
            if (now - lastGuiOverflowSound > 300) {
                SoundUtil.playUi("unknowncommand", 0.5F, 200L);
                lastGuiOverflowSound = now;
            }
            cornerGui.current = GUI_CORNER_LIMIT;
        }
    }

    public static float getHudCorner() {
        return (float) cornerHud.current;
    }

    public static float getHudSquirt() {
        return squircleHud.get() ? squircleCornerHud.get().floatValue() : 0f;
    }

    public static boolean isHudSquircle() {
        return squircleHud.get();
    }

    public static float getHudAlpha() {
        return hudAlpha.get().floatValue();
    }

    public static float getGuiCorner() {
        return (float) cornerGui.current;
    }

    public static float getGuiSquirt() {
        return squircleGui.get() ? squircleCornerGui.get().floatValue() : 0f;
    }

    public static boolean isGuiSquircle() {
        return squircleGui.get();
    }

    public static boolean isModern() {
        return hudVariation.is("Modern");
    }
}
