package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffects;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.utils.player.MoveUtil;

@IModule(
        name = "AutoSprint",
        description = "Автоматически включает спринт",
        category = Category.MOVEMENT,
        bind = -1
)
@Environment(EnvType.CLIENT)
public final class AutoSprint extends Module {
    public static int tickStop = 0;

    public static MultiBooleanSetting ignoreEffects = new MultiBooleanSetting(
            "Игнорировать эффекты",
            new BooleanSetting("Замедление", false),
            new BooleanSetting("Слепота", false)
    );

    public AutoSprint() {
        this.addSettings(new Setting[]{ignoreEffects});
    }

    public static AutoSprint getInstance() {
        return Vesence.get.getManager().get(AutoSprint.class);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player != null && mc.world != null) {
            if (!canSprint()) {
                mc.player.setSprinting(false);
                mc.options.sprintKey.setPressed(false);
            } else if (!mc.options.sprintKey.isPressed()) {
                mc.player.setSprinting(true);
                mc.options.sprintKey.setPressed(true);
            }

            tickStop--;
        }
    }

    public boolean canSprint() {
        if (!this.enable || mc.player == null || mc.options == null) {
            return false;
        }

        boolean hasSlowness = mc.player.hasStatusEffect(StatusEffects.SLOWNESS);
        boolean hasBlindness = mc.player.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean ignoreSlowness = ignoreEffects.get("Замедление");
        boolean ignoreBlindness = ignoreEffects.get("Слепота");

        boolean blockedBySlowness = hasSlowness && !ignoreSlowness;
        boolean blockedByBlindness = hasBlindness && !ignoreBlindness;

        boolean horizontalCollision = mc.player.horizontalCollision && !mc.player.collidedSoftly;
        boolean sneaking = mc.player.isSneaking() && !mc.player.isSwimming();

        return tickStop <= 0 && !sneaking && !blockedBySlowness && !blockedByBlindness && !horizontalCollision && mc.player.forwardSpeed > 0.0F;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) {
            mc.player.setSprinting(false);
            mc.options.sprintKey.setPressed(false);
        }
        tickStop = 0;
    }
}
