package vesence.utils.other;

import org.lwjgl.glfw.GLFW;

public class KeyUtil {
    public static String getKey(int key) {
        if (key == -1) return "null";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            default -> "KEY_" + key;
        };
    }
}
