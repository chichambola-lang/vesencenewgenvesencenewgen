package vesence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.render.GlassHandsRenderEvent;
import vesence.event.render.HandAnimationEvent;
import vesence.event.render.HandOffsetEvent;
import vesence.event.render.HandShadowRenderEvent;
import vesence.event.render.ItemTransformEvent;
import vesence.event.render.RenderItemEvent;
import vesence.module.impl.movement.FreecamState;
import vesence.module.impl.visuals.CustomHand;

@Environment(EnvType.CLIENT)
@Mixin({HeldItemRenderer.class})
public abstract class HeldItemRendererMixin {
   @Shadow
   private ItemStack mainHand;
   @Shadow
   private ItemStack offHand;
   @Shadow
   private float equipProgressMainHand;
   @Shadow
   private float lastEquipProgressMainHand;
   @Shadow
   private float equipProgressOffHand;
   @Shadow
   private float lastEquipProgressOffHand;

   @Inject(
           method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
           at = {@At("HEAD")},
           cancellable = true
   )
   private void onRenderItemHead(CallbackInfo ci) {
      if (FreecamState.pos != null) {
         ci.cancel();
      }
   }

   @Inject(
           method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
           at = {@At("HEAD")}
   )
   private void onRenderItemPre(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, ClientPlayerEntity player, int light, CallbackInfo ci) {
      if (FreecamState.pos == null && CustomHand.glassShouldRender()) {
         EventManager.call(new GlassHandsRenderEvent(GlassHandsRenderEvent.Phase.PRE, matrices, tickProgress));
      }
      if (FreecamState.pos == null && CustomHand.shadowShouldRender()) {
         EventManager.call(new HandShadowRenderEvent(HandShadowRenderEvent.Phase.PRE, matrices, tickProgress));
      }
   }

   @Inject(
           method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
           at = {@At("TAIL")}
   )
   private void onRenderItemPost(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, ClientPlayerEntity player, int light, CallbackInfo ci) {
      if (FreecamState.pos == null && CustomHand.glassShouldRender()) {
         EventManager.call(new GlassHandsRenderEvent(GlassHandsRenderEvent.Phase.POST, matrices, tickProgress));
      }
      if (FreecamState.pos == null && CustomHand.shadowShouldRender()) {
         EventManager.call(new HandShadowRenderEvent(HandShadowRenderEvent.Phase.POST, matrices, tickProgress));
      }
   }

   @Inject(
           method = {"renderFirstPersonItem"},
           at = {@At("HEAD")}
   )
   private void onRenderFirstPersonItem(
           AbstractClientPlayerEntity player,
           float tickProgress,
           float pitch,
           Hand hand,
           float swingProgress,
           ItemStack stack,
           float equipProgress,
           MatrixStack matrices,
           OrderedRenderCommandQueue queue,
           int light,
           CallbackInfo ci
   ) {
      RenderItemEvent renderItemEvent = new RenderItemEvent(matrices, hand);
      EventManager.call(renderItemEvent);
   }

   @Redirect(
           method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F"
           )
   )
   private float redirectGetYaw(ClientPlayerEntity instance, float tickProgress) {
      return MinecraftClient.getInstance().gameRenderer.getCamera().getYaw();
   }

   @Redirect(
           method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F"
           )
   )
   private float redirectGetPitch(ClientPlayerEntity instance, float tickProgress) {
      return MinecraftClient.getInstance().gameRenderer.getCamera().getPitch();
   }

   @Inject(
           method = {"renderFirstPersonItem"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                   shift = At.Shift.AFTER
           )}
   )
   private void handOffsetHook(
           AbstractClientPlayerEntity player,
           float tickProgress,
           float pitch,
           Hand hand,
           float swingProgress,
           ItemStack stack,
           float equipProgress,
           MatrixStack matrices,
           OrderedRenderCommandQueue queue,
           int light,
           CallbackInfo ci
   ) {
      HandOffsetEvent event = new HandOffsetEvent(matrices, stack, hand);
      EventManager.call(event);

      float scale = event.getScale();
      if (scale != 1.0F) {
         matrices.scale(scale, scale, scale);
      }
   }

   @Inject(
           method = {"renderFirstPersonItem"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"
           )},
           require = 0
   )
   private void itemTransformHook(
           AbstractClientPlayerEntity player,
           float tickProgress,
           float pitch,
           Hand hand,
           float swingProgress,
           ItemStack stack,
           float equipProgress,
           MatrixStack matrices,
           OrderedRenderCommandQueue queue,
           int light,
           CallbackInfo ci
   ) {
      EventManager.call(new ItemTransformEvent(matrices, stack, hand));
   }

   @WrapOperation(
           method = {"renderFirstPersonItem"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V"
           )}
   )
   private void handAnimationHook(
           HeldItemRenderer instance,
           float swingProgress,
           MatrixStack matrices,
           int armX,
           Arm arm,
           Operation<Void> original,
           @Local(ordinal = 0, argsOnly = true) AbstractClientPlayerEntity player,
           @Local(ordinal = 0, argsOnly = true) Hand hand
   ) {
      HandAnimationEvent event = new HandAnimationEvent(matrices, hand, swingProgress);
      EventManager.call(event);

      if (!event.isCancelled()) {
         original.call(instance, swingProgress, matrices, armX, arm);
      }
   }

   @WrapOperation(
           method = {"renderFirstPersonItem"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"
           )}
   )
   private void wrapApplyEquipOffset(
           HeldItemRenderer instance,
           MatrixStack matrices,
           Arm arm,
           float equipProgress,
           Operation<Void> original
   ) {
      CustomHand customHand = Vesence.get.getManager().get(CustomHand.class);
      if (customHand != null && customHand.isHideVanilla()) {
         equipProgress = 0.0F;
      }
      original.call(instance, matrices, arm, equipProgress);
   }
}
