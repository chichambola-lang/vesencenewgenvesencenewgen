package vesence.module.api.setting.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import vesence.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class BindSettings extends Setting<Integer> {
   public int key;
   public final List<Integer> extraKeys = new ArrayList<>();
   public String description;
   public boolean active;

   public BindSettings(String name, int key) {
      this.name = name;
      this.key = key;
   }

   @Override
   public Integer get() {
      return this.key;
   }

   public void set(int key) {
      this.key = key;
   }

   public BindSettings hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   public boolean isKeyDown(int keyCode) {
      return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), keyCode);
   }

   /** Текст бинда для отображения: "R" или "CTRL + R" и т.д. */
   public String label() {
      StringBuilder sb = new StringBuilder(vesence.utils.render.utils.KeyUtil.getKey(this.key));
      for (int k : this.extraKeys) {
         sb.append(" + ").append(vesence.utils.render.utils.KeyUtil.getKey(k));
      }
      return sb.toString();
   }

   public boolean isPressed() {
      if (this.key == -1) return false;
      if (!isDown(this.key)) return false;
      if (!this.extraKeys.isEmpty()) {
         // Комбо-бинд: нужны все дополнительные клавиши
         for (int k : this.extraKeys) {
            if (!isDown(k)) return false;
         }
         return true;
      }
      // Голый бинд: игнорируем если зажат посторонний модификатор,
      // чтобы CTRL+X не срабатывал как X.
      if (isModifierHeld(this.key)) return false;
      return true;
   }

   /**
    * Проверяет, зажат ли какой-либо модификатор (Ctrl/Alt/Left Shift),
    * исключая сам бинд (self), чтобы бинды на модификаторы работали.
    * Right Shift исключён - открывает GUI клиента.
    */
   private boolean isModifierHeld(int self) {
      int[] mods = {
            GLFW.GLFW_KEY_LEFT_CONTROL,
            GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_ALT,
            GLFW.GLFW_KEY_RIGHT_ALT,
            GLFW.GLFW_KEY_LEFT_SHIFT
      };
      long handle = MinecraftClient.getInstance().getWindow().getHandle();
      for (int m : mods) {
         if (m == self) continue;
         if (GLFW.glfwGetKey(handle, m) == GLFW.GLFW_PRESS) {
            return true;
         }
      }
      return false;
   }

   private boolean isDown(int k) {
      if (k == -1) return false;
      if (k <= -100) {
         int button = -k - 100;
         long handle = MinecraftClient.getInstance().getWindow().getHandle();
         return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
      }
      return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), k);
   }
}
