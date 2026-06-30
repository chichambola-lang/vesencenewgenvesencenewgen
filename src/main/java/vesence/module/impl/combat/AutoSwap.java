package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;

import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;
import vesence.utils.notifications.Notifications;
import vesence.utils.player.InventoryActionUtil;
import vesence.utils.player.MoveUtil;
import vesence.utils.other.Mathf;

import org.lwjgl.glfw.GLFW;
import vesence.utils.render.text.ColorFormat;

@IModule(name = "AutoSwap", description = "Свапает по бинду предмет в оффхэнд", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoSwap extends Module {

    public final ModeSetting firstItem  = new ModeSetting("Первый предмет",  "Тотем", "Тотем", "Золотое яблоко", "Щит", "Голова игрока");
    public final ModeSetting secondItem = new ModeSetting("Второй предмет",  "Голова игрока", "Тотем", "Золотое яблоко", "Щит", "Голова игрока");
    public final ModeSetting mode = new ModeSetting("Режим", "Легитный", "Обычный", "Легитный");
    public final BindSettings swapBind  = new BindSettings("Кнопка", GLFW.GLFW_KEY_UNKNOWN);
    public final BooleanSetting notify  = new BooleanSetting("Уведомление", true);
    public final BooleanSetting onlyEnchanted = new BooleanSetting("Только зачарованные тотемы", false);
    public final BooleanSetting spookyBypass = new BooleanSetting("Обход SpookyTime", false);
    private boolean swapRequested = false;
    private boolean wasPressed = false;
    private static final long SWAP_COOLDOWN_MS = 100L;
    private long lastSwapTime = 0L;
    private boolean progress = false;
    private boolean allow = false;
    private long delay = -1L;
    private int cachedSlot = -1;
    private boolean lockHeld = false;
    private boolean lockApplied = false;
    private long lockStartTime = 0L;
    private static final long LOCK_TIMEOUT_MS = 300L;

    public AutoSwap() {
        addSettings(mode, firstItem, secondItem, swapBind, notify, onlyEnchanted, spookyBypass);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable) return;
        if (mc.player == null || mc.world == null) return;

        if (spookyBypass.get() && progress) {
            if (allow) {
                if (MoveUtil.isMoving() && mc.player.isOnGround()) {
                    MoveComponent.stopTicks = (int) Mathf.randomValue(1, 2);
                    MoveComponent.stop = true;
                } else {
                    performSwap(cachedSlot);
                    delay = System.currentTimeMillis() + 250L;
                    allow = false;
                }
            } else if (delay >= 0L && System.currentTimeMillis() >= delay) {
                MoveComponent.stop = false;
                MoveComponent.stopTicks = 0;
                resetSpookyState();
            }
            return;
        }

        boolean pressed = swapBind.isPressed();
        if (pressed && !wasPressed) {
            swapRequested = true;
        }
        wasPressed = pressed;

        if (!swapRequested) {
            clearLock();
            return;
        }

        if (spookyBypass.get()) {
            int slot = resolveTargetSlot();
            if (slot < 0) {
                swapRequested = false;
                clearLock();
                return;
            }
            progress = true;
            allow = true;
            cachedSlot = slot;
            if (mc.player.isOnGround()) {
                MoveComponent.stopTicks = (int) Mathf.randomValue(1, 2);
                MoveComponent.stop = true;
            }
            swapRequested = false;
        } else {
            long now = System.currentTimeMillis();
            if (now - lastSwapTime < SWAP_COOLDOWN_MS) return;

            int targetSlot = resolveTargetSlot();
            if (targetSlot >= 0) {
                if (mode.is("Легитный")) {
                    if (!lockHeld) {
                        MoveComponent.stop = true;
                        lockHeld = true;
                        lockApplied = false;
                        lockStartTime = now;
                        return;
                    }

                    if (!lockApplied) {
                        lockApplied = true;
                        return;
                    }

                    boolean canSwap = mc.currentScreen == null;

                    if (canSwap) {
                        performSwap(targetSlot);
                        lastSwapTime = now;
                        swapRequested = false;
                        clearLock();
                        return;
                    }

                    if (lockHeld && now - lockStartTime > LOCK_TIMEOUT_MS) {
                        clearLock();
                        swapRequested = false;
                    }
                } else {
                    performSwap(targetSlot);
                    lastSwapTime = now;
                    swapRequested = false;
                }
            } else {
                swapRequested = false;
                clearLock();
            }
        }
    }

    private int resolveTargetSlot() {
        ItemStack offhand = mc.player.getOffHandStack();
        Item currentOff = offhand.getItem();

        Item first  = resolveItem(firstItem.get());
        Item second = resolveItem(secondItem.get());

        if (currentOff == Items.AIR) {
            int s = findSlot(first);
            return s >= 0 ? s : findSlot(second);
        }

        if (currentOff == first)  return findSlot(second);
        if (currentOff == second) return findSlot(first);

        int s = findSlot(first);
        return s >= 0 ? s : findSlot(second);
    }

    private Item resolveItem(String name) {
        return switch (name) {
            case "Тотем"           -> Items.TOTEM_OF_UNDYING;
            case "Щит"             -> Items.SHIELD;
            case "Золотое яблоко"  -> Items.GOLDEN_APPLE;
            case "Голова игрока"   -> Items.PLAYER_HEAD;
            default                -> Items.AIR;
        };
    }

    private int findSlot(Item item) {
        if (item == Items.AIR) return -1;
        boolean totem = item == Items.TOTEM_OF_UNDYING;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isOf(item)) continue;
            if (totem && onlyEnchanted.get() && !s.hasGlint()) continue;
            if (totem && !onlyEnchanted.get() && s.hasGlint()) continue;
            return i;
        }
        if (totem) {
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).isOf(item)) return i;
            }
        }
        return -1;
    }

    private void performSwap(int invSlot) {
        ItemStack targetStack = mc.player.getInventory().getStack(invSlot).copy();

        if (invSlot >= 0 && invSlot < 9) {
            InventoryActionUtil.swapHotbarWithOffhandPacket(invSlot);
        } else {
            InventoryActionUtil.swapWithOffhand(invSlot);
        }

        if (notify.get()) {
            if (!targetStack.isEmpty()) {
                String itemName = vesence.utils.render.text.RichTextUtil.itemName(targetStack, 255);
                Notifications.add(targetStack, ColorFormat.color(255,255,255) + "Свап на: " + itemName);
            }
        }
    }
    private void clearLock() {
        if (lockHeld) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
            lockHeld = false;
            lockApplied = false;
        }
    }

    private void resetSpookyState() {
        progress = false;
        allow = false;
        delay = -1L;
        cachedSlot = -1;
    }

    private void cleanup() {
        clearLock();
        if (progress) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
            resetSpookyState();
        }
    }

    @Override
    public void onDisable() {
        swapRequested = false;
        wasPressed = false;
        cleanup();
        super.onDisable();
    }
}
