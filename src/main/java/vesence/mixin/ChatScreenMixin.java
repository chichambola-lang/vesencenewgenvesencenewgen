package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.utils.commands.suggestions.CommandSuggestionOverlay;
import vesence.utils.commands.suggestions.CommandSuggestions;
import vesence.utils.commands.suggestions.CommandSuggestionState;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

   @Shadow
   protected TextFieldWidget chatField;

   @Unique
   private final CommandSuggestionState vesence$suggestionState = new CommandSuggestionState();

   protected ChatScreenMixin(net.minecraft.text.Text title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void vesence$initSuggestions(CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      vesence$suggestionState.clear();
      if (chatField != null) {
         vesence$suggestionState.refresh(chatField.getText());
      }
   }

   @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
   private void vesence$onChatFieldUpdate(String chatText, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      vesence$suggestionState.refresh(chatText);
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void vesence$renderSuggestions(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      if (chatField == null) return;
      vesence$suggestionState.refresh(chatField.getText());
      if (!vesence$suggestionState.isActive()) {
         vesence$suggestionState.setLayout(null);
         return;
      }
      List<CommandSuggestions.SuggestionEntry> entries = vesence$suggestionState.getEntries();
      CommandSuggestionOverlay.Layout layout = CommandSuggestionOverlay.render(
         context,
         this.textRenderer,
         chatField,
         this.width,
         entries,
         vesence$suggestionState.getSelectionIndex(),
         vesence$suggestionState.getScrollOffset(),
         CommandSuggestionState.MAX_VISIBLE,
         mouseX,
         mouseY
      );
      vesence$suggestionState.setLayout(layout);
   }

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   private void vesence$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;

      int key = input.key();
      switch (key) {
         case 264:
            vesence$suggestionState.move(1);
            cir.setReturnValue(true);
            break;
         case 265:
            vesence$suggestionState.move(-1);
            cir.setReturnValue(true);
            break;
         case 258:
            vesence$applyCompletion();
            cir.setReturnValue(true);
            break;
         default:
            break;
      }
   }

   @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
   private void vesence$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;
      CommandSuggestionOverlay.Layout layout = vesence$suggestionState.getLayout();
      if (layout != null && layout.contains(mouseX, mouseY)) {
         vesence$suggestionState.scroll(verticalAmount > 0 ? -1 : 1);
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
   private void vesence$mouseClicked(net.minecraft.client.gui.Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;
      if (click.button() != 0) return;
      CommandSuggestionOverlay.Layout layout = vesence$suggestionState.getLayout();
      if (layout == null) return;
      int index = layout.entryIndexAt(click.x(), click.y());
      if (index >= 0) {
         vesence$suggestionState.setSelectionIndex(index);
         vesence$applyCompletion();
         cir.setReturnValue(true);
      }
   }

   @Unique
   private void vesence$applyCompletion() {
      CommandSuggestions.SuggestionEntry entry = vesence$suggestionState.getSelectedEntry();
      if (entry == null || chatField == null) return;
      String suffix = entry.completionSuffix();
      if (suffix == null || suffix.isEmpty()) return;
      chatField.setText(chatField.getText() + suffix);
      chatField.setCursorToEnd(false);
      vesence$suggestionState.refresh(chatField.getText());
   }
}
