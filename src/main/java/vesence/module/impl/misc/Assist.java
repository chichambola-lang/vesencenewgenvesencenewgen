package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;
import vesence.utils.player.InventoryActionUtil;
import vesence.utils.other.Mathf;
import java.util.Locale;

@IModule(name = "Assist", description = "Быстрое использование предметов по кнопке", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class Assist extends Module {
    public final ModeSetting mode = new ModeSetting("Режим", "FunTime", "FunTime", "HolyWorld");
    public final BindSettings windChargeBind = new BindSettings("Виндхоп", -1);
    public final BindSettings ftDisorientationBind = new BindSettings("Дезориентация", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftTrapBind = new BindSettings("Трапка", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftDustBind = new BindSettings("Явная пыль", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftCrossbowBind = new BindSettings("Арбалет", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftPlastBind = new BindSettings("Пласт", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftAuraBind = new BindSettings("Божья аура", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftSnowballBind = new BindSettings("Снежок", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftSmerchBind = new BindSettings("Смерч", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BooleanSetting ftAutoAura = new BooleanSetting("Авто божья аура", true)
            .hidden(() -> !mode.is("FunTime"));

    public final BindSettings hwExplosiveTrapBind = new BindSettings("Взрывная трапка", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwStanBind = new BindSettings("Стан", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwExplosiveThingBind = new BindSettings("Взрывная штучка", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwTrapBind = new BindSettings("Трапка", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwSnowballBind = new BindSettings("Ком снега", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BooleanSetting hwBypass = new BooleanSetting("Обход", true)
            .hidden(() -> !mode.is("HolyWorld"));

    private final boolean[] wasPressed = new boolean[11];
    private int state = 0;
    private int savedCurrentSlot = -1;
    private int savedInvSlot = -1;
    private Item pendingItem = null;
    private int swapSlot = 8;
    private boolean wasFull = false;

    private enum HWState { IDLE, USE, SWAP_BACK }
    private HWState hwState = HWState.IDLE;
    private Item hwCurrentItem = null;
    private int hwOldHotbarSlot = -1;
    private int hwTargetHotbarSlot = -1;
    private int hwSwappedFromSlot = -1;
    private boolean hwRestoreSwappedSlot = false;
    private long hwActionStartTime = 0L;
    private final java.util.Random hwRandom = new java.util.Random();

    public Assist() {
        addSettings(
                mode,
                windChargeBind,
                ftDisorientationBind, ftTrapBind, ftDustBind, ftCrossbowBind, ftPlastBind, ftAuraBind, ftSnowballBind, ftSmerchBind, ftAutoAura,
                hwExplosiveTrapBind, hwStanBind, hwExplosiveThingBind, hwTrapBind, hwSnowballBind, hwBypass
        );
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        if (mode.is("FunTime")) {
            handleFunTime();
        } else {
            handleHolyWorld();
        }
    }

    private void handleFunTime() {
        if (mc.currentScreen != null) return;

        switch (state) {
            case 1: {
                if (!InventoryActionUtil.swapWithHotbar(savedInvSlot, swapSlot)) {
                    resetState();
                    return;
                }
                wasFull = true;
                state = 2;
                return;
            }
            case 2: {
                InventoryActionUtil.useHand(Hand.MAIN_HAND);
                state = 3;
                return;
            }
            case 3: {
                if (wasFull && savedInvSlot != -1) {
                    InventoryActionUtil.swapWithHotbar(savedInvSlot, swapSlot);
                }
                resetState();
                return;
            }
            case 4: {
                InventoryActionUtil.swapHotbarSlots(savedCurrentSlot, swapSlot);
                state = 5;
                return;
            }
            case 5: {
                InventoryActionUtil.useHand(Hand.MAIN_HAND);
                state = 6;
                return;
            }
            case 6: {
                InventoryActionUtil.swapHotbarSlots(savedCurrentSlot, swapSlot);
                resetState();
                return;
            }
        }

        if (ftAutoAura.get()) {
            boolean hasNegative = false;
            StatusEffectInstance poison = mc.player.getStatusEffect(StatusEffects.POISON);
            if (poison != null && poison.getAmplifier() >= 0 && poison.getDuration() >= 50 * 20) {
                hasNegative = true;
            }
            StatusEffectInstance slowness = mc.player.getStatusEffect(StatusEffects.SLOWNESS);
            if (slowness != null && slowness.getAmplifier() >= 2 && slowness.getDuration() >= 90 * 20) {
                hasNegative = true;
            }
            StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
            if (weakness != null && weakness.getAmplifier() >= 1 && weakness.getDuration() >= 90 * 20) {
                hasNegative = true;
            }
            StatusEffectInstance wither = mc.player.getStatusEffect(StatusEffects.WITHER);
            if (wither != null && wither.getAmplifier() >= 3 && wither.getDuration() >= 30 * 20) {
                hasNegative = true;
            }

            if (hasNegative) {
                useItem("божья", Items.PHANTOM_MEMBRANE);
            }
        }

        tryUse(ftDisorientationBind, 0, "дезориентация", Items.ENDER_EYE);
        tryUse(ftTrapBind, 1, "трапка", Items.NETHERITE_SCRAP);
        tryUse(ftDustBind, 2, "явная", Items.SUGAR);
        tryUse(ftCrossbowBind, 3, null, Items.CROSSBOW);
        tryUse(ftPlastBind, 6, "пласт", Items.DRIED_KELP);
        tryUse(ftAuraBind, 7, "божья", Items.PHANTOM_MEMBRANE);
        tryUse(ftSnowballBind, 8, "снежок", Items.SNOWBALL);
        tryUse(windChargeBind, 9, "виндхоп", Items.WIND_CHARGE);
        tryUse(ftSmerchBind, 10, "смерч", Items.FIRE_CHARGE);
    }

    private void tryUse(BindSettings bind, int index, String nameSubstring, Item item) {
        if (bind.key != -1 && bind.isPressed()) {
            if (!wasPressed[index]) {
                wasPressed[index] = true;
                useItem(nameSubstring, item);
            }
        } else {
            wasPressed[index] = false;
        }
    }

    private void useItem(String nameSubstring, Item item) {
        if (state != 0) return;
        if (mc.currentScreen != null) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int hotbarSlot = findItemSlot(nameSubstring, item, true);

        if (hotbarSlot != -1) {
            if (hotbarSlot == currentSlot) {
                InventoryActionUtil.useHand(Hand.MAIN_HAND);
            } else {
                MoveComponent.stop = true;
                savedCurrentSlot = currentSlot;
                swapSlot = hotbarSlot;
                state = 4;
            }
            return;
        }

        int invSlot = findItemSlot(nameSubstring, item, false);
        if (invSlot == -1) return;

        MoveComponent.stop = true;
        savedCurrentSlot = currentSlot;
        savedInvSlot = invSlot;
        swapSlot = currentSlot;
        state = 1;
    }

    private void resetState() {
        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;
        state = 0;
        pendingItem = null;
        wasFull = false;
    }

    private void handleHolyWorld() {
        if (mc.currentScreen != null) return;

        checkHolyWorldBinds();

        if (hwState == HWState.IDLE) {
            return;
        }

        long now = System.currentTimeMillis();

        switch (hwState) {
            case USE: {
                if (now - hwActionStartTime >= 30 + hwRandom.nextInt(20)) {
                    InventoryActionUtil.useHand(Hand.MAIN_HAND);
                    hwState = HWState.SWAP_BACK;
                    hwActionStartTime = now;
                }
                break;
            }
            case SWAP_BACK: {
                if (now - hwActionStartTime >= 45 + hwRandom.nextInt(15)) {
                    resetHWState();
                }
                break;
            }
        }
    }

    private void checkHolyWorldBinds() {
        if (hwState != HWState.IDLE) return;
        if (mc.currentScreen != null) return;

        Item selectedItem = null;
        if (hwExplosiveTrapBind.key != -1 && hwExplosiveTrapBind.isPressed()) {
            selectedItem = Items.PRISMARINE_SHARD;
        } else if (hwStanBind.key != -1 && hwStanBind.isPressed()) {
            selectedItem = Items.NETHER_STAR;
        } else if (hwExplosiveThingBind.key != -1 && hwExplosiveThingBind.isPressed()) {
            selectedItem = Items.FIRE_CHARGE;
        } else if (hwTrapBind.key != -1 && hwTrapBind.isPressed()) {
            selectedItem = Items.POPPED_CHORUS_FRUIT;
        } else if (hwSnowballBind.key != -1 && hwSnowballBind.isPressed()) {
            selectedItem = Items.SNOWBALL;
        } else if (windChargeBind.key != -1 && windChargeBind.isPressed()) {
            selectedItem = Items.WIND_CHARGE;
        }

        if (selectedItem != null) {
            if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(selectedItem))) {
                return;
            }

            if (hwBypass.get()) {
                MoveComponent.stopTicks = (int) Mathf.randomValue(1, 2);
                MoveComponent.stop = true;
            }

            hwCurrentItem = selectedItem;
            hwOldHotbarSlot = mc.player.getInventory().getSelectedSlot();

            int hotbarSlot = findItemSlot(null, selectedItem, true);
            if (hotbarSlot != -1) {
                if (hotbarSlot == hwOldHotbarSlot) {
                    hwTargetHotbarSlot = hwOldHotbarSlot;
                    hwState = HWState.USE;
                    hwActionStartTime = System.currentTimeMillis();
                } else {
                    InventoryActionUtil.swapHotbarSlots(hwOldHotbarSlot, hotbarSlot);
                    hwTargetHotbarSlot = hwOldHotbarSlot;
                    hwRestoreSwappedSlot = true;
                    hwSwappedFromSlot = hotbarSlot;
                    hwState = HWState.USE;
                    hwActionStartTime = System.currentTimeMillis();
                }
            } else {
                int invSlot = findItemSlot(null, selectedItem, false);
                if (invSlot == -1) {
                    if (hwBypass.get()) {
                        MoveComponent.stop = false;
                        MoveComponent.stopTicks = 0;
                    }
                    return;
                }

                hwTargetHotbarSlot = hwOldHotbarSlot;
                hwSwappedFromSlot = invSlot;
                hwRestoreSwappedSlot = InventoryActionUtil.swapWithHotbar(invSlot, hwOldHotbarSlot);
                if (!hwRestoreSwappedSlot) {
                    if (hwBypass.get()) {
                        MoveComponent.stop = false;
                        MoveComponent.stopTicks = 0;
                    }
                    resetHWState();
                    return;
                }
                hwState = HWState.USE;
                hwActionStartTime = System.currentTimeMillis();
            }
        }
    }

    private void resetHWState() {
        if (hwRestoreSwappedSlot && hwSwappedFromSlot != -1 && hwTargetHotbarSlot >= 0) {
            if (hwSwappedFromSlot < 9) {
                InventoryActionUtil.swapHotbarSlots(hwTargetHotbarSlot, hwSwappedFromSlot);
            } else {
                InventoryActionUtil.swapWithHotbar(hwSwappedFromSlot, hwTargetHotbarSlot);
            }
        }
        hwState = HWState.IDLE;
        hwCurrentItem = null;
        hwOldHotbarSlot = -1;
        hwTargetHotbarSlot = -1;
        hwSwappedFromSlot = -1;
        hwRestoreSwappedSlot = false;
        if (hwBypass.get()) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
        }
    }

    private int findItemSlot(String nameSubstring, Item fallbackItem, boolean inHotbar) {
        int start = inHotbar ? 0 : 9;
        int end = inHotbar ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    return i;
                }
            }

            if (fallbackItem != null && stack.isOf(fallbackItem)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
    }

    private String decodeMojibake(String value) {
        try {
            return new String(value.getBytes(java.nio.charset.Charset.forName("windows-1251")), java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    @Override
    public void onDisable() {
        state = 0;
        pendingItem = null;
        for (int i = 0; i < wasPressed.length; i++) {
            wasPressed[i] = false;
        }

        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;

        if (hwState != HWState.IDLE && hwBypass.get()) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
        }
        resetHWState();

        super.onDisable();
    }
}
