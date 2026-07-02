package vesence.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.player.LockSlot;
import vesence.ui.clickgui.GuiClient;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        int key = input.key();
        if (key == -1 || key == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            return;
        }

        if (mc.player != null
                && mc.options.dropKey.matchesKey(input)
                && LockSlot.shouldCancelSelectedDrop()) {
            ci.cancel();
            return;
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            mc.setScreen(new GuiClient());
            if (mc.mouse != null) {
                mc.mouse.unlockCursor();
            }
            ci.cancel();
            return;
        }

        if (mc.player != null && Vesence.get != null && Vesence.get.manager != null) {
            long handle = mc.getWindow().getHandle();
            for (var module : Vesence.get.manager.getModules()) {
                if (module.bind == -1) continue;
                if (module.comboKeys.isEmpty()) {
                    // Голый бинд: срабатывает только если нажата именно эта клавиша
                    // и НЕ зажат посторонний модификатор (иначе конфликт с CTRL+X).
                    if (module.bind == key && !isModifierHeld(handle, module.bind)) {
                        module.toggle();
                    }
                } else {
                    // Комбо-бинд: нажатая клавиша должна быть частью комбо,
                    // а все остальные клавиши комбо должны быть зажаты.
                    if (isComboTriggered(handle, key, module.bind, module.comboKeys)) {
                        module.toggle();
                    }
                }
            }
        }

        if (mc.player != null) {
            vesence.utils.macro.MacroManager.getInstance().onKeyPress(key);
        }
    }

    private static boolean isKeyHeld(long handle, int k) {
        if (k == -1) return false;
        if (k <= -100) {
            return GLFW.glfwGetMouseButton(handle, -k - 100) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, k) == GLFW.GLFW_PRESS;
    }

    /**
     * Комбо срабатывает только если нажатая клавиша (key) входит в комбо
     * (это основной bind или один из comboKeys), а ВСЕ остальные клавиши
     * комбо в этот момент зажаты. Так CTRL+R сработает только на нажатии
     * последней недостающей клавиши при зажатых остальных.
     */
    private static boolean isComboTriggered(long handle, int pressedKey, int bind, java.util.List<Integer> comboKeys) {
        boolean pressedIsPart = (pressedKey == bind) || comboKeys.contains(pressedKey);
        if (!pressedIsPart) return false;
        // Основная клавиша должна быть зажата (или это она и есть)
        if (pressedKey != bind && !isKeyHeld(handle, bind)) return false;
        for (int k : comboKeys) {
            if (k == pressedKey) continue;
            if (!isKeyHeld(handle, k)) return false;
        }
        return true;
    }

    /**
     * Проверяет, зажат ли сейчас какой-либо модификатор (Ctrl/Alt/Left Shift).
     * Right Shift исключён - он открывает GUI клиента.
     * Если сам бинд является модификатором (self) - его зажатие игнорируется,
     * чтобы бинд на Ctrl/Alt/Shift продолжал работать.
     */
    private static boolean isModifierHeld(long handle, int self) {
        int[] mods = {
                GLFW.GLFW_KEY_LEFT_CONTROL,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                GLFW.GLFW_KEY_LEFT_ALT,
                GLFW.GLFW_KEY_RIGHT_ALT,
                GLFW.GLFW_KEY_LEFT_SHIFT
        };
        for (int m : mods) {
            if (m == self) continue;
            if (GLFW.glfwGetKey(handle, m) == GLFW.GLFW_PRESS) {
                return true;
            }
        }
        return false;
    }
}
