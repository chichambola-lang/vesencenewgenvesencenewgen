package vesence.utils.commands;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.impl.combat.auraComponent.neural.AimBrain;
import vesence.module.impl.combat.auraComponent.neural.ExpertCapture;
import vesence.module.impl.combat.auraComponent.neural.HistoryManager;
import vesence.module.impl.combat.auraComponent.neural.HumanizedRotation;
import vesence.module.impl.combat.auraComponent.neural.RLAgent;

@Environment(EnvType.CLIENT)
public final class AimCaptureCommand implements Command {
   private static final AimCaptureCommand INSTANCE = new AimCaptureCommand();
   private static final List<String> ALIASES = List.of(".ac", ".aimcapture", ".aimrec", ".aim");
   private static final int DEFAULT_EPOCHS = 50;
   private static final float DEFAULT_LR = 0.001f;

   private AimCaptureCommand() {
   }

   public static AimCaptureCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "AimCapture";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
       return ".ac <start|stop|status|list|use <id>|info <id>|clear <id>|train [ep]|rl on|off|stats|reset>";
   }

   @Override
   public String description() {
      return "Захват и Q-learning обучение Neural-режима (Smile + ReplayBuffer)";
   }

   @Override
   public void execute(CommandContext ctx, String args) throws CommandException {
      if (args == null || args.trim().isEmpty()) {
         ctx.sendInfo("Использование: " + usage());
         return;
      }
      String[] parts = args.trim().split("\\s+");
      String sub = parts[0].toLowerCase();

      switch (sub) {
         case "start": {
            String serverId = ExpertCapture.currentServerId();
            if (ExpertCapture.isRecording()) {
               ctx.sendError("Захват уже запущен (" + ExpertCapture.activeServer() + ")");
               return;
            }
            boolean wasNotNeural = !vesence.module.impl.combat.AttackAura.rotationType.is("Neural");
            boolean wasNotCapture = !vesence.module.impl.combat.AttackAura.neuralMode.is("Capture");
            vesence.module.impl.combat.AttackAura.rotationType.currentMode = "Neural";
            vesence.module.impl.combat.AttackAura.neuralMode.currentMode = "Capture";
            if (wasNotNeural || wasNotCapture) {
               ctx.sendInfo("Aura → §eNeural§7 → §eCapture§7 (авто-переключение).");
            }
            if (ExpertCapture.start(serverId)) {
               ctx.sendSuccess("Захват начат. Сервер: §e" + serverId);
               ctx.sendInfo("Управляй прицелом вручную — мышь пишется в файл.");
               ctx.sendInfo("Останови запись: §f.ac stop§7 (после — авто-обучение).");
            } else {
               ctx.sendError("Не удалось начать захват. Проверь права на запись в " + ExpertCapture.getBaseDir());
            }
            break;
         }
         case "stop": {
            if (!ExpertCapture.isRecording()) {
               ctx.sendError("Захват не активен.");
               return;
            }
            int count = ExpertCapture.count();
            String sid = ExpertCapture.activeServer();
            ExpertCapture.stop();
            ctx.sendSuccess("Захват остановлен. Сервер: §e" + sid + "§a, сэмплов: §e" + count);

            if (count < ExpertCapture.MIN_SAMPLES) {
               ctx.sendError("Мало данных (" + count + " < " + ExpertCapture.MIN_SAMPLES + "). Запиши больше боёв.");
               return;
            }
            ctx.sendInfo("Запускаю обучение...");
            trainSync(ctx, sid, DEFAULT_EPOCHS, DEFAULT_LR);
            break;
         }
         case "status": {
            String currentId = ExpertCapture.currentServerId();
            String activeId = ExpertCapture.getActiveServerId();
            String recId = ExpertCapture.activeServer();
            ctx.sendInfo("Физический сервер: §e" + currentId);
            ctx.sendInfo("Активный сервер: §e" + activeId
               + (currentId.equals(activeId) ? "" : " §7(отличается от физического)"));
            if (ExpertCapture.isRecording()) {
               ctx.sendSuccess("Захват идёт → §e" + recId + "§a, сэмплов: §e" + ExpertCapture.count());
            } else {
               ctx.sendInfo("Захват: выключен");
            }
            File rlFile = ExpertCapture.brainFile(activeId);
            ctx.sendInfo("RL-модель для активного: " + (rlFile.exists() ? "§aобучена" : "§eотсутствует"));
            ctx.sendInfo("RL-режим: " + (HumanizedRotation.isRLEnabled() ? "§aвкл" : "§cвыкл"));
            break;
         }
         case "list":
         case "ls": {
            List<ExpertCapture.ServerInfo> servers = ExpertCapture.listServers();
            if (servers.isEmpty()) {
               ctx.sendInfo("Серверов с датасетами нет. Начни запись: .ac start");
               return;
            }
            ctx.sendSuccess("Сохранённые серверы (" + servers.size() + "):");
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");
            for (ExpertCapture.ServerInfo s : servers) {
               StringBuilder line = new StringBuilder();
               if (s.isActive) line.append("§a▶ ");
               else if (s.isCurrent) line.append("§e▸ ");
               else line.append("  ");
               line.append("§f").append(s.id);
               line.append(" §7| §f").append(s.samples).append(" §7сэмплов");
               line.append(" §7| §f").append(formatSize(s.fileSize));
               line.append(" §7| RL: ").append(s.hasBc ? "§aобучен" : "§7—");
               if (s.lastModified > 0) {
                  line.append(" §7| §f").append(sdf.format(new Date(s.lastModified)));
               }
               if (s.isCurrent) line.append(" §7(сейчас здесь)");
               if (s.isActive) line.append(" §a(активный)");
               ctx.sendInfo(line.toString());
            }
            ctx.sendInfo("§7Легенда: §a▶ §7= активный, §e▸ §7= текущий физический");
            break;
         }
         case "use": {
            if (parts.length < 2) {
               String cur = ExpertCapture.getActiveServerId();
               ctx.sendInfo("Активный сервер: §e" + cur);
               ctx.sendInfo("Использование: .ac use <id>   (см. .ac list)");
               return;
            }
            String newId = ExpertCapture.resolveServerId(parts[1]);
            ExpertCapture.ServerInfo info = ExpertCapture.getServerInfo(newId);
            if (info == null) {
               ctx.sendError("Сервер §e" + newId + "§c не найден среди датасетов. См. .ac list");
               return;
            }
            ExpertCapture.setActiveServerId(newId);
            HumanizedRotation.initBrain(newId);
            ctx.sendSuccess("Активный сервер: §e" + newId + "§a, сэмплов: §e" + info.samples
               + "§a, RL: " + (info.hasBc ? "§aобучен" : "§e—"));
            if (info.hasBc) {
               ctx.sendInfo("RL-весы загружены. Включи Aura в режиме Neural → Active.");
            }
            break;
         }
         case "info": {
            if (parts.length < 2) {
               ctx.sendError("Использование: .ac info <id>");
               return;
            }
            String id = ExpertCapture.resolveServerId(parts[1]);
            ExpertCapture.ServerInfo info = ExpertCapture.getServerInfo(id);
            if (info == null) {
               ctx.sendError("Сервер §e" + id + "§c не найден.");
               return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            ctx.sendInfo("§f" + info.id);
            ctx.sendInfo("  Сэмплов: §e" + info.samples);
            ctx.sendInfo("  Файл: §7" + ExpertCapture.captureFile(info.id).getAbsolutePath());
            ctx.sendInfo("  Размер: §7" + formatSize(info.fileSize));
            ctx.sendInfo("  Изменён: §7" + (info.lastModified > 0 ? sdf.format(new Date(info.lastModified)) : "—"));
            ctx.sendInfo("  RL-веса: " + (info.hasBc ? "§a" + ExpertCapture.brainFile(info.id).getName() : "§e—"));
            if (info.isCurrent) ctx.sendInfo("  §7(ты сейчас на этом сервере)");
            if (info.isActive) ctx.sendInfo("  §a(активный)");
            break;
         }
         case "clear": {
            String sid = parts.length > 1 ? ExpertCapture.resolveServerId(parts[1]) : ExpertCapture.getActiveServerId();
            if (ExpertCapture.isRecording() && sid.equals(ExpertCapture.activeServer())) {
               ctx.sendError("Идёт запись. Сначала .ac stop");
               return;
            }
            boolean ok = ExpertCapture.clear(sid);
            File bf = ExpertCapture.brainFile(sid);
            if (bf.exists()) bf.delete();
            if (ok) {
               ctx.sendSuccess("Датасет и RL-весы очищены для §e" + sid);
            } else {
               ctx.sendError("Очистка не удалась.");
            }
            break;
         }
         case "debug": {
            if (parts.length < 2) {
               ctx.sendInfo("Debug: " + (ExpertCapture.isDebug() ? "§aON" : "§cOFF") + ". Использование: .ac debug on|off");
               return;
            }
            String v = parts[1].toLowerCase();
            if ("on".equals(v) || "1".equals(v) || "true".equals(v)) {
               ExpertCapture.setDebug(true);
               ctx.sendSuccess("Debug §aON§f: каждые ~1.5с в чат — yaw, dy, raw mouse, FreeLookUtil и т.д.");
            } else if ("off".equals(v) || "0".equals(v) || "false".equals(v)) {
               ExpertCapture.setDebug(false);
               ctx.sendInfo("Debug §cOFF");
            } else {
               ctx.sendError("Аргумент: on|off");
            }
            break;
         }
         case "train": {
            int epochs = DEFAULT_EPOCHS;
            float lr = DEFAULT_LR;
            if (parts.length > 1) {
               try { epochs = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            }
            if (parts.length > 2) {
               try { lr = Float.parseFloat(parts[2]); } catch (NumberFormatException ignored) {}
            }
            String sid = ExpertCapture.getActiveServerId();
            ctx.sendInfo("Запуск Q-learning обучения: epochs=" + epochs + ", lr=" + lr);
            trainSync(ctx, sid, epochs, lr);
            break;
         }
         case "rl": {
            if (parts.length < 2) {
               ctx.sendInfo("RL: " + (HumanizedRotation.isRLEnabled() ? "§aON" : "§cOFF") + ". Использование: .ac rl on|off");
               return;
            }
            String v = parts[1].toLowerCase();
            boolean on = v.equals("on") || v.equals("1") || v.equals("true");
            HumanizedRotation.setRLEnabled(on);
            ctx.sendInfo("RL-агент: " + (on ? "§aON" : "§cOFF"));
            break;
         }
         case "stats": {
            AimBrain brain = HumanizedRotation.getBrain();
            if (brain == null || brain.getAgent() == null) {
               ctx.sendInfo("RL-агент не инициализирован. Сначала .ac train");
               return;
            }
            RLAgent agent = brain.getAgent();
            HistoryManager hist = brain.getHistory();
            ctx.sendInfo("§f[§bAI§f] Статистика:");
            ctx.sendInfo("  Сервер: §e" + agent.getServerId());
            ctx.sendInfo("  Replay buffer: §e" + agent.getBufferSize() + " / " + vesence.module.impl.combat.auraComponent.neural.ReplayBuffer.CAPACITY);
            ctx.sendInfo("  ε (exploration): §e" + String.format("%.4f", agent.getEpsilon()));
            ctx.sendInfo("  Total steps: §e" + agent.getTotalSteps());
            ctx.sendInfo("  History: §e" + hist.size() + " переходов, " + hist.getTotalBattles() + " боёв, " + hist.getTotalHits() + " попаданий");
            float acc = hist.getAccuracy() * 100f;
            ctx.sendInfo("  Accuracy: §e" + String.format("%.1f%%", acc));
            break;
         }
         case "reset": {
            AimBrain brain = HumanizedRotation.getBrain();
            if (brain != null) {
               if (brain.getAgent() != null) {
                  brain.getAgent().getQNetwork().getClass();
               }
            }
            HumanizedRotation.initBrain(ExpertCapture.getActiveServerId());
            ctx.sendSuccess("RL-агент и история перезагружены");
            break;
         }
         default:
            ctx.sendInfo("Использование: " + usage());
            break;
      }
   }

   private static String formatSize(long bytes) {
      if (bytes < 1024) return bytes + "B";
      if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
      return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
   }

   private void trainSync(CommandContext ctx, String serverId, int epochs, float lr) {
      ExpertCapture.setActiveServerId(serverId);
      HumanizedRotation.initBrain(serverId);
      AimBrain brain = HumanizedRotation.getBrain();
      if (brain == null) {
         ctx.sendError("Не удалось инициализировать брейн.");
         return;
      }
      try {
         List<ExpertCapture.Sample> samples = ExpertCapture.loadAll(ExpertCapture.captureFile(serverId));
         rebuildHistory(brain, samples);
         brain.setAgent(new RLAgent(serverId));
         if (samples.size() >= 50) {
            double meanDy = 0, meanDp = 0;
            for (ExpertCapture.Sample s : samples) { meanDy += s.dy; meanDp += s.dp; }
            meanDy /= samples.size(); meanDp /= samples.size();
            double varDy = 0, varDp = 0;
            for (ExpertCapture.Sample s : samples) { varDy += (s.dy - meanDy) * (s.dy - meanDy); varDp += (s.dp - meanDp) * (s.dp - meanDp); }
            varDy /= samples.size(); varDp /= samples.size();
            double stdDy = Math.sqrt(varDy), stdDp = Math.sqrt(varDp);
            if (stdDy < 0.01 && stdDp < 0.01) {
               ctx.sendError("Датасет пустой по движению (stdev dy=" + String.format("%.4f", stdDy) + ", dp=" + String.format("%.4f", stdDp) + ")");
               ctx.sendError("Возможно мышь не записывалась. Перезапиши: .ac clear " + serverId + ", затем .ac start и подвигай мышью.");
               return;
            }
         }
         brain.warmupFromHistory(brain.getHistory());
         int n = samples.size();
         for (int ep = 0; ep < epochs; ep++) {
            brain.trainStep();
         }
         RLAgent agent = brain.getAgent();
         File saveFile = ExpertCapture.brainFile(serverId);
         if (agent != null) {
            agent.save(saveFile);
         }
         brain.saveToFile();
         ctx.sendSuccess("RL обучен. Сервер: §e" + serverId + "§a, сэмплов: §e" + n + "§a, эпох: §e" + epochs + "§a, ε: §e" + String.format("%.4f", agent != null ? agent.getEpsilon() : 0));
         ctx.sendInfo("История: §e" + brain.getHistory().getTotalBattles() + "§a боёв, §e" + brain.getHistory().getTotalHits() + "§a попаданий, точность: §e" + String.format("%.1f%%", brain.getHistory().getAccuracy() * 100f));
         ctx.sendInfo("Включи Aura в режиме Neural → Active — теперь работает Q-агент.");
      } catch (Throwable t) {
         ctx.sendError("Ошибка обучения: " + t.getMessage());
         t.printStackTrace();
      }
   }

   private void rebuildHistory(AimBrain brain, List<ExpertCapture.Sample> samples) {
      if (brain == null) {
         return;
      }

      HistoryManager history = brain.getHistory();
      history.clear();
      if (samples == null || samples.isEmpty()) {
         return;
      }

      int lastTargetId = Integer.MIN_VALUE;
      boolean battleStarted = false;
      for (ExpertCapture.Sample sample : samples) {
         if (sample == null || sample.state == null) {
            continue;
         }

         if (!battleStarted || sample.targetId != lastTargetId) {
            history.incrementBattleNow();
            battleStarted = true;
            lastTargetId = sample.targetId;
         }

         boolean isAttack = sample.state.length > 2 && sample.state[2] >= 0.5f;
         int action = encodeAction(sample.dy, sample.dp);
         float reward = sample.hit ? 1.0f : (isAttack ? -0.05f : -0.01f);
         history.addTransition(sample.state, action, reward, sample.hit, isAttack);
      }
   }

   private int encodeAction(float dy, float dp) {
      int yawIdx = encodeAxis(dy, 30.0f);
      int pitchIdx = encodeAxis(dp, 25.0f);
      return yawIdx * 5 + pitchIdx;
   }

   private int encodeAxis(float delta, float maxAbs) {
      float normalized = Math.min(Math.abs(delta) / Math.max(maxAbs, 0.001f), 1.0f);
      int idx = Math.round(normalized * 4.0f);
      if (idx < 0) {
         return 0;
      }
      if (idx > 4) {
         return 4;
      }
      return idx;
   }
}
