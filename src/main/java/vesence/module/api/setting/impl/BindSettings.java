package vesence.module.api.setting.impl;

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

   public boolean isPressed() {
      if (this.key == -1) return false;
      if (this.key <= -100) {
         int button = -this.key - 100;
         long handle = MinecraftClient.getInstance().getWindow().getHandle();
         return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
      }
      return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), this.key);
   }
}
