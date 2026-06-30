package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;

@IModule(name = "Crosshair", description = "Заменяет обычный курсор на кастомный", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Crosshair extends Module {

    private final ModeSetting mode = new ModeSetting("Вид", "Орбиз", "Орбиз", "Классический", "Кругляшок");
    private final BooleanSetting staticCrosshair = new BooleanSetting("Статический", false);

    private float animationSize = 0.0f;
    private float circleSwingAnim = 0.0f;

    public Crosshair() {
        addSettings(mode, staticCrosshair);
    }

    @EventInit
    public void onRender2D(EventScreen event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;

        float x = event.viewportWidth() / 2.0f;
        float y = event.viewportHeight() / 2.0f;
        Renderer2D r = event.renderer();

        if (mode.is("Орбиз")) {
            float cooldown = 1.0f - mc.player.getAttackCooldownProgress(0.0f);
            animationSize = MathHelper.lerp(0.1f, animationSize, cooldown * 3.0f);
            float radius = 3.0f + (staticCrosshair.get() ? 0.0f : animationSize);

            r.circle(x, y, radius + 1.0f, 0.0f, 1.0f, 0xFF000000);
            r.circle(x, y, radius, 0.0f, 1.0f, -1);
        } else if (mode.is("Классический") || mode.is("Класический")) {
            float cooldown = 1.0f - mc.player.getAttackCooldownProgress(0.0f);
            float thickness = 1.0f;
            float length = 3.0f;
            float gap = 2.0f + (staticCrosshair.get() ? 0.0f : 8.0f * cooldown);

            int color = mc.targetedEntity != null ? 0xFFFF0000 : -1;

            drawOutlined(r, x - thickness / 2.0f, y - gap - length, thickness, length, color);
            drawOutlined(r, x - thickness / 2.0f, y + gap, thickness, length, color);
            drawOutlined(r, x - gap - length, y - thickness / 2.0f, length, thickness, color);
            drawOutlined(r, x + gap, y - thickness / 2.0f, length, thickness, color);
        } else if (mode.is("Кругляшок")) {
            float swingProgress = mc.player.handSwingProgress;
            float sin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
            circleSwingAnim = MathHelper.lerp(0.05f, circleSwingAnim, sin);

            float radius = 4.0f + circleSwingAnim * 4.0f;
            float pct = 1.0f - circleSwingAnim;
            float thickness = 2.0f;

            int bgColor = ColorUtil.getColor(30, 30, 30, 102);

            r.circleOutline(x, y, radius, 0.0f, 1.0f, bgColor, thickness);

            float angleLimit = 360.0f * pct;
            for (float i = 0; i < angleLimit; i += 3) {
                int color = ColorUtil.fade((int) i);
                float stepPct = Math.min(4.0f, angleLimit - i) / 360.0f;
                r.circleOutline(x, y, radius, 180.0f + i, stepPct, color, thickness);
            }
        }
    }

    private void drawOutlined(Renderer2D r, float rx, float ry, float w, float h, int color) {
        float outline = 0.5f;
        r.rect(rx - outline, ry - outline, w + outline * 2.0f, h + outline * 2.0f, 0xFF000000);
        r.rect(rx, ry, w, h, color);
    }
}
