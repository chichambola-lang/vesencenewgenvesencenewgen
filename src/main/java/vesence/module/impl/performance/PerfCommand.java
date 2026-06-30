package vesence.module.impl.performance;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.commands.Command;
import vesence.utils.commands.CommandContext;
import vesence.utils.commands.CommandException;

@Environment(EnvType.CLIENT)
public final class PerfCommand implements Command {

   private static final PerfCommand INSTANCE = new PerfCommand();
   private static final List<String> ALIASES = List.of(".perf", "/perf");

   private PerfCommand() {
   }

   public static PerfCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "perf";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ALIASES.get(0) + " stats";
   }

   @Override
   public String description() {
      return "Performance modules state and FPS impact";
   }

   @Override
   public void execute(CommandContext context, String arguments) throws CommandException {
      String sub = arguments == null ? "" : arguments.trim().toLowerCase(Locale.ROOT);
      if (sub.isEmpty() || sub.equals("stats")) {
         printStats(context);
      } else {
         context.sendInfo("Usage: " + usage());
      }
   }

   private void printStats(CommandContext context) {
      PerfManager perf = PerfManager.getInstance();
      int current = perf.getCurrentFps();
      int average = perf.getAverageFps();
      int baseline = perf.getBaselineFps();
      int impact = perf.getFpsImpact();

      context.sendInfo("=== Vesence Performance ===");
      context.sendInfo("FPS: " + current + " (avg " + average + ", baseline " + baseline + ")");
      context.sendInfo("Impact: " + (impact >= 0 ? "+" : "") + impact + " FPS");

      Map<String, String> status = perf.getModuleStatus();
      if (status.isEmpty()) {
         context.sendInfo("No performance modules active.");
         return;
      }
      for (Map.Entry<String, String> e : status.entrySet()) {
         context.sendInfo(" - " + e.getKey() + ": " + e.getValue());
      }
   }
}
