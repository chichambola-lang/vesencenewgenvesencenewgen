package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.EventInput;
import vesence.event.player.ScreenCloseEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.utils.other.Mathf;
import vesence.utils.other.StopWatch;
import vesence.utils.other.TimerUtil;
import vesence.utils.player.MoveUtil;
import vesence.utils.player.MovementManager;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;

import java.util.ArrayList;
import java.util.List;

@IModule(
   name = "GuiMove",
   description = "Позволяет двигаться в инвентаре",
   category = Category.MOVEMENT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class GuiMove extends Module {

   public static boolean sendingQueued = false;
   private boolean closing = false;
   public boolean slow = false;
   public static boolean stopzx = false;
   private int pusotras = 0;
   private int stophw = 0;

   private final ModeSetting mode = new ModeSetting("Режим", "Обычный", "Обычный", "Грим", "Легитный");
   private final BooleanSetting stop = new BooleanSetting("Полная остановка", false);

   private final List<Packet<?>> packet = new ArrayList<>();
   private final List<Packet<?>> spookyQueue = new ArrayList<>();
   private final StopWatch timer = new StopWatch();
   private final TimerUtil wait = new TimerUtil();
   private final StopWatch sprint = new StopWatch();

   public GuiMove() {
      this.addSettings(new Setting[]{mode, stop});
   }

   private KeyBinding[] movementKeys() {
      return new KeyBinding[]{
         mc.options.forwardKey, mc.options.backKey,
         mc.options.leftKey, mc.options.rightKey,
         mc.options.jumpKey, mc.options.sprintKey
      };
   }

   private KeyBinding[] pressedKeys() {
      return new KeyBinding[]{
         mc.options.forwardKey, mc.options.backKey,
         mc.options.leftKey, mc.options.rightKey,
         mc.options.jumpKey
      };
   }

   private boolean isInInventory() {
      return mc.currentScreen instanceof InventoryScreen
         || mc.currentScreen instanceof GenericContainerScreen
         || mc.currentScreen instanceof ShulkerBoxScreen
         || mc.currentScreen instanceof CraftingScreen
         || mc.currentScreen instanceof AnvilScreen
         || mc.currentScreen instanceof FurnaceScreen
         || mc.currentScreen instanceof BlastFurnaceScreen
         || mc.currentScreen instanceof EnchantmentScreen;
   }

   @EventInit
   public void onUpdate(ClientTickEvent e) {
      if (mc.player == null) return;
      if (!this.enable) return;

      --this.stophw;

      if ("Легитный".equals(mode.get())) {
         if (mc.currentScreen instanceof ChatScreen) return;
         KeyBinding[] keys = pressedKeys();
         if (stopzx) {
            for (KeyBinding keyBinding : keys) {
               keyBinding.setPressed(false);
            }
         } else {
            updateKeyBindingState(keys);
         }
         return;
      }

      if ("Обычный".equals(mode.get())) {
         final KeyBinding[] keys = pressedKeys();
         if (!wait.hasReached(51)) {
            for (KeyBinding keyBinding : keys) {
               keyBinding.setPressed(false);
            }
            return;
         }
         if (mc.currentScreen == null) {
            updateKeyBindingState(keys);
            return;
         }
         if (mc.currentScreen instanceof ChatScreen || isInInventory()) {
            return;
         }
         updateKeyBindingState(keys);
      } else {
         handleGrimMode();
      }
   }

   private void handleGrimMode() {
      if (!timer.isReached(250)) {
         for (KeyBinding key : movementKeys()) {
            key.setPressed(false);
         }
         MovementManager.getInstance().lockMovement("GuiMove");
         return;
      }

      if (mc.currentScreen == null) {
         MovementManager.getInstance().unlockMovement("GuiMove");
         updateKeyBindingState(movementKeys());
         return;
      }

      if (!(mc.currentScreen instanceof InventoryScreen)) {
         MovementManager.getInstance().unlockMovement("GuiMove");
      }

      if (isInInventory()) {
         updateKeyBindingState(movementKeys());
      }
   }

   @EventInit
   public void onPacket(EventPacket e) {
      if (!this.enable) return;
      if (sendingQueued) return;
      if (mc.player == null) return;

      Packet<?> p = e.getPacket();

      if (p instanceof CloseScreenS2CPacket && "Легитный".equals(mode.get())) {
         e.cancel();
      }

      if (p instanceof ClickSlotC2SPacket clickPacket) {
         int slotId = clickPacket.slot();
         boolean isCraftingSlot = slotId >= 1 && slotId <= 4;

         if (isCraftingSlot) return;

         if ("Легитный".equals(mode.get())) {
            if (clickPacket.actionType() == SlotActionType.PICKUP) {
               this.pusotras = 5;
            }

            if (MoveUtil.isMoving() && mc.currentScreen instanceof InventoryScreen) {
               e.cancel();
               stopzx = true;
               new Thread(() -> {
                  stopzx = true;
                  this.wait.reset();
                  try {
                     Thread.sleep(90L);
                  } catch (InterruptedException ex) {
                     throw new RuntimeException(ex);
                  }
                  sendingQueued = true;
                  mc.getNetworkHandler().sendPacket(clickPacket);
                  sendingQueued = false;
                  ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
                  if (cursorStack.isEmpty()) {
                     sendingQueued = true;
                     mc.getNetworkHandler().sendPacket(
                        new CloseHandledScreenC2SPacket(0));
                     sendingQueued = false;
                  }
                  try {
                     Thread.sleep(70L);
                  } catch (InterruptedException ex) {
                     throw new RuntimeException(ex);
                  }
                  stopzx = false;
               }).start();
               stopzx = false;
            }
            return;
         }

         if ("Обычный".equals(mode.get())) {
            if (clickPacket.actionType() == SlotActionType.PICKUP
               && (mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof ShulkerBoxScreen)
               && MoveUtil.isMoving()) {
               if (mc.currentScreen instanceof InventoryScreen) {
                  packet.add(clickPacket);
                  e.cancel();
               }
            }
         } else {
            if ((mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof ShulkerBoxScreen)) {
               MovementManager.getInstance().lockMovement("GuiMove");
               if (MoveUtil.isMoving()) {
                  spookyQueue.add(clickPacket);
                  e.cancel();
               }
            }
         }
      }
   }

   @EventInit
   public void onClose(ScreenCloseEvent e) {
      if (!this.enable) return;
      if (mc.player == null) return;
      if (closing) return;
      if ("Легитный".equals(mode.get())) return;

      if ("Обычный".equals(mode.get())) {
         if (mc.currentScreen instanceof InventoryScreen && MoveUtil.isMoving()) {
            closing = true;
            slow = true;
            new Thread(() -> {
               wait.reset();
               try {
                  Thread.sleep(51);
               } catch (InterruptedException ex) {
                  throw new RuntimeException(ex);
               }
               sendingQueued = true;
               for (Packet<?> pk : packet) {
                  mc.getNetworkHandler().sendPacket(pk);
               }
               sendingQueued = false;
               slow = false;
               packet.clear();
               mc.getNetworkHandler().sendPacket(
                  new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
               mc.player.setVelocity(0, 0, 0);
               MoveComponent.stopTicks = Mathf.randomInt(8, 12);
               MoveComponent.stop = true;
               closing = false;
            }).start();
            e.cancel();
         }
      } else {
         if (mc.currentScreen instanceof InventoryScreen && MoveUtil.isMoving()) {
            closing = true;
            e.cancel();
            new Thread(() -> {
               for (KeyBinding key : movementKeys()) {
                  key.setPressed(false);
               }
               timer.reset();
               try {
                  Thread.sleep(100);
               } catch (InterruptedException ex) {
                  throw new RuntimeException(ex);
               }
               sendingQueued = true;
               for (Packet<?> pk : spookyQueue) {
                  mc.getNetworkHandler().sendPacket(pk);
               }
               sendingQueued = false;
               spookyQueue.clear();
               mc.getNetworkHandler().sendPacket(
                  new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
               mc.player.setVelocity(0, 0, 0);
               MoveComponent.stopTicks = Mathf.randomInt(8, 12);
               MoveComponent.stop = true;
               closing = false;
            }).start();
         }
      }
   }

   @EventInit
   public void onMoveInput(EventInput e) {
      if (mc.player == null) return;
      if (!this.enable) return;
      if (!(mc.currentScreen instanceof InventoryScreen)) return;

      ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
      boolean hasCraftingItems = false;
      for (int i = 1; i <= 4; i++) {
         if (!mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
            hasCraftingItems = true;
            break;
         }
      }

      if ("Легитный".equals(mode.get()) && !cursorStack.isEmpty() && !hasCraftingItems) {
         e.setSneak(false);
         e.setJump(false);
         e.setForward(0.0F);
         e.setStrafe(0.0F);
      }

      if (stopzx) {
         e.setForward(0.0F);
         e.setStrafe(0.0F);
      }

      if (this.pusotras > 0) {
         e.setSneak(false);
         e.setJump(false);
         e.setForward(0.0F);
         e.setStrafe(0.0F);
         if (this.pusotras == 1 && cursorStack.isEmpty()) {
            sendingQueued = true;
            mc.getNetworkHandler().sendPacket(
               new CloseHandledScreenC2SPacket(0));
            sendingQueued = false;
         }
         --this.pusotras;
      }

      if (this.stophw > 0) {
         e.setSneak(false);
         e.setJump(false);
         e.setForward(0.0F);
         e.setStrafe(0.0F);
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();

      if (mc.currentScreen == null) {
         for (KeyBinding keyBinding : pressedKeys()) {
            keyBinding.setPressed(false);
         }
      }
      packet.clear();
      spookyQueue.clear();
      slow = false;
      closing = false;
      stopzx = false;
      pusotras = 0;
   }

   private void updateKeyBindingState(KeyBinding[] keyBindings) {
      for (KeyBinding keyBinding : keyBindings) {
         boolean isKeyPressed = InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
         keyBinding.setPressed(isKeyPressed);
      }
   }
}
