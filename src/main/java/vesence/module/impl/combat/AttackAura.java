package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.EventInput;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.Attack;
import vesence.module.impl.combat.auraComponent.AuraUtil;
import vesence.module.impl.combat.auraComponent.Rotate;
import vesence.module.impl.combat.auraComponent.neural.ExpertCapture;
import vesence.module.impl.combat.auraComponent.neural.HumanizedRotation;
import vesence.module.impl.combat.rotation.*;
import vesence.renderengine.utils.MathHelper;
import vesence.utils.player.InventoryActionUtil;
import vesence.utils.player.MoveUtil;
import vesence.utils.player.MovementManager;
import vesence.utils.render.math.animation.anim2.Interpolator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "AttackAura",
        description = "Атакует сущностей в радиусе атаки",
        category = Category.COMBAT,
        bind = 82
)
@Environment(EnvType.CLIENT)
public class AttackAura extends Module {
    public static SliderSetting attackRange = new SliderSetting("Радиус атаки", 3.0F, 3.0F, 6.0F, 0.1F, false);
    public static SliderSetting preRange = new SliderSetting("Радиус обнаружения", 1.0F, 0.0F, 5.0F, 0.1F, false);
    public static ModeSetting rotationType = new ModeSetting("Режим ротации", "SpookyTime", "Neural", "FunTime", "SpookyTime", "Fast", "LegitSnap", "Legit");
    public static ModeSetting neuralMode = new ModeSetting("Режим Neural", "Active", "Active", "Capture").hidden(() -> !rotationType.is("Neural"));

    public static BooleanSetting showFov = new BooleanSetting("Отображать Fov", true).hidden(() -> !rotationType.is("LegitSnap"));
    public static SliderSetting fovValue = new SliderSetting("Fov", 40.0F, 10.0F, 90, 1.0F, true).hidden(() -> !rotationType.is("LegitSnap"));
    public static ModeSetting snapSetting = new ModeSetting("Режим снапа", "Fast", "Fast", "Smooth", "Random").hidden(() -> !rotationType.is("Snap"));
    public static MultiBooleanSetting targets = new MultiBooleanSetting(
            "Цели", new BooleanSetting("Игроки", true), new BooleanSetting("Голые", true), new BooleanSetting("Мобы", true)
    );

    public static MultiBooleanSetting misc = new MultiBooleanSetting(
            "Проверки до удара",
            new BooleanSetting("Бить через блоки", false),
            new BooleanSetting("Только с оружием", false),
            new BooleanSetting("Не бить если ешь", true),
            new BooleanSetting("Не бить в гуишках", false),
            new BooleanSetting("Пробивать щит", false),
            new BooleanSetting("Отжимать щит", false)
    );
    public static MultiBooleanSetting extraSettings = new MultiBooleanSetting(
            "Доп.настройка",
            new BooleanSetting("Сброс спринта", true),
            new BooleanSetting("Умные криты", false));
    public static ModeSetting motion = new ModeSetting("Режим движения", "Обычное", "Обычное", "Свободная", "Преследование");

    public static BooleanSetting autoDamageOrb = new BooleanSetting("Авто-сфера урона", false);

    public long lastLookUpTime = 0;
    public long nextLookUpDelay = ThreadLocalRandom.current().nextLong(36500L, 39200L);
    public boolean isLookingUp = false;
    public long lookUpStartTime = 0;
    public int lookUpDuration = 0;
    public static LivingEntity target;
    public static int count;
    private static final double[][] SPOOKY_PRESET = {
            {0.0, 0.94, 0.0}, {0.0, 0.86, 0.0}, {0.0, 0.78, 0.0}, {0.0, 0.7, 0.0},
            {0.0, 0.62, 0.0}, {0.0, 0.54, 0.0}, {0.0, 0.46, 0.0},
            {0.24, 0.86, 0.0}, {-0.24, 0.86, 0.0}, {0.0, 0.86, 0.24}, {0.0, 0.86, -0.24},
            {0.24, 0.78, 0.24}, {0.24, 0.78, -0.24}, {-0.24, 0.78, 0.24}, {-0.24, 0.78, -0.24},
            {0.36, 0.7, 0.0}, {-0.36, 0.7, 0.0}, {0.0, 0.7, 0.36}, {0.0, 0.7, -0.36},
            {0.22, 0.62, 0.22}, {0.22, 0.62, -0.22}, {-0.22, 0.62, 0.22}, {-0.22, 0.62, -0.22}
    };

    private ItemStack savedOffhand = ItemStack.EMPTY;
    private boolean orbSwapped = false;
    private int orbReturnTicks = 0;
    private int bestOrbSlot = -1;

    public AttackAura() {
        this.addSettings(new Setting[]{
                attackRange, preRange, rotationType, neuralMode, showFov, fovValue,
                snapSetting, targets, misc, extraSettings, motion,
                autoDamageOrb
        });
    }

    @EventInit
    public void onRender2D(EventScreen event) {
        if (!this.enable || !rotationType.is("LegitSnap") || !showFov.get()) return;
        if (mc.player == null || mc.world == null) return;

        float sw = event.viewportWidth() / 2.0f;
        float sh = event.viewportHeight() / 2.0f;

        float fov = mc.options.getFov().getValue().floatValue();
        float baseFovValue = fovValue.get().floatValue();

        float visualFov = baseFovValue;

        float radius = (float) (Math.tan(Math.toRadians(visualFov / 2.0)) / Math.tan(Math.toRadians(fov / 2.0)) * (event.viewportHeight() / 2.0f));

        event.renderer().circleOutline(sw, sh, radius, 0.0f, 1.0f, 0x80FFFFFF, 1.0f);
    }


    private double getDamageValue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        final double[] damage = {0.0};
        try {
            stack.applyAttributeModifiers(EquipmentSlot.OFFHAND, (attribute, modifier) -> {
                if (attribute == EntityAttributes.ATTACK_DAMAGE) {
                    damage[0] += modifier.value();
                }
            });
        } catch (Exception ignored) {
        }
        return damage[0];
    }

    private int findBestDamageOrbSlot() {
        double currentDamage = getDamageValue(mc.player.getOffHandStack());
        int bestSlot = -1;
        double bestDamage = currentDamage;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            double dmg = getDamageValue(stack);
            if (dmg > bestDamage) {
                bestDamage = dmg;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void swapToDamageOrb(int slot) {
        if (slot < 0 || slot >= 36) return;
        savedOffhand = mc.player.getOffHandStack().copy();
        bestOrbSlot = slot;

        if (slot < 9) {
            InventoryActionUtil.swapHotbarWithOffhandPacket(slot);
        } else {
            InventoryActionUtil.swapWithOffhand(slot);
        }
        orbSwapped = true;
    }

    private void returnOrb() {
        if (!orbSwapped) return;
        if (bestOrbSlot >= 0) {
            if (bestOrbSlot < 9) {
                InventoryActionUtil.swapHotbarWithOffhandPacket(bestOrbSlot);
            } else {
                InventoryActionUtil.swapWithOffhand(bestOrbSlot);
            }
        }
        orbSwapped = false;
        bestOrbSlot = -1;
        savedOffhand = ItemStack.EMPTY;
    }

    @EventInit
    public void onEvent(ClientTickEvent e) {
        if (!this.enable) {
            if (orbSwapped) returnOrb();
            if ("Neural".equals(rotationType.get()) && HumanizedRotation.isReturning()) {
                HumanizedRotation.tickReturn();
            } else if ("SpookyTime".equals(rotationType.get())) {
                USpookyTimeRotation.smoothBack();
            }
            return;
        }

        boolean capture = "Neural".equals(rotationType.get()) && "Capture".equals(neuralMode.get());
        HumanizedRotation.setCaptureMode(capture);
        if (target != null && mc.player != null && mc.world != null && this.isValidTarget(target)) {
            this.updateRotation();
        } else if ("Neural".equals(rotationType.get()) && !capture) {
            if (!HumanizedRotation.isReturning() && !HumanizedRotation.isStopped()) {
                HumanizedRotation.stopSmooth();
            }
            if (HumanizedRotation.isReturning()) {
                HumanizedRotation.tickReturn();
            }
        } else if ("SpookyTime".equals(rotationType.get())) {
            USpookyTimeRotation.smoothBack();
        }
    }

    @EventInit
    public void onEvent(EventUpdate e) {
        if (!this.enable) {
            return;
        }

        if (!mc.player.isAlive()) {
            if (orbSwapped) returnOrb();
            this.toggle();
            return;
        }

        if (orbSwapped && orbReturnTicks > 0) {
            orbReturnTicks--;
            if (orbReturnTicks <= 0) {
                returnOrb();
            }
        }

        if (target == null || !this.isValidTarget(target)) {
            UFastRotations.state();
            if (orbSwapped) returnOrb();
            this.updateTarget();
        }

        if (target != null && mc.player != null && mc.world != null) {
            USpookyTimeRotation.state();

            if (motion.is("Преследование")) {
                MoveUtil.targetMovement(mc.player.getYaw(), new Vec3d(target.getX(), target.getY(), target.getZ()));
            }

            if (!this.checkToAttack()) {
                this.attackEntity();
            }

            float[] ranges = getRanges();
        } else {
            this.reset();
        }
    }

    public static float[] getRanges() {
        return new float[]{attackRange.get().floatValue(), preRange.get().floatValue()};
    }

    public boolean isRayCastRuleToAttack() {
        return true;
    }

    public void attackEntity() {
        if (autoDamageOrb.get() && !orbSwapped) {
            int orbSlot = findBestDamageOrbSlot();
            if (orbSlot >= 0) {
                swapToDamageOrb(orbSlot);
            }
        }

        if (!(AuraUtil.getStrictDistance(target) >= attackRange.get())) {
            float[] ranges = getRanges();
            ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};

            boolean canPacket = !mc.player.hasStatusEffect(StatusEffects.BLINDNESS) && !mc.player.isFlyingVehicle() && !extraSettings.get("Легитный спринт");
            if (target != null) {
                boolean canAttack = Attack.shouldAttack(target, this.isRayCastRuleToAttack(), true, true, 0L, ranges);
                if (canAttack) {
                    Runnable[] shieldBreak = Attack.hitShieldBreakTaskForUse(target, misc.get("Автоматичиски ломать щит"));
                    Runnable[] shieldPressBypass = Attack.resetShieldSilentTaskForUse(true);
                    Runnable[] skipSilentSprint = Attack.skipSilentSprintingTaskForUse(canPacket);
                    Runnable preHitSendCodeSingleTick = () -> {
                        skipSilentSprint[0].run();
                        shieldPressBypass[0].run();
                        shieldBreak[0].run();
                    };
                    Runnable postHitSendCodeSingleTick = () -> {
                        shieldBreak[1].run();
                        shieldPressBypass[1].run();
                        skipSilentSprint[1].run();
                    };
                    if (misc.get("Отжимать щит при ударе") && mc.player.getActiveItem().getItem().equals(Items.SHIELD) && mc.player.isUsingItem()
                    )
                    {
                        mc.interactionManager.stopUsingItem(mc.player);
                    }
                    Attack.useEntity(target, preHitSendCodeSingleTick, postHitSendCodeSingleTick, Hand.MAIN_HAND, true);

                    if (orbSwapped) {
                        int delay = 0;
                        if (delay <= 0) {
                            returnOrb();
                        } else {
                            orbReturnTicks = delay;
                        }
                    }
                }
            }
        }
    }

    @EventInit
    public void onMoveKeys(EventInput event) {
        if (this.enable && motion.is("Свободная") && target != null && mc.player != null) {
            float cameraYaw = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());
            if (mc.options.getPerspective().isFrontView()) {
                cameraYaw += 180.0F;
            }
            MoveUtil.fixMovement(event, cameraYaw);
        }

        if (Attack.resetSprintTick(target, getRanges()) && !mc.player.isOnGround()) {
            event.setStrafe(0.0F);
            event.setForward(0.0F);
            if (mc.options != null && mc.options.sprintKey != null) {
                mc.options.sprintKey.setPressed(false);
            }
            if (mc.player != null) {
                mc.player.setSprinting(false);
            }
        }
    }

    private Vec3d getAttackPoint(LivingEntity target, boolean isFunTime) {
        if (target == null) return null;

        Box box = target.getBoundingBox();
        Vec3d center = box.getCenter();
        Vec3d eyePos = mc.player.getEyePos();
        float range = attackRange.get().floatValue() + preRange.get().floatValue();
        boolean ignoreWalls = misc.get("Бить через блоки");

        List<Vec3d> candidates = new ArrayList<>();

        if (isFunTime) {
            double step = Math.max(box.getLengthY() / 10.0, 0.1);
            for (double y = box.minY; y <= box.maxY + 1e-4; y += step) {
                Vec3d p = new Vec3d(center.x, y, center.z);
                if (isValidPoint(p, range, ignoreWalls)) candidates.add(p);
            }
        } else {
            double halfX = box.getLengthX() * 0.5;
            double halfZ = box.getLengthZ() * 0.5;
            double heightY = box.getLengthY();

            for (double[] preset : SPOOKY_PRESET) {
                Vec3d p = new Vec3d(
                        center.x + halfX * preset[0],
                        box.minY + heightY * preset[1],
                        center.z + halfZ * preset[2]
                );
                if (isValidPoint(p, range, ignoreWalls)) candidates.add(p);
            }
        }

        if (candidates.isEmpty()) return target.getEyePos();

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        return candidates.stream()
                .min(Comparator.comparingDouble(p -> {
                    Vec3d dir = p.subtract(eyePos);
                    float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    float pitch = (float) Math.toDegrees(-Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));
                    float dy = net.minecraft.util.math.MathHelper.wrapDegrees(yaw - currentYaw);
                    float dp = pitch - currentPitch;
                    return Math.hypot(dy, dp);
                }))
                .orElse(target.getEyePos());
    }

    private boolean isValidPoint(Vec3d point, float range, boolean ignoreWalls) {
        Vec3d eyePos = mc.player.getEyePos();
        if (eyePos.distanceTo(point) > range) return false;
        if (!ignoreWalls) {
            return mc.world.raycast(new RaycastContext(
                    eyePos, point,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            )).getType() == HitResult.Type.MISS;
        }
        return true;
    }

    private void updateRotation() {
        float[] ranges = getRanges();
        ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
        boolean canAttack = Attack.shouldAttack(target, false, true, true, 0L, ranges);
        String mode = rotationType.get();

        Vec3d point = null;
        if ("FunTime".equals(mode) || "SpookyTime".equals(mode)) {
            point = getAttackPoint(target, "FunTime".equals(mode));
        }

        switch (mode) {
            case "Neural":
                if ("Capture".equals(neuralMode.get())) {
                    HumanizedRotation.compute(target, canAttack);
                    return;
                }
                Rotate.onNeuralRotation(target, canAttack);
                break;
            case "FunTime":
                UFunTimeRotations.rotation(target, canAttack);
                break;
            case "SpookyTime":
                USpookyTimeRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "HolyWorld":
                UHolyWorldRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "SlothAc":
                USlothACRotations.rotation(target, canAttack, attackRange.get().floatValue() + preRange.get().floatValue());
                break;
            case "Fast":
                UFastRotations.rotation(target, canAttack);
                break;
            case "LegitSnap":
                ULegitSnapRotation.rotation(target, canAttack);
                break;
        }
    }

    private void updateTarget() {
        LivingEntity bestTarget = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d eyePos = mc.player.getEyePos();
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();
        Vec3d lookVec = Vec3d.fromPolar(camPitch, camYaw).normalize();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living && this.isValidTarget(living)) {
                Vec3d targetPos = new Vec3d(living.getX(), living.getY(), living.getZ()).add(0.0, living.getHeight() * 0.5, 0.0);
                Vec3d toTarget = targetPos.subtract(eyePos).normalize();
                double angle = Math.acos(MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0));

                if (angle < bestAngle) {
                    bestAngle = angle;
                    bestTarget = living;
                }
            }
        }

        target = bestTarget;
    }

    private float auraDist() {
        return attackRange.get().floatValue() + preRange.get().floatValue();
    }

    private boolean isValidTarget(LivingEntity entity) {
        return isValidTarget(entity, false);
    }

    private boolean isValidTarget(LivingEntity entity, boolean ignoreDist) {

        if (entity instanceof ClientPlayerEntity) {
            return false;
        } else if (!ignoreDist && mc.player.distanceTo(entity) > this.auraDist()) {
            return false;
        } else if (!misc.get("Бить через блоки") && !mc.player.canSee(entity)) {
            return false;
        } else if (entity instanceof PlayerEntity p && NoFriendDamage.isFriend(p)) {
            return false;
        } else if (entity instanceof PlayerEntity && !targets.get("Игроки")) {
            return false;
        } else if (entity instanceof PlayerEntity && entity.getArmor() == 0 && !targets.get("Голые")) {
            return false;
        } else if (entity instanceof PlayerEntity && ((PlayerEntity)entity).isCreative()) {
            return false;
        } else if (entity instanceof PlayerEntity && AntiBot.isBot(entity)) {
            return false;
        } else {
            return (entity instanceof Monster || entity instanceof SlimeEntity || entity instanceof VillagerEntity || entity instanceof AnimalEntity)
                    && !targets.get("Мобы")
                    ? false
                    : !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
        }
    }

    @Override
    public void toggle() {
        super.toggle();
        if (this.enable && "Neural".equals(rotationType.get())) {
            ExpertCapture.setActiveServerId(ExpertCapture.currentServerId());
            HumanizedRotation.initBrain(ExpertCapture.currentServerId());
        }
        this.reset();
    }

    private void reset() {
        target = null;
        if (orbSwapped) returnOrb();
        if (mc.player != null) {
            isLookingUp = false;
            lookUpStartTime = 0;
        }
        MovementManager.getInstance().unlockMovement("AttackAura");
    }

    public static float randomLerp(float min, float max) {
        return Interpolator.lerp(max, min, new SecureRandom().nextFloat());
    }

    private boolean checkToAttack() {
        return mc.player.isUsingItem() && misc.get("Не бить если ешь") && !(mc.player.getActiveItem().getItem() instanceof ShieldItem)
                || mc.currentScreen != null && misc.get("Не бить в гуишках")
                || !mc.player.getMainHandStack().isIn(ItemTags.SWORDS)
                && !mc.player.getMainHandStack().isIn(ItemTags.AXES)
                && misc.get("Только с оружием");
    }

    @Override
    public void onDisable() {
        if (orbSwapped) returnOrb();
        if ("Neural".equals(rotationType.get())) {
            HumanizedRotation.stopSmooth();
        }
        HumanizedRotation.setCaptureMode(false);
        super.onDisable();
    }
}