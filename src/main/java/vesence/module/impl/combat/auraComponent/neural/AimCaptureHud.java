package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.text.FontRegistry;

import java.io.File;

@Environment(EnvType.CLIENT)
public final class AimCaptureHud {

   public AimCaptureHud() {
   }

   @EventInit
   public void onRender(EventScreen e) {
      if (e == null || e.renderer() == null) return;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null || mc.world == null) return;

      boolean recording = ExpertCapture.isRecording();
      String activeId = ExpertCapture.getActiveServerId();
      String currentId = ExpertCapture.currentServerId();
      File bcFile = ExpertCapture.brainFile(activeId);
      boolean hasBc = bcFile.exists();
      if (!hasBc) {
         File fallback = ExpertCapture.brainFile(currentId);
         if (fallback.exists()) {
            bcFile = fallback;
            activeId = currentId;
            hasBc = true;
         }
      }

      if (!recording) return;
      boolean showExtra = ExpertCapture.isDebug();
      int lineCount = showExtra ? 6 : 3;

      Renderer2D r = e.renderer();
      int width = e.viewportWidth();

      float boxW = 460.0F;
      float lineH = 22.0F;
      float padX = 16.0F;
      float padY = 12.0F;
      float boxH = padY * 2.0F + lineH * lineCount;
      float x = (width - boxW) * 0.5F;
      float y = 60.0F;

      r.shadow(x, y, boxW, boxH, 10.0F, 12.0F, 0.35F, 0x33000000);
      r.rect(x, y, boxW, boxH, 10.0F, 0x66000000);
      r.rectOutline(x - 0.5F, y - 0.5F, boxW + 1.0F, boxH + 1.0F, 10.0F,
         recording ? 0xAAFF3344 : 0xAA33DD66, 1.5F);

      float textY = y + padY;
      float textX = x + padX;

      if (recording) {
         int count = ExpertCapture.count();
         String recId = ExpertCapture.activeServer();
         r.text(FontRegistry.SF_SEMI, textX, textY, 20.0F,
            "REC ● " + count + " сэмплов → " + recId, 0xFFFF4444);
         r.text(FontRegistry.SF_MEDIUM, textX, textY + lineH, 15.0F,
            "Активный: " + activeId, 0xFFDDDDDD);

         if (ExpertCapture.ringHasData()) {
            float meanDy = ExpertCapture.ringMeanDy();
            float meanDp = ExpertCapture.ringMeanDp();
            float rawYaw = ExpertCapture.ringMeanRawYaw();
            float rawPitch = ExpertCapture.ringMeanRawPitch();
            int color = (Math.abs(meanDy) > 0.01f || Math.abs(meanDp) > 0.01f) ? 0xFF66FF66 : 0xFFFFAA33;
            r.text(FontRegistry.SF_MEDIUM, textX, textY + lineH * 2, 15.0F,
               "dy=" + String.format("%+.2f", meanDy) + "° dp=" + String.format("%+.2f", meanDp)
                  + "° | raw mouse=" + String.format("%+.2f", rawYaw) + "/" + String.format("%+.2f", rawPitch),
               color);
         } else {
            r.text(FontRegistry.SF_MEDIUM, textX, textY + lineH * 2, 15.0F,
               "Ждём первый записанный сэмпл…", 0xFFCCCCCC);
         }

         if (showExtra) {
            float yaw = mc.player.getYaw();
            float headYaw = mc.player.headYaw;
            float bodyYaw = mc.player.bodyYaw;
            r.text(FontRegistry.SF_MEDIUM, textX, textY + lineH * 3, 13.0F,
               String.format("yaw=%.2f headYaw=%.2f bodyYaw=%.2f", yaw, headYaw, bodyYaw), 0xFFBFBFBF);
            r.text(FontRegistry.SF_MEDIUM, textX, textY + lineH * 4, 13.0F,
               String.format("FreeLook.active=%b changeLook=%b evCancel=%b",
                  FreeLookUtil.active, MouseDeltas.lastChangeLookCalled, MouseDeltas.lastEventCancelled), 0xFFBFBFBF);
            if (Math.abs(MouseDeltas.lastRawYaw) < 0.0001 && Math.abs(MouseDeltas.lastRawPitch) < 0.0001
               && ExpertCapture.ringHasData()) {
               r.text(FontRegistry.SF_MEDIUM, textX, textY + lineH * 5, 13.0F,
                  "⚠ raw mouse = 0 — мышь не доходит до mixin'а", 0xFFFF6666);
            }
         }
      }
   }
}
