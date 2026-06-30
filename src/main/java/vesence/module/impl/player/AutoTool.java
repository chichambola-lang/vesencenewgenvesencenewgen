package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import vesence.event.EventInit;
import vesence.event.impl.BlockBreakingEvent;
import vesence.event.impl.EventUpdate;
import vesence.event.player.AttackEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;

@IModule(name = "AutoTool", description = "Автоматически выбирает лучший инструмент", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoTool extends Module {

    public BooleanSetting sword = new BooleanSetting("Меч при атаке", true);
    public BooleanSetting silentSwap = new BooleanSetting("Визуально", true);

    private ItemStack originalStack = null;
    private int originalSlotIndex = -1;
    private int toolSlotIndex = -1;
    private boolean isActive = false;
    private long lastSwapTime = 0;
    private long lastBreakTime = 0;
    private Slot swapBackSlot = null;

    public AutoTool() {
        this.addSettings(new Setting[]{sword, silentSwap});
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    private void resetState() {
        lastSwapTime = 0;
        lastBreakTime = 0;
        originalStack = null;
        originalSlotIndex = -1;
        toolSlotIndex = -1;
        isActive = false;
        swapBackSlot = null;
    }

    @EventInit
    public void onBlockBreaking(BlockBreakingEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isCreative()) return;
        if (isActive) return;

        lastBreakTime = System.currentTimeMillis();

        if (!hasSwapCooldownPassed()) return;

        Slot bestSlot = findBestTool(e.getBlockPos());
        Slot mainHandSlot = getMainHandSlot();

        if (bestSlot == null || mainHandSlot == null) return;
        if (bestSlot.id == mainHandSlot.id) return;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        int bestToolHotbarIndex = bestSlot.id - 36;

        if (silentSwap.get()) {
            originalStack = mc.player.getInventory().getStack(selectedSlot).copy();
            originalSlotIndex = selectedSlot;
            toolSlotIndex = bestToolHotbarIndex;
        }

        swapBackSlot = bestSlot;
        swapToHand(bestSlot);

        isActive = true;
        lastSwapTime = System.currentTimeMillis();
    }

    @EventInit
    public void onTick(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (!isActive) return;

        if (silentSwap.get() && originalSlotIndex != -1) {
            int currentSlot = mc.player.getInventory().getSelectedSlot();
            if (currentSlot != originalSlotIndex) {
                forceReset();
                return;
            }
        }

        if (!hasSwapCooldownPassed()) return;

        boolean breakingStopped = System.currentTimeMillis() - lastBreakTime >= 100;

        if (breakingStopped) {
            if (swapBackSlot != null) {
                swapToHand(swapBackSlot);
            }

            originalStack = null;
            originalSlotIndex = -1;
            toolSlotIndex = -1;
            isActive = false;
            swapBackSlot = null;
            lastSwapTime = System.currentTimeMillis();
        }
    }

    @EventInit
    public void onAttack(AttackEvent event) {
        if (!sword.get() || mc.player == null) return;

        Entity target = event.getTarget();
        if (target == null) return;
        if (target instanceof EndCrystalEntity) return;

        ItemStack heldItem = mc.player.getMainHandStack();
        if (heldItem.isIn(ItemTags.SWORDS)) return;
        if (heldItem.isOf(Items.GOLDEN_APPLE) || heldItem.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                || heldItem.contains(DataComponentTypes.FOOD) || heldItem.getItem() instanceof PotionItem) return;

        int bestSwordSlot = findSwordSlot();
        if (bestSwordSlot != -1) {
            mc.player.getInventory().setSelectedSlot(bestSwordSlot);
        }
    }

    private void forceReset() {
        originalStack = null;
        originalSlotIndex = -1;
        toolSlotIndex = -1;
        isActive = false;
        swapBackSlot = null;
    }

    private boolean hasSwapCooldownPassed() {
        return System.currentTimeMillis() - lastSwapTime >= 350;
    }

    private Slot getMainHandSlot() {
        if (mc.player == null) return null;
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        return mc.player.playerScreenHandler.getSlot(36 + selectedSlot);
    }

    private void swapToHand(Slot slot) {
        if (mc.player == null || mc.interactionManager == null || slot == null) return;
        int hotbarSlot = mc.player.getInventory().getSelectedSlot();
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                slot.id,
                hotbarSlot,
                SlotActionType.SWAP,
                mc.player
        );
    }

    private Slot findBestTool(net.minecraft.util.math.BlockPos blockPos) {
        if (mc.player == null || mc.world == null || blockPos == null) return getMainHandSlot();

        BlockState state = mc.world.getBlockState(blockPos);
        if (state.isAir()) return getMainHandSlot();

        Slot mainHandSlot = getMainHandSlot();
        float currentSpeed = mainHandSlot != null ? getMiningSpeed(mainHandSlot.getStack(), state) : 1.0f;

        Slot bestSlot = mc.player.playerScreenHandler.slots.stream()
                .filter(slot -> slot.id >= 36 && slot.id <= 44)
                .filter(slot -> !slot.getStack().isEmpty())
                .filter(slot -> getMiningSpeed(slot.getStack(), state) > 1.0f)
                .max(java.util.Comparator.comparingDouble(slot -> (double) getMiningSpeed(slot.getStack(), state)))
                .orElse(null);

        if (bestSlot != null && getMiningSpeed(bestSlot.getStack(), state) > currentSpeed) {
            return bestSlot;
        }

        return mainHandSlot;
    }

    private int findSwordSlot() {
        int bestSlot = -1;
        float bestDamage = -1.0F;

        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isIn(ItemTags.SWORDS)) {
                float damage = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        net.minecraft.component.type.AttributeModifiersComponent.DEFAULT)
                        .modifiers().stream()
                        .filter(mod -> mod.modifier().id().toString().contains("attack_damage"))
                        .map(mod -> (float) mod.modifier().value())
                        .reduce(0.0F, Float::max);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = slot;
                }
            }
        }
        return bestSlot;
    }

    private static float getMiningSpeed(ItemStack stack, BlockState state) {
        ToolComponent tool = stack.get(DataComponentTypes.TOOL);
        if (tool == null) return 1.0F;
        return tool.getSpeed(state);
    }
}
