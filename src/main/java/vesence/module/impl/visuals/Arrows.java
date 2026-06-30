package vesence.module.impl.visuals;

import java.awt.Color;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.friends.FriendStorage;
import vesence.utils.other.Mathf;
import vesence.utils.player.MoveUtil;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.ScaledResolution;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.Direction;
import vesence.utils.render.math.animation.impl.SmoothStepAnimation;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

@IModule(name = "Pointers", description = "Стрелки-указатели на игроков за пределами экрана", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Arrows extends vesence.module.api.Module {
   private static final Identifier ARROW_TEXTURE = Identifier.of("vesence", "/textures/arrows/arrows1.png");
   public static BooleanSetting mode3d = new BooleanSetting("3D Mode", true);
   public static BooleanSetting showDistance = new BooleanSetting("Отображать дистанцию", true);
   public static SliderSetting sizeSetting = new SliderSetting("Размер", 1.0f, 0.1f, 3.0f, 0.05f, false);
   public static SliderSetting radiusSetting = new SliderSetting("Отдаление", 100.0f, 10.0f, 300.0f, 5.0f, true);
   public ArrayList<Arrows.ArrowsPlayer> arrowsPlayers = new ArrayList<>();

   public Arrows() {
      this.addSettings(new Setting[] { mode3d, showDistance, sizeSetting, radiusSetting });
   }

   @EventInit
   public void onRender(EventScreen e) {
      if (mc.player != null || mc.world != null) {
         for (Entity entity : mc.world.getPlayers()) {
            if (entity != mc.player) {
               boolean alreadyExists = false;

               for (Arrows.ArrowsPlayer arrowsPlayer : this.arrowsPlayers) {
                  if (arrowsPlayer.entity == entity) {
                     alreadyExists = true;
                     break;
                  }
               }

               if (!alreadyExists) {
                  this.arrowsPlayers.add(new Arrows.ArrowsPlayer(entity));
               }
            }
         }

         for (Arrows.ArrowsPlayer arrowsPlayerx : this.arrowsPlayers) {
            arrowsPlayerx.render(e);
         }

         this.arrowsPlayers.removeIf(
               arrow -> arrow.animation.getDirection() != Direction.FORWARDS && arrow.animation.getOutput() == 0.0F);
      }
   }

   @Environment(EnvType.CLIENT)
   public static class ArrowsPlayer {
        Animation animation = new SmoothStepAnimation(500, 1.0);
      Entity entity;
      float animationStep;
      float lastYaw;
      float lastPitch;
      float animatedYaw;
      float animatedPitch;
      float yaw;

      public ArrowsPlayer(Entity entity) {
         this.entity = entity;
      }

      public void update() {
         boolean entityExists = mc.world.getPlayers().contains(this.entity);
         boolean isInWorld = this.entity.isAlive();
         this.animation
               .setDirection(
                     entityExists && isInWorld && this.entity != mc.player && this.entity != AttackAura.target
                           ? Direction.FORWARDS
                           : Direction.BACKWARDS);
      }

      public void render(EventScreen event) {
         Renderer2D render = event.renderer();
         this.update();
         float[] movement = MoveUtil.getMovementFromKeys();
         float forward = movement[0];
         float strafe = movement[1];
         this.animatedYaw = Mathf.fast(this.animatedYaw, strafe * 10.0F, 5.0F);
         this.animatedPitch = Mathf.fast(this.animatedPitch, forward * 10.0F, 5.0F);
         float realYaw = FreeLookUtil.active ? mc.gameRenderer.getCamera().getYaw() : FreeLookUtil.freeYaw;
         this.yaw = Mathf.fast(this.yaw, realYaw, 10.0F);
         float size = this.animation.getOutput() * Arrows.radiusSetting.get().floatValue();
         if (mc.currentScreen instanceof GenericContainerScreen) {
            size += 200.0F;
         }

         if (mc.currentScreen instanceof InventoryScreen) {
            size += 150.0F;
         }

         if (isMoving() || mc.player.isInSneakingPose() || mc.player.isSwimming()
               || mc.currentScreen instanceof ChatScreen) {
            size += 20.0F;
         }

         size *= ScaledResolution.getScaleFactor();

         this.animationStep = Mathf.fast(this.animationStep, size, 6.0F);
         double x = this.entity.lastX
               + (this.entity.getX() - this.entity.lastX) * mc.gameRenderer.getCamera().getLastTickProgress()
               - mc.gameRenderer.getCamera().getCameraPos().x;
         double y = this.entity.lastY
               + (this.entity.getY() - this.entity.lastY) * mc.gameRenderer.getCamera().getLastTickProgress()
               + this.entity.getHeight() / 2.0F
               - mc.gameRenderer.getCamera().getCameraPos().y
               - mc.player.getEyeHeight(mc.player.getPose());
         double z = this.entity.lastZ
               + (this.entity.getZ() - this.entity.lastZ) * mc.gameRenderer.getCamera().getLastTickProgress()
               - mc.gameRenderer.getCamera().getCameraPos().z;
         double distance = Math.sqrt(x * x + y * y + z * z);
         double cos = (float) Math.cos((float) (this.yaw * (Math.PI / 180.0)));
         double sin = (float) Math.sin((float) (this.yaw * (Math.PI / 180.0)));
         double rotatateYaw = -(z * cos - x * sin);
         double rotatatePitch = -(x * cos + z * sin);
         double angle = Math.atan2(rotatateYaw, rotatatePitch) * 180.0 / Math.PI;
         double distanceFactor = Math.min(1.0, distance / 20.0);

         double xOffset = this.animationStep * (float) Math.cos((float) Math.toRadians(angle));
         double yOffset = this.animationStep * (float) Math.sin((float) Math.toRadians(angle));

         double xPos = xOffset + event.viewportWidth() / 2.0;
         double yPos = yOffset + event.viewportHeight() / 2.0;

         xPos += this.animatedYaw * ScaledResolution.getScaleFactor();
         yPos += (this.animatedPitch + distanceFactor) * ScaledResolution.getScaleFactor();
         Identifier texture = ARROW_TEXTURE;
         if (mc.getTextureManager().getTexture(texture).getGlTexture() instanceof GlTexture glTexture) {
            int id = glTexture.getGlId();
            if (id > 0) {
                int color = this.entity instanceof AbstractClientPlayerEntity p
                      && FriendStorage.isFriend(p.getNameForScoreboard()) ? ColorUtil.GREEN : ColorUtil.fade();
                float animProgress = this.animation.getOutput();
                Color c1 = Renderer2D.ColorUtil.getColor(
                      Renderer2D.ColorUtil.swapAlpha(Renderer2D.ColorUtil.getMainColor(1, 1),
                            animProgress * 50.0F));
                float pulse = 1.0F;
                if (this.animation.getDirection() == Direction.FORWARDS && animProgress >= 0.95F) {
                   pulse = 1.0F + 0.04F * (float) Math.sin(System.currentTimeMillis() / 500.0 * Math.PI * 2.0);
                }
                float displayAlpha = Math.min(1.0F, animProgress * pulse);

                float texSize = 32.0f * Arrows.sizeSetting.get().floatValue();
                float texOffset = -texSize / 2.0f;

                if (Arrows.mode3d.get()) {
                    float currentPitch = 60.0f;
                    float pitchRad = (float) Math.toRadians(currentPitch);
                    float faceYScale = Math.max(0.35f, Math.abs((float) Math.cos(pitchRad)));

                    float maxExtrusion = 8.0f * Arrows.sizeSetting.get().floatValue();
                    float extrusionOffset = (float) Math.sin(pitchRad) * maxExtrusion;

                    int extrusionLayers = 12;
                    if (Math.abs(extrusionOffset) > 0.3f) {
                        for (int i = extrusionLayers; i >= 1; i--) {
                            float t = (float) i / (float) extrusionLayers;
                            float layerY = extrusionOffset * t;

                            int sideColor = Renderer2D.ColorUtil.multDark(color, 0.15f + 0.25f * (1.0f - t));

                            render.pushTranslation((float) xPos, (float) yPos + layerY);
                            render.pushScale(1.0f, faceYScale);
                            render.pushRotation((float) (angle - 90.0));
                            render.pushAlpha(displayAlpha);
                            render.drawRgbaTexture(id, texOffset, texOffset, texSize, texSize, sideColor, false);
                            render.popAlpha();
                            render.popTransform();
                            render.popTransform();
                            render.popTransform();
                        }
                    }

                    render.pushTranslation((float) xPos, (float) yPos);
                    render.pushScale(1.0f, faceYScale);
                    render.pushRotation((float) (angle - 90.0));
                    render.shadow(0.5F, -1.0F, 0.1F, 0.1F, 5.0F, 8.0F, 0.1F, c1.getRGB());
                    render.popTransform();
                    render.popTransform();
                    render.popTransform();

                    drawBloom(render, id, (float) xPos, (float) yPos, texOffset, texSize,
                              (float) (angle - 90.0), faceYScale, color, displayAlpha, true);

                    render.pushTranslation((float) xPos, (float) yPos);
                    render.pushScale(1.0f, faceYScale);
                    render.pushRotation((float) (angle - 90.0));
                    render.pushAlpha(displayAlpha);
                    render.drawRgbaTexture(id, texOffset, texOffset, texSize, texSize, color, false);
                    render.popAlpha();
                    render.popTransform();
                    render.popTransform();
                    render.popTransform();
                } else {
                    render.pushTranslation((float) xPos, (float) yPos);
                    render.pushRotation((float) (angle - 90.0));
                    render.shadow(0.5F, -1.0F, 0.1F, 0.1F, 5.0F, 8.0F, 0.1F, c1.getRGB());

                    render.popTransform();
                    render.popTransform();

                    drawBloom(render, id, (float) xPos, (float) yPos, texOffset, texSize,
                              (float) (angle - 90.0), 1.0f, color, displayAlpha, false);

                    render.pushTranslation((float) xPos, (float) yPos);
                    render.pushRotation((float) (angle - 90.0));
                    render.pushAlpha(displayAlpha);
                    render.drawRgbaTexture(id, texOffset, texOffset, texSize, texSize, color, false);
                    render.popAlpha();
                    render.popTransform();
                    render.popTransform();
                }

                if (Arrows.showDistance.get() && displayAlpha > 0.05f) {
                   String distText = String.format("%.1f", distance);
                   FontObject font = FontRegistry.SF_MEDIUM;
                   float textWidth = render.measureText(font, distText, 14.0f).width;
                   render.pushAlpha(displayAlpha);
                   render.text(font, (float)xPos - textWidth / 2.0f, (float)yPos + texSize / 2.0f + 5.0f, 14.0f, distText, -1);
                   render.popAlpha();
                }

               this.lastYaw = FreeLookUtil.active ? mc.gameRenderer.getCamera().getYaw() : FreeLookUtil.freeYaw;
               this.lastPitch = FreeLookUtil.active ? mc.gameRenderer.getCamera().getPitch()
                     : FreeLookUtil.freePitch;
            }
         }
      }

      public static boolean isMoving() {
         float[] movement = MoveUtil.getMovementFromKeys();
         return movement[0] != 0.0F || movement[1] != 0.0F;
      }

      private static void drawBloom(Renderer2D render, int id, float xPos, float yPos,
                                     float texOffset, float texSize, float rotation,
                                     float yScale, int color, float displayAlpha, boolean is3d) {

         int[] sizes = {1, 2, 3, 5, 8};
         float[] alphas = {0.35f, 0.25f, 0.18f, 0.12f, 0.07f};

         long pulseTime = System.currentTimeMillis();
         float pulse = 0.85f + 0.15f * (float) Math.sin(pulseTime / 400.0 * Math.PI * 2.0);

         for (int i = 0; i < sizes.length; i++) {
            float scale = 1.0f + sizes[i] * 0.15f;
            float layerSize = texSize * scale;
            float layerOffset = -layerSize / 2.0f;
            float layerAlpha = alphas[i] * displayAlpha * pulse;
            int bloomColor = Renderer2D.ColorUtil.swapAlpha(color, 255);

            render.pushTranslation(xPos, yPos);
            if (is3d) render.pushScale(1.0f, yScale);
            render.pushRotation(rotation);
            render.pushAlpha(layerAlpha);
            render.drawRgbaTexture(id, layerOffset, layerOffset, layerSize, layerSize, bloomColor, false);
            render.popAlpha();
            render.popTransform();
            if (is3d) render.popTransform();
            render.popTransform();
         }
      }
   }
}
