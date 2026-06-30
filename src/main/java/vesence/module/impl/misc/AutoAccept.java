package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.utils.friends.FriendStorage;

import java.util.Locale;

@IModule(name = "AutoAccept", description = "Автоматически принимает телепорт", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoAccept extends Module {

    private final BooleanSetting onlyFriend = new BooleanSetting("Только друзья", true);

    public AutoAccept() {
        this.addSettings(new Setting[]{onlyFriend});
    }

    @EventInit
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;
        if (e.isSend()) return;

        if (e.getPacket() instanceof GameMessageS2CPacket p) {
            String raw = p.content().getString().toLowerCase(Locale.ROOT);
            if (raw.contains("хочет телепортироваться") || raw.contains("has requested teleport") || raw.contains("просит телепортироваться")) {
                if (onlyFriend.get()) {
                    boolean found = false;
                    for (String friend : FriendStorage.getFriends()) {
                        if (raw.contains(friend.toLowerCase(Locale.ROOT))) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return;
                }
                mc.player.networkHandler.sendChatMessage("/tpaccept");
            }
        }
    }

    private static AutoAccept instance;

    public static AutoAccept getInstance() {
        return instance;
    }
}
