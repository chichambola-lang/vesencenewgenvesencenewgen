package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "AutoEat", description = "Автоматически ест еду при определенном уровне голода", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoEat extends Module {

    public static SliderSetting hungerLevel = new SliderSetting("Уровень голода", 14, 1, 20, 1, false);

    private boolean isEating = false;
    private int prevSlot = -1;

    public AutoEat() {
        addSettings(hungerLevel);
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (isEating) {
            if (mc.player.isUsingItem()) {
                mc.options.useKey.setPressed(true);
                return;
            }
            stopEating();
            return;
        }

        int foodLevel = mc.player.getHungerManager().getFoodLevel();
        if (foodLevel >= hungerLevel.get().intValue()) return;
        if (!mc.player.canConsume(false)) return;

        ItemStack offhand = mc.player.getStackInHand(Hand.OFF_HAND);
        if (offhand.contains(DataComponentTypes.FOOD)) {
            mc.options.useKey.setPressed(true);
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            isEating = true;
            return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.contains(DataComponentTypes.FOOD)) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(i);
                mc.options.useKey.setPressed(true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                isEating = true;
                return;
            }
        }
    }

    private void stopEating() {
        mc.options.useKey.setPressed(false);
        isEating = false;
        if (prevSlot != -1) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (isEating) {
            stopEating();
        }
    }
}
