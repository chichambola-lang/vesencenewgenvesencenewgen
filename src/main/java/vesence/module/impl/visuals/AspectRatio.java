package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Screen Ratio", description = "Растягивает матрицу экрана", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class AspectRatio extends Module {

    public static SliderSetting strength = new SliderSetting("Сила растяга", 1.0, 0.1, 3.0, 0.01, false);

    private static AspectRatio instance;

    public AspectRatio() {
        this.addSettings(new Setting[]{strength});
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static float getStretchFactor() {
        if (!isEnabled()) return 1.0f;
        return (float) strength.current;
    }
}
