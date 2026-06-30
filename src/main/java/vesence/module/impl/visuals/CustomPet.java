package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.impl.visuals.custompet.CustomPetFollowerController;
import vesence.module.impl.visuals.custompet.CustomPetVariant;

/**
 * Кастомный питомец-компаньон, который следует за игроком и анимируется через GeckoLib 5.x.
 * Перенесён из RelevantPremiumpp4 (GeckoLib 4.8 -> 5.4, Yarn 1.21.4 -> 1.21.11).
 * Питомец полностью локальный и клиентский.
 */
@IModule(name = "Custom Pet", description = "Клиентский питомец-компаньон рядом с игроком", category = Category.VISUALS)
@Environment(EnvType.CLIENT)
public class CustomPet extends Module {

    private final ModeSetting variant = new ModeSetting(
            "Вид жабы", CustomPetVariant.NITWIT.getSettingValue(), CustomPetVariant.settingValues());

    private final CustomPetFollowerController controller = new CustomPetFollowerController();

    public CustomPet() {
        this.addSettings(this.variant);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        try {
            this.controller.reset();
        } catch (Throwable ignored) {
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            this.controller.reset();
            return;
        }
        CustomPetVariant selected = CustomPetVariant.fromSettingValue(this.variant.get());
        this.controller.tick(mc.player, selected);
    }
}
