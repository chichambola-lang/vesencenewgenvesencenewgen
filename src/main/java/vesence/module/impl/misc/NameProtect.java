package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.utils.friends.FriendStorage;

@IModule(
    name = "NameProtect",
    description = "Скрывает ваш никнейм от чужих глазенок",
    category = Category.MISC,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class NameProtect extends Module {

    private static NameProtect instance;
    public static String fakeName = "";

    public StringSetting name = new StringSetting(
        "Ваше новое имя",
        "vesence"
    );

    public BooleanSetting hideFriends = new BooleanSetting("Скрывать друзей", false);

    public NameProtect() {
        instance = this;
        addSettings(new Setting[]{name, hideFriends});
    }

    @EventInit
    private void onUpdate(EventUpdate e) {
        fakeName = name.get();
    }

    public static String getReplaced(String input) {
        if (instance != null && instance.enable) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null) {
                input = input.replace(mc.getSession().getUsername(), fakeName);
            }

            if (instance.hideFriends.get()) {
                for (String friend : FriendStorage.getFriends()) {
                    input = input.replace(friend, fakeName);
                }
            }
        }
        return input;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static NameProtect getInstance() {
        return instance;
    }
}
