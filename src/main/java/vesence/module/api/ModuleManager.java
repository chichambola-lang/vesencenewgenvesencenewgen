package vesence.module.api;

import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.impl.combat.*;
import vesence.module.impl.misc.*;
import vesence.module.impl.movement.*;
import vesence.module.impl.player.*;
import vesence.module.impl.visuals.*;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
public class ModuleManager {
   public ArrayList<Module> module = new ArrayList<>();

   public void init() {
      this.module.add(new AutoSprint());
      this.module.add(new NoSlow());
      this.module.add(new Speed());
      this.module.add(new NoWeb());
      this.module.add(new GuiMove());
      this.module.add(new DashTrails());
      this.module.add(new NoDelay());
      this.module.add(new vesence.module.impl.visuals.ThemeModule());
      this.module.add(new Hud());
      this.module.add(new Arrows());
      this.module.add(new vesence.module.impl.visuals.JumpCircle());
      this.module.add(new vesence.module.impl.visuals.TargetESP());
      this.module.add(new vesence.module.impl.visuals.FakePlayer());
      this.module.add(new vesence.module.impl.visuals.SkeletonESP());
      this.module.add(new vesence.module.impl.visuals.ChunksAnimation());
      this.module.add(new vesence.module.impl.visuals.EnchantmentColor());
      this.module.add(new vesence.module.impl.visuals.HitBubbles());
      this.module.add(new vesence.module.impl.visuals.HitMarker());
      this.module.add(new vesence.module.impl.visuals.HitSound());
      this.module.add(new vesence.module.impl.visuals.CameraClip());
      this.module.add(new vesence.module.impl.visuals.Particles());
      this.module.add(new vesence.module.impl.visuals.ParticularWater());
      this.module.add(new vesence.module.impl.visuals.InterpolateF5());
      this.module.add(new vesence.module.impl.misc.SettingView());
      this.module.add(new vesence.module.impl.misc.PotionTracker());
      this.module.add(new vesence.module.impl.misc.AuctionHelper());
      this.module.add(new AttackAura());
      this.module.add(new AimBot());
      this.module.add(new AutoExplosion());
      this.module.add(new TriggerBot());
      this.module.add(new AntiBot());      this.module.add(new Assist());
      this.module.add(new ScoreboardHealth());
      this.module.add(new Criticals());
      this.module.add(new NoFriendDamage());
      this.module.add(new NameProtect());
      this.module.add(new RPSpoofer());
      this.module.add(new AutoDuel());
      this.module.add(new ClickGui());
      this.module.add(new NoRender());
      this.module.add(new Ambience());
      this.module.add(new FullBright());
      this.module.add(new AspectRatio());
      this.module.add(new ClickAction());
      this.module.add(new AutoEat());
      this.module.add(new FreeLook());
      this.module.add(new AutoJoin());
      this.module.add(new BlockOutline());
      this.module.add(new vesence.module.impl.visuals.Nametags());
      this.module.add(new vesence.module.impl.visuals.Trails());
      this.module.add(new AutoTotem());
      this.module.add(new AutoSwap());
      this.module.add(new SeeInvisibles());
      this.module.add(new Crosshair());
      this.module.add(new ChinaHat());
      this.module.add(new ContainerESP());
      this.module.add(new Projectile());
      this.module.add(new ItemPhysic());
      this.module.add(new vesence.module.impl.visuals.CustomHand());
      this.module.add(new vesence.module.impl.visuals.ViewModel());
      this.module.add(new vesence.module.impl.visuals.LivingHands());
      this.module.add(new HitBox());
      this.module.add(new NoEntityTrace());
      this.module.add(new ChestStealer());
      this.module.add(new AutoAccept());
      this.module.add(new AutoFix());
      this.module.add(new ItemScroller());
      this.module.add(new OpenWalls());
      this.module.add(new ItemRelease());
      this.module.add(new FastBreak());
      this.module.add(new AutoTool());
      this.module.add(new LockSlot());
      this.module.add(new NoInteract());
      this.module.add(new NoPush());
      this.module.add(new FreeCamera());
      this.module.add(new AirStuck());
      this.module.add(new AutoPotion());
      this.module.add(new vesence.module.impl.misc.BetterMinecraft());
      this.module.add(new vesence.module.impl.visuals.CustomPet());

      this.module.add(new vesence.module.impl.performance.FpsBoost());
      this.module.add(new vesence.module.impl.performance.RenderOptimizer());
      this.module.add(new vesence.module.impl.performance.OcclusionCulling());
      this.module.add(new vesence.module.impl.performance.MemoryOptimizer());
      this.module.add(new vesence.module.impl.performance.NetworkOptimizer());
      this.module.add(new vesence.module.impl.performance.WorldOptimizer());
      this.module.add(new vesence.module.impl.performance.ThreadOptimizer());
      this.module.add(new vesence.module.impl.performance.HudOptimizer());
      this.module.add(new vesence.module.impl.performance.CrashReporter());
      this.module.add(new StructureHelper());

      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Watermark", "Watermark"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Informations", "Informations"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Keybinds", "Keybinds"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Target Hud", "Target Hud"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Potions List", "Potions List"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Cooldowns", "Cooldowns"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Staff List", "Staff List"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Hud Binds Assist", "Hud Binds Assist"));
      this.module.add(new vesence.module.impl.visuals.HudToggleModule("Notifications", "Notifications"));
   }

   public ArrayList<Module> getModules() {
      return this.module;
   }

   public <T extends Module> T get(Class<T> clazz) {
      return this.module.stream().filter(module -> clazz.isAssignableFrom(module.getClass())).map(clazz::cast).findFirst().orElse(null);
   }

   public Module getModule(Class<?> class1) {
      for (Module module1 : this.module) {
         if (module1.getClass() == class1) {
            return module1;
         }
      }

      return null;
   }

   public ArrayList<Module> getType(Category category) {
      ArrayList<Module> modules = new ArrayList<>();

      for (Module module1 : this.module) {
         if (module1.category == category && !module1.hiddenFromGui) {
            modules.add(module1);
         }
      }

      return modules;
   }

   public Module[] getBind(int bind) {
      if (bind == -1) {
         return new Module[0];
      }
      return Vesence.get.manager.module.stream().filter(module -> module.bind == bind).toArray(Module[]::new);
   }
}
