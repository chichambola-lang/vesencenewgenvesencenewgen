package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;
import vesence.utils.notifications.Notifications;
import vesence.utils.player.MoveUtil;
import vesence.utils.other.Mathf;
import vesence.utils.render.text.ColorFormat;

@IModule(name = "AutoTotem", description = "Автоматически помещает тотем в оффхэнд при опасности", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoTotem extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Обычный", "Обычный", "Легитный");
    public final BooleanSetting spookyBypass = new BooleanSetting("Обход SpookyTime", false);
    public final MultiBooleanSetting options = new MultiBooleanSetting("Настройки",
            new BooleanSetting("Динамит", true),
            new BooleanSetting("Падение", false),
            new BooleanSetting("Трезубец", true),
            new BooleanSetting("Якорь", false),
            new BooleanSetting("Эндер-кристалл", false));

    public final SliderSetting health = new SliderSetting("Здоровье", 4.0, 1.0, 20.0, 0.5, false);
    public final SliderSetting elytraHealth = new SliderSetting("Здоровье на элитре", 9.0, 0.0, 20.0, 0.5, false)
            .hidden(() -> !options.get("Здоровье с элитрами"));
    public final SliderSetting crystalDistance = new SliderSetting("Дистанция до кристалла", 4.0, 1.0, 10.0, 1.0, false)
            .hidden(() -> !options.get("Эндер-кристалл"));
    public final SliderSetting tntDistance = new SliderSetting("Дистанция до динамита", 30.0, 3.0, 50.0, 1.0, false)
            .hidden(() -> !options.get("Динамит"));
    public final SliderSetting tridentDistance = new SliderSetting("Дистанция трезубца", 10.0, 5.0, 50.0, 1.0, false)
            .hidden(() -> !options.get("Трезубец"));
    public final BooleanSetting notify  = new BooleanSetting("Уведомление", true);
    private ItemStack savedOffhandItem = ItemStack.EMPTY;
    private int savedInvSlot = -1;
    private boolean lockHeld = false;
    private long lockStartTime = 0L;
    private static final long SWAP_CD_MS = 80L;
    private long lastSwapMs = 0L;
    private boolean spookyProgress = false;
    private boolean spookyAllow = false;
    private long spookyDelay = -1L;
    private int spookySlot = -1;
    private boolean spookyIsSwapBack = false;

    private ItemStack lastHeldTotem = ItemStack.EMPTY;

    public AutoTotem() {
        addSettings(mode, spookyBypass, options, health, elytraHealth, crystalDistance, tntDistance, tridentDistance, notify);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable) return;
        if (mc.player == null || !mc.player.isAlive() || mc.world == null) {
            cleanup();
            return;
        }

        ItemStack heldTotem = heldTotemOf(mc.player);
        if (!heldTotem.isEmpty()) {
            lastHeldTotem = heldTotem.copy();
        }

        if (spookyBypass.get() && spookyProgress) {
            handleSpookyBypass();
            return;
        }

        boolean needsTotem = canSwap();
        ItemStack offhandStack = mc.player.getOffHandStack();
        boolean totemInOffhand = offhandStack.isOf(Items.TOTEM_OF_UNDYING);
        boolean enchantedTotemInOffhand = totemInOffhand && offhandStack.hasGlint();

        if (mode.is("Обычный")) {
            handleNormal(needsTotem, totemInOffhand, enchantedTotemInOffhand);
        } else {
            handleLegit(needsTotem, totemInOffhand, enchantedTotemInOffhand);
        }
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable) return;
        if (!notify.get()) return;
        if (event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket) {
            if (statusPacket.getStatus() == 35) {
                Entity entity = statusPacket.getEntity(mc.world);
                if (entity == null) return;

                if (entity == mc.player) {
                    ItemStack totemStack = totemStackFor(mc.player);
                    String totemName = vesence.utils.render.text.RichTextUtil.itemName(totemStack, 255);
                    Notifications.add(totemStack,
                            ColorFormat.color(255,255,255) + "Вы потеряли " + totemName + ColorFormat.color(255,255,255) + "!");
                } else if (entity instanceof PlayerEntity player) {
                    ItemStack totemStack = totemStackFor(player);
                    String totemName = vesence.utils.render.text.RichTextUtil.itemName(totemStack, 255);
                    String playerName = vesence.utils.render.text.RichTextUtil.toColorFormat(player.getDisplayName(), 0xFFFFFF, 255);
                    if (playerName.isEmpty()) playerName = player.getName().getString();
                    Notifications.add(totemStack,
                            ColorFormat.color(255,255,255) + playerName + ColorFormat.color(255,255,255) + " потерял " + totemName + ColorFormat.color(255,255,255) + "!");
                }
            }
        }
    }

    private ItemStack heldTotemOf(PlayerEntity player) {
        ItemStack off = player.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) return off;
        ItemStack main = player.getMainHandStack();
        if (main.isOf(Items.TOTEM_OF_UNDYING)) return main;
        return ItemStack.EMPTY;
    }

    private ItemStack totemStackFor(PlayerEntity player) {
        ItemStack held = heldTotemOf(player);
        if (!held.isEmpty()) return held;
        if (player == mc.player && !lastHeldTotem.isEmpty()) return lastHeldTotem;
        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    private void handleSpookyBypass() {
        if (spookyAllow) {
            if (MoveUtil.isMoving() && mc.player.isOnGround()) {
                MoveComponent.stopTicks = (int) Mathf.randomValue(1, 2);
                MoveComponent.stop = true;
            } else {
                performTotemSwap(spookySlot, spookyIsSwapBack);
                spookyDelay = System.currentTimeMillis() + 250L;
                spookyAllow = false;
            }
        } else if (spookyDelay >= 0L && System.currentTimeMillis() >= spookyDelay) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
            resetSpookyState();
        }
    }

    private void startSpookyBypass(int slot, boolean isSwapBack) {
        spookyProgress = true;
        spookyAllow = true;
        spookySlot = slot;
        spookyIsSwapBack = isSwapBack;
    }

    private void handleNormal(boolean needsTotem, boolean totemInOffhand, boolean enchantedTotemInOffhand) {
        if (needsTotem) {
            if (!totemInOffhand) {
                int slot = findTotemSlot();
                if (slot >= 0) {
                    saveOffhandAndSwap(slot, false);
                }
            } else if (enchantedTotemInOffhand) {
                int slot = findNormalTotemSlot();
                if (slot >= 0) {
                    saveOffhandAndSwap(slot, false);
                }
            }
        } else if (savedInvSlot != -1 && !savedOffhandItem.isEmpty()) {
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                if (spookyBypass.get() && !spookyProgress) {
                    startSpookyBypass(savedInvSlot, true);
                    return;
                }
                performTotemSwap(savedInvSlot, true);
            } else {
                resetSavedSlot();
            }
        }
    }

    private void handleLegit(boolean needsTotem, boolean totemInOffhand, boolean enchantedTotemInOffhand) {
        if (needsTotem) {
            if (!totemInOffhand) {
                int slot = findTotemSlot();
                if (slot < 0) {
                    clearLock();
                    return;
                }
                tryLegitSwap(slot, false);
            } else if (enchantedTotemInOffhand) {
                int slot = findNormalTotemSlot();
                if (slot < 0) {
                    clearLock();
                    return;
                }
                tryLegitSwap(slot, false);
            } else {
                clearLock();
            }
        } else if (savedInvSlot != -1 && !savedOffhandItem.isEmpty()) {
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                tryLegitSwap(savedInvSlot, true);
            } else {
                resetSavedSlot();
                clearLock();
            }
        } else {
            clearLock();
        }
    }

    private void tryLegitSwap(int slot, boolean isSwapBack) {
        if (!lockHeld && !spookyProgress) {
            MoveComponent.stopTicks = (int) Mathf.randomValue(1, 2);
            MoveComponent.stop = true;
            lockHeld = true;
            lockStartTime = System.currentTimeMillis();
        }

        if (!MoveUtil.isMoving() && mc.currentScreen == null) {
            if (savedOffhandItem.isEmpty() && !mc.player.getOffHandStack().isEmpty()) {
                savedOffhandItem = mc.player.getOffHandStack().copy();
                savedInvSlot = slot;
            }

            if (spookyBypass.get() && !spookyProgress) {
                clearLock();
                startSpookyBypass(slot, isSwapBack);
                return;
            }

            performTotemSwap(slot, isSwapBack);
            clearLock();
        } else if (lockHeld && System.currentTimeMillis() - lockStartTime > (isSwapBack ? 300L : 150L)) {
            clearLock();
            if (!isSwapBack) resetSavedSlot();
        }
    }

    private void saveOffhandAndSwap(int slot, boolean isSwapBack) {
        if (savedOffhandItem.isEmpty() && !mc.player.getOffHandStack().isEmpty()) {
            savedOffhandItem = mc.player.getOffHandStack().copy();
            savedInvSlot = slot;
        }

        if (spookyBypass.get() && !spookyProgress) {
            startSpookyBypass(slot, isSwapBack);
            return;
        }

        performTotemSwap(slot, isSwapBack);
    }

    private void performTotemSwap(int slot, boolean isSwapBack) {
        long now = System.currentTimeMillis();
        if (now - lastSwapMs < SWAP_CD_MS) return;
        lastSwapMs = now;

        ItemStack swappedTotem = ItemStack.EMPTY;
        if (!isSwapBack && slot >= 0 && slot < 36) {
            swappedTotem = mc.player.getInventory().getStack(slot).copy();
        }

        int invFrom = slot < 9 ? slot + 36 : slot;
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                invFrom,
                40,
                SlotActionType.SWAP,
                mc.player
        );

        if (mc.currentScreen == null) {

        }

        if (notify.get() && !isSwapBack && !swappedTotem.isEmpty()) {
            String totemName = vesence.utils.render.text.RichTextUtil.itemName(swappedTotem, 255);
            Notifications.add(swappedTotem, ColorFormat.color(255, 255, 255) + "Свап на: " + totemName);
        }

        if (isSwapBack) {
            resetSavedSlot();
        }
    }

    private int findTotemSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING) && !s.hasGlint()) return i;
        }
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private int findNormalTotemSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING) && !s.hasGlint()) return i;
        }
        return -1;
    }

    private boolean canSwap() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp <= (float) health.current) return true;
        if (options.get("Динамит") && checkTnt()) return true;
        if (options.get("Падение") && mc.player.fallDistance > 10.0f) return true;
        if (options.get("Трезубец") && checkTrident()) return true;
        if (options.get("Якорь") && checkAnchor()) return true;
        if (options.get("Эндер-кристалл") && checkCrystal()) return true;
        return false;
    }

    private boolean checkTnt() {
        float maxDist = (float) tntDistance.current;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof TntEntity && mc.player.distanceTo(e) <= maxDist) return true;
        }
        return false;
    }

    private boolean checkTrident() {
        float maxDist = (float) tridentDistance.current;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof TridentEntity trident && mc.player.distanceTo(trident) <= maxDist) {
                if (trident.getX() != trident.lastRenderX
                        || trident.getY() != trident.lastRenderY
                        || trident.getZ() != trident.lastRenderZ) return true;
            }
        }
        return false;
    }

    private boolean checkCrystal() {
        float maxDist = (float) crystalDistance.current;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity && mc.player.distanceTo(e) <= maxDist) return true;
        }
        return false;
    }

    private boolean checkAnchor() {
        BlockPos playerPos = mc.player.getBlockPos();
        int range = 4;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (mc.world.getBlockState(playerPos.add(dx, dy, dz)).isOf(Blocks.RESPAWN_ANCHOR)) return true;
                }
            }
        }
        return false;
    }

    private void clearLock() {
        if (lockHeld) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
            lockHeld = false;
        }
    }

    private void resetSavedSlot() {
        savedOffhandItem = ItemStack.EMPTY;
        savedInvSlot = -1;
    }

    private void resetSpookyState() {
        spookyProgress = false;
        spookyAllow = false;
        spookyDelay = -1L;
        spookySlot = -1;
        spookyIsSwapBack = false;
    }

    private void cleanup() {
        clearLock();
        resetSavedSlot();
        if (spookyProgress) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
            resetSpookyState();
        }
    }

    @Override
    public void onDisable() {
        cleanup();
        super.onDisable();
    }
}
