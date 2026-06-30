package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import vesence.event.EventInit;
import vesence.event.player.AttackEvent;
import vesence.event.player.EventMotion;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.HueSetting;
import net.minecraft.util.math.MathHelper;
import vesence.module.impl.visuals.world.CubesEngine;
import vesence.module.impl.visuals.world.LineGlyphsEngine;
import vesence.renderengine.render.Renderer2D;

import java.util.ArrayList;
import java.util.List;

@IModule(name = "Custom World", description = "Изменяет время, туман, кубы и линии вокруг мира", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Ambience extends Module {

    public static final String FOG_DO_NOTHING = "Ничего не делать";
    public static final String FOG_CLEAR = "Очистить";
    public static final String FOG_OVERRIDE = "Переопределить";

    public static ModeSetting mode = new ModeSetting("Время игры", "День", "День", "Ночь", "Вечер", "Закат", "Рассвет", "Кастом");
    public static SliderSetting customTime = new SliderSetting("Ваше время", 6000, 0, 24000, 100, false)
            .hidden(() -> !mode.is("Кастом"));

    public static ModeSetting fog = new ModeSetting("Туман", FOG_DO_NOTHING, FOG_DO_NOTHING, FOG_CLEAR, FOG_OVERRIDE);
    public static SliderSetting fogStart = (SliderSetting) new SliderSetting("Начало тумана", 0.25F, 0.1F, 1.5F, 0.05F, false).hidden(() -> !fog.is(FOG_OVERRIDE));
    public static SliderSetting fogEnd = (SliderSetting) new SliderSetting("Конец тумана", 1.0F, 0.1F, 1.5F, 0.05F, false).hidden(() -> !fog.is(FOG_OVERRIDE));
    public static BooleanSetting skyShader = new BooleanSetting("Шейдер для неба", false);
    public static ModeSetting skyShaderMode = new ModeSetting("Тип шейдера неба", "Стандарт", "Стандарт", "Закат")
            .hidden(() -> !skyShader.get());
    public static BooleanSetting changeSaturation = new BooleanSetting("Изменить насыщенность игры", false);
    public static SliderSetting saturationLevel = (SliderSetting) new SliderSetting("Уровень насыщенности", 1.0F, 0.0F, 2.0F, 0.05F, false).hidden(() -> !changeSaturation.get());

    private final BooleanSetting cubesEnabled = new BooleanSetting("Кубы", false);
    private final BooleanSetting glyphsEnabled = new BooleanSetting("Линии", false);
    private final CubesEngine cubes = new CubesEngine();
    private final LineGlyphsEngine glyphs = new LineGlyphsEngine();

    private static Ambience instance;

    public Ambience() {
        this.addSettings(this.buildSettings());
        instance = this;
    }

    private Setting[] buildSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(mode);
        list.add(customTime);
        list.add(fog);
        list.add(fogStart);
        list.add(fogEnd);
        list.add(skyShader);
        list.add(skyShaderMode);
        list.add(changeSaturation);
        list.add(saturationLevel);

        list.add(this.cubesEnabled);
        for (Setting s : this.cubes.settings()) {
            list.add(this.gate(s, this.cubesEnabled));
        }
        list.add(this.glyphsEnabled);
        for (Setting s : this.glyphs.settings()) {
            list.add(this.gate(s, this.glyphsEnabled));
        }
        return list.toArray(new Setting[0]);
    }

    private Setting gate(Setting setting, BooleanSetting toggle) {
        java.util.function.Supplier<Boolean> previous = setting.hidden;
        setting.hidden = () -> !toggle.get() || (previous != null && previous.get());
        return setting;
    }

    public static long getVisualTime(long realTime) {
        if (instance == null || !instance.enable) return realTime;

        if (mode.is("День")) return 6000;
        if (mode.is("Ночь")) return 18000;
        if (mode.is("Вечер")) return 13000;
        if (mode.is("Закат")) return 12000;
        if (mode.is("Рассвет")) return 23000;
        if (mode.is("Кастом")) return (long) customTime.current;

        return realTime;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static Ambience getInstance() {
        return instance;
    }

    public static float getSaturation() {
        if (instance == null || !instance.enable || !changeSaturation.get()) return 1.0F;
        return saturationLevel.get().floatValue();
    }

    public boolean shouldOverrideFog() {
        return this.enable && fog.is(FOG_OVERRIDE);
    }

    public int getFogColorValue() {
        return Renderer2D.ColorUtil.getClientColor();
    }

    @vesence.event.EventInit
    public void onWorldRender(vesence.event.render.WorldRenderEvent event) {
        if (!this.enable || !skyShader.get()) return;

        net.minecraft.client.util.math.MatrixStack stack = new net.minecraft.client.util.math.MatrixStack();
        Renderer2D renderer = vesence.Vesence.getRenderer();
        if (renderer != null) {
            int shaderIndex = 0;
            if (skyShaderMode.is("Шторм")) shaderIndex = 1;
            else if (skyShaderMode.is("Закат")) shaderIndex = 2;
            renderer.drawSkyShader(stack, event.worldRenderer().camera(), shaderIndex);
        }
    }

    @Override
    public void onDisable() {
        this.cubes.clear();
        this.glyphs.clear();
        super.onDisable();
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (this.glyphsEnabled.get()) {
            this.glyphs.onMotion();
        } else {
            this.glyphs.clear();
        }
    }

    @EventInit
    public void onAttack(AttackEvent event) {
        if (this.cubesEnabled.get() && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            this.cubes.onAttack(mc.gameRenderer.getCamera());
        }
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        MatrixStack matrices = event.getMatrixStack();
        Camera camera = mc.gameRenderer.getCamera();

        if (this.cubesEnabled.get()) {
            this.cubes.onRender3D(matrices, camera);
        } else {
            this.cubes.clear();
        }

        if (this.glyphsEnabled.get()) {
            this.glyphs.onRender3D(matrices, event.getTickDelta());
        }
    }
}
