package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "HitBox", description = "Увеличивает хитбоксы игроков", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public class HitBox extends Module {

    public final SliderSetting size = new SliderSetting("Размер", 0.2, 0.0, 3.0, 0.05);
    public final BooleanSetting visible = new BooleanSetting("Визуал", false);

    public HitBox() {
        this.addSettings(new Setting[]{size, visible});
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!visible.get() || mc.player == null) return;

        double sizeMultiplier = this.size.get() * 2.5;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isNotValid(player)) {
                player.setBoundingBox(calculateBoundingBox(player, sizeMultiplier));
            }
        }
    }

    private boolean isNotValid(PlayerEntity player) {
        return player == mc.player || !player.isAlive();
    }

    private Box calculateBoundingBox(Entity entity, double size) {
        double minX = entity.getX() - size;
        double minY = entity.getBoundingBox().minY;
        double minZ = entity.getZ() - size;
        double maxX = entity.getX() + size;
        double maxY = entity.getBoundingBox().maxY;
        double maxZ = entity.getZ() + size;

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static HitBox instance;

    public static HitBox getInstance() {
        return instance;
    }
}
