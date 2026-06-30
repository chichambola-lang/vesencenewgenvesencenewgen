package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Network Optimizer", description = "Безопасное снижение избыточного сетевого трафика", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class NetworkOptimizer extends PerformanceModule {

   private static NetworkOptimizer instance;

   private final BooleanSetting skipRedundantMoves = new BooleanSetting("Пропуск дублей движения", false);
   private final SliderSetting moveEpsilon = new SliderSetting("Порог дельты (×1000)", 1, 0, 20, 1);

   private final AtomicLong inbound = new AtomicLong(0);
   private final AtomicLong outbound = new AtomicLong(0);
   private final AtomicLong skipped = new AtomicLong(0);

   private volatile double lastX, lastY, lastZ;
   private volatile float lastYaw, lastPitch;
   private volatile boolean lastOnGround;
   private volatile boolean hasLast = false;

   public NetworkOptimizer() {
      this.addSettings(skipRedundantMoves, moveEpsilon);
      instance = this;
   }

   public static NetworkOptimizer getInstance() {
      return instance;
   }

   @Override
   protected void applyOptimizations() {
      inbound.set(0);
      outbound.set(0);
      skipped.set(0);
      hasLast = false;
   }

   @Override
   protected void restoreOriginals() {
      hasLast = false;
   }

   @EventInit
   public void onPacket(EventPacket e) {
      if (!isActive() || e.getPacket() == null) {
         return;
      }
      if (!e.isSend()) {
         inbound.incrementAndGet();
         return;
      }

      outbound.incrementAndGet();
      if (skipRedundantMoves.get() && e.getPacket() instanceof PlayerMoveC2SPacket move) {
         if (isRedundant(move)) {
            skipped.incrementAndGet();
            e.cancel();
         } else {
            remember(move);
         }
      }
   }

   private boolean isRedundant(PlayerMoveC2SPacket move) {
      if (!hasLast) {
         return false;
      }
      double eps = moveEpsilon.current / 1000.0;
      boolean changesPos = move.changesPosition();
      boolean changesLook = move.changesLook();

      if (changesPos) {
         double nx = move.getX(lastX);
         double ny = move.getY(lastY);
         double nz = move.getZ(lastZ);
         if (Math.abs(nx - lastX) > eps || Math.abs(ny - lastY) > eps || Math.abs(nz - lastZ) > eps) {
            return false;
         }
      }
      if (changesLook) {
         float nyaw = move.getYaw(lastYaw);
         float npitch = move.getPitch(lastPitch);
         if (Math.abs(nyaw - lastYaw) > eps || Math.abs(npitch - lastPitch) > eps) {
            return false;
         }
      }

      return move.isOnGround() == lastOnGround;
   }

   private void remember(PlayerMoveC2SPacket move) {
      lastX = move.getX(lastX);
      lastY = move.getY(lastY);
      lastZ = move.getZ(lastZ);
      lastYaw = move.getYaw(lastYaw);
      lastPitch = move.getPitch(lastPitch);
      lastOnGround = move.isOnGround();
      hasLast = true;
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      PerfManager.getInstance().reportStatus(this.name,
            "ON / in=" + inbound.get() + " out=" + outbound.get() + " skip=" + skipped.get());
   }
}
