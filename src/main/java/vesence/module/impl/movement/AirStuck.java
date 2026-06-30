package vesence.module.impl.movement;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;

import java.util.List;

@IModule(
        name = "AirStuck",
        category = Category.MOVEMENT,
        description = "Зависает в воздухе"
)
public class AirStuck extends Module {
   public static AirStuck INSTANCE = new AirStuck();

   public final BooleanSetting onlyFalling = new BooleanSetting("Только при падении", false);
   private final BooleanSetting swapToChestplate = new BooleanSetting("Свап на нагрудник", false);
   private final BooleanSetting swapBack = new BooleanSetting("Свапать обратно", false);

   private boolean wearingElytra;
   public boolean frozen;
   private Vec3d frozenPos;
   private Packet<?> lastPacket;

   public AirStuck() {
      this.addSettings(onlyFalling, swapToChestplate, swapBack);
   }

   @Override
   public void onEnable() {
      super.onEnable();
      frozen = false;
      frozenPos = null;
      lastPacket = null;
      wearingElytra = false;
   }

   @Override
   public void onDisable() {
      if (swapBack.get() && wearingElytra) {
         trySwapToElytra();
      }

      super.onDisable();

      if (mc.player != null && frozenPos != null) {
         mc.player.setVelocity(Vec3d.ZERO);
      }

      frozenPos = null;
      frozen = false;
      lastPacket = null;
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) return;

      if (frozen && frozenPos != null) {
         mc.player.setPosition(frozenPos.x, frozenPos.y, frozenPos.z);
         mc.player.setVelocity(0, 0, 0);
         mc.player.fallDistance = 0.0F;
         return;
      }

      if (onlyFalling.get()) {
         if (mc.player.isOnGround() && !mc.world.isAir(mc.player.getBlockPos().down())) {
            return;
         }
      }

      wearingElytra = mc.player.isGliding()
              || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

      if (swapToChestplate.get() && wearingElytra) {
         trySwapToChestplate();
      }

      frozenPos = mc.player.getEntityPos();
      frozen = true;
   }

   @EventInit
   public void onPacket(EventPacket e) {
      if (!frozen || mc.player == null || frozenPos == null) return;
      if (!e.isSend()) return;

      if (e.getPacket() instanceof PlayerMoveC2SPacket movePacket) {
         if (movePacket instanceof PlayerMoveC2SPacket.Full
                 || movePacket instanceof PlayerMoveC2SPacket.PositionAndOnGround) {
            lastPacket = movePacket;
         }
         e.cancel();
      }
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || !frozen) return;
      mc.player.setVelocity(Vec3d.ZERO);
   }

   private int findInvSlot(List<Item> items, int from, int to) {
      if (mc.player == null) return -1;
      for (int i = from; i <= to && i < 36; i++) {
         ItemStack s = mc.player.getInventory().getStack(i);
         if (!s.isEmpty() && items.contains(s.getItem())) return i;
      }
      return -1;
   }

   private void trySwapToChestplate() {
      if (mc.player == null || mc.interactionManager == null) return;
      if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;

      int slot = findInvSlot(
              List.of(Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE,
                      Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE,
                      Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE), 0, 8);
      if (slot != -1) {
         mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
         mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
      }
   }

   private void trySwapToElytra() {
      if (mc.player == null || mc.interactionManager == null) return;
      if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) return;

      int slot = findInvSlot(List.of(Items.ELYTRA), 0, 8);
      if (slot != -1) {
         mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
         mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
      }
   }
}
