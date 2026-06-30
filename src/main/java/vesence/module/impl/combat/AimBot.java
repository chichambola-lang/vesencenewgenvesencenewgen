package vesence.module.impl.combat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventLook;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.utils.render.ColorUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@IModule(name = "AimBot", description = "Авто-наведение для лука и арбалета", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public class AimBot extends Module {

   private static final Identifier CROSSHAIR_TEX = Identifier.of("vesence", "textures/world/dashbloom.png");

   private static final RenderPipeline CROSSHAIR_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "aimbot_crosshair"))
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.ADDITIVE)
         .build()
   );
   private static final RenderLayer CROSSHAIR_LAYER = RenderLayer.of(
      "aimbot_crosshair", RenderSetup.builder(CROSSHAIR_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", CROSSHAIR_TEX).build()
   );

   private final MultiBooleanSetting targetTypes = new MultiBooleanSetting("Типы целей",
      new BooleanSetting("Игроки", true),
      new BooleanSetting("В броне", true),
      new BooleanSetting("Без брони", false),
      new BooleanSetting("Мобы", false),
      new BooleanSetting("Зомби", false)
   );
   private final SliderSetting range = new SliderSetting("Дистанция", 40.0, 10.0, 100.0, 1.0, false);
   private final SliderSetting aimTime = new SliderSetting("Время наводки (тики)", 10.0, 0.0, 40.0, 1.0, false);
   private final BooleanSetting silentRotations = new BooleanSetting("Тихие повороты", true);
   private final BooleanSetting showCrosshair = new BooleanSetting("Показать прицел", true);
   private final SliderSetting crosshairSize = new SliderSetting("Размер прицела", 1.0, 0.3, 3.0, 0.1, false);

   private LivingEntity target = null;
   private boolean isAiming = false;
   private float aimProgress = 0F;
   private Rotation targetRotation = null;

   public AimBot() {
      this.addSettings(new Setting[]{targetTypes, range, aimTime, silentRotations, showCrosshair, crosshairSize});
   }

   private boolean isHoldingBowOrCrossbow() {
      ItemStack mainHand = mc.player.getMainHandStack();
      ItemStack offHand = mc.player.getOffHandStack();
      return mainHand.getItem() instanceof BowItem
         || mainHand.getItem() instanceof CrossbowItem
         || offHand.getItem() instanceof BowItem
         || offHand.getItem() instanceof CrossbowItem;
   }

   private boolean isUsingBowOrCrossbow() {
      return mc.player.isUsingItem() && this.isHoldingBowOrCrossbow();
   }

   private boolean isValidTarget(LivingEntity entity) {
      if (entity == mc.player) {
         return false;
      }
      if (!entity.isAlive() || entity.getHealth() <= 0) {
         return false;
      }

      if (entity instanceof PlayerEntity player) {
         if (!this.targetTypes.get("Игроки")) {
            return false;
         }
         if (NoFriendDamage.isFriend(entity)) {
            return false;
         }

         boolean hasArmor = false;
         for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
            net.minecraft.entity.EquipmentSlot.HEAD,
            net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS,
            net.minecraft.entity.EquipmentSlot.FEET
         }) {
            if (!player.getEquippedStack(slot).isEmpty()) {
               hasArmor = true;
               break;
            }
         }

         if (this.targetTypes.get("В броне") && hasArmor) {
            return true;
         }
         if (this.targetTypes.get("Без брони") && !hasArmor) {
            return true;
         }
         if (!this.targetTypes.get("В броне") && !this.targetTypes.get("Без брони")) {
            return true;
         }

         return false;
      }

      if (entity instanceof ZombieEntity) {
         return this.targetTypes.get("Зомби");
      }

      if (entity instanceof HostileEntity) {
         return this.targetTypes.get("Мобы");
      }

      return false;
   }

   private LivingEntity findBestTarget() {
      List<LivingEntity> targets = new ArrayList<>();

      float searchRange = this.range.get().floatValue();
      Box searchBox = mc.player.getBoundingBox().expand(searchRange);

      for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> true)) {
         if (!this.isValidTarget(entity)) {
            continue;
         }

         double dist = mc.player.distanceTo(entity);
         if (dist > searchRange) {
            continue;
         }

         targets.add(entity);
      }

      if (targets.isEmpty()) {
         return null;
      }

      targets.sort(Comparator.comparingDouble(entity -> mc.player.distanceTo(entity)));
      return targets.get(0);
   }

   private Rotation calculateBowRotation(LivingEntity target) {
      Vec3d eyes = mc.player.getEyePos();
      Vec3d targetPos = target.getBoundingBox().getCenter();

      double dx = targetPos.x - eyes.x;
      double dy = targetPos.y - eyes.y;
      double dz = targetPos.z - eyes.z;

      double distance = Math.sqrt(dx * dx + dz * dz);

      float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
      float pitch = (float) -Math.toDegrees(Math.atan2(dy, distance));

      return new Rotation(yaw, pitch);
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (!this.showCrosshair.get() || this.target == null || !this.isAiming) {
         return;
      }

      float partialTicks = event.getTickDelta();
      Vec3d targetPos = new Vec3d(
         MathHelper.lerp(partialTicks, this.target.lastRenderX, this.target.getX()),
         MathHelper.lerp(partialTicks, this.target.lastRenderY, this.target.getY()) + this.target.getHeight() / 2.0,
         MathHelper.lerp(partialTicks, this.target.lastRenderZ, this.target.getZ())
      );

      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cameraPos = camera.getCameraPos();
      MatrixStack matrices = event.getMatrixStack();

      double renderX = targetPos.x - cameraPos.x;
      double renderY = targetPos.y - cameraPos.y;
      double renderZ = targetPos.z - cameraPos.z;

      matrices.push();
      matrices.translate(renderX, renderY, renderZ);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

      float size = this.crosshairSize.get().floatValue() * 0.5F;
      int alpha = MathHelper.clamp((int) (255 * this.aimProgress), 0, 255);
      int color = ColorUtil.fade();
      int r = ColorUtil.red(color);
      int g = ColorUtil.green(color);
      int b = ColorUtil.blue(color);

      Matrix4f matrix = matrices.peek().getPositionMatrix();
      Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
      VertexConsumer buffer = immediate.getBuffer(CROSSHAIR_LAYER);

      buffer.vertex(matrix, -size, -size, 0).color(r, g, b, alpha).texture(0, 1);
      buffer.vertex(matrix, -size, size, 0).color(r, g, b, alpha).texture(0, 0);
      buffer.vertex(matrix, size, size, 0).color(r, g, b, alpha).texture(1, 0);
      buffer.vertex(matrix, size, -size, 0).color(r, g, b, alpha).texture(1, 1);

      immediate.draw();

      matrices.pop();
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      this.isAiming = this.isUsingBowOrCrossbow();

      if (this.isAiming) {
         LivingEntity newTarget = this.findBestTarget();

         if (newTarget != null) {
            if (this.target != newTarget) {
               this.target = newTarget;
               this.aimProgress = 0F;
            }

            Rotation newRotation = this.calculateBowRotation(this.target);

            float maxStep = 1F / Math.max(1F, this.aimTime.get().floatValue());
            this.aimProgress = Math.min(this.aimProgress + maxStep, 1F);

            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();
            float targetYaw = newRotation.yaw;
            float targetPitch = newRotation.pitch;

            float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float pitchDiff = targetPitch - currentPitch;

            float stepYaw = yawDiff * this.aimProgress;
            float stepPitch = pitchDiff * this.aimProgress;

            this.targetRotation = new Rotation(currentYaw + stepYaw, currentPitch + stepPitch);

            if (!this.silentRotations.get()) {
               mc.player.setYaw(this.targetRotation.yaw);
               mc.player.setPitch(this.targetRotation.pitch);
            }
         }
      } else {
         this.target = null;
         this.targetRotation = null;
         this.aimProgress = 0F;
      }
   }

   @EventInit
   public void onLook(EventLook event) {
      if (this.target != null && this.isAiming && this.targetRotation != null && this.silentRotations.get()) {
         event.setYaw(this.targetRotation.yaw);
         event.setPitch(MathHelper.clamp(this.targetRotation.pitch, -90.0F, 90.0F));
      }
   }

   public LivingEntity getTarget() {
      return this.target;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.target = null;
      this.isAiming = false;
      this.aimProgress = 0F;
      this.targetRotation = null;
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.target = null;
      this.isAiming = false;
      this.aimProgress = 0F;
      this.targetRotation = null;
   }
}
