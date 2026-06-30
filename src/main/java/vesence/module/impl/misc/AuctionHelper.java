package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.impl.HandledScreenEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IModule(name = "AuctionHelper", description = "Подсвечивает самые дешёвые лоты, ищет предмет в руке по биндом и считает цену за единицу", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public final class AuctionHelper extends Module {

   private static final int COLOR_CHEAPEST = 0xFF4BFF4B;
   private static final int COLOR_SECOND = 0xFFFFE54B;
   private static final int COLOR_THIRD = 0xFFFF4B4B;

   private static final Pattern NUM_PATTERN = Pattern.compile("(\\d{1,3}(?:[\\s,._]\\d{3})+|\\d+)");

   private static AuctionHelper INSTANCE;

   private final BooleanSetting highlightCheapest = new BooleanSetting("Подсветка дешёвого", true);
   private final BooleanSetting highlightThree = new BooleanSetting("Подсвечивать три предмета", false);
   private final BooleanSetting perUnitTooltip = new BooleanSetting("Цена за единицу в подсказке", true);
   private final BindSettings searchBind = new BindSettings("Поиск предмета", -1);

   private boolean searchKeyWasDown = false;

   public AuctionHelper() {
      INSTANCE = this;
      this.addSettings(new Setting[]{highlightCheapest, highlightThree, perUnitTooltip, searchBind});
   }

   @Override
   public void onDisable() {
      this.searchKeyWasDown = false;
      super.onDisable();
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null) {
         this.searchKeyWasDown = false;
         return;
      }

      if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
         this.searchKeyWasDown = false;
         return;
      }

      boolean down = this.searchBind.isPressed();
      if (down && !this.searchKeyWasDown) {
         this.trySearchHeldItem();
      }
      this.searchKeyWasDown = down;
   }

   private void trySearchHeldItem() {
      if (mc.player == null || mc.player.networkHandler == null) {
         return;
      }

      ItemStack stack = mc.player.getMainHandStack();
      if (stack == null || stack.isEmpty()) {
         return;
      }

      String name = this.cleanName(stack.getName().getString());
      if (name.isEmpty()) {
         return;
      }

      mc.player.networkHandler.sendChatCommand("ah search " + name);
   }

   @EventInit
   public void onHandledScreen(HandledScreenEvent event) {
      if (!this.highlightCheapest.get()) {
         return;
      }
      if (mc.player == null || mc.currentScreen == null) {
         return;
      }

      ScreenHandler handler = mc.player.currentScreenHandler;
      if (handler == null) {
         return;
      }

      List<PricedSlot> priced = new ArrayList<>();
      for (Slot slot : handler.slots) {
         if (slot == null || !slot.hasStack()) {
            continue;
         }

         if (slot.inventory == mc.player.getInventory()) {
            continue;
         }

         long price = this.extractTotalPrice(slot.getStack());
         if (price < 0) {
            continue;
         }

         priced.add(new PricedSlot(slot, price));
      }

      if (priced.isEmpty()) {
         return;
      }

      priced.sort((a, b) -> Long.compare(a.price, b.price));

      if (this.highlightThree.get()) {
         int count = Math.min(3, priced.size());
         for (int i = 0; i < count; i++) {
            int color = switch (i) {
               case 0 -> COLOR_CHEAPEST;
               case 1 -> COLOR_SECOND;
               default -> COLOR_THIRD;
            };
            this.highlightSlot(event, priced.get(i).slot, color);
         }
      } else {
         this.highlightSlot(event, priced.get(0).slot, COLOR_CHEAPEST);
      }
   }

   private void highlightSlot(HandledScreenEvent event, Slot slot, int color) {
      DrawContext context = event.getDrawContext();
      int x = event.getScreenX() + slot.x;
      int y = event.getScreenY() + slot.y;

      int fill = (color & 0x00FFFFFF) | 0x80000000;
      int border = color | 0xFF000000;

      context.fill(x, y, x + 16, y + 16, fill);

      context.fill(x - 1, y - 1, x + 17, y, border);
      context.fill(x - 1, y + 16, x + 17, y + 17, border);
      context.fill(x - 1, y, x, y + 16, border);
      context.fill(x + 16, y, x + 17, y + 16, border);
   }

   public static void appendPerUnitTooltip(ItemStack stack, List<Text> tooltip) {
      AuctionHelper inst = INSTANCE;
      if (inst == null || !inst.enable || !inst.perUnitTooltip.get()) {
         return;
      }
      if (stack == null || stack.isEmpty() || tooltip == null || tooltip.isEmpty()) {
         return;
      }
      inst.appendPerUnit(stack, tooltip);
   }

   private void appendPerUnit(ItemStack stack, List<Text> tooltip) {
      int count = stack.getCount();
      if (count <= 1) {
         return;
      }

      List<String> lines = new ArrayList<>(tooltip.size());
      for (Text t : tooltip) {
         if (t != null) {
            lines.add(this.stripFormatting(t.getString()));
         }
      }

      PriceInfo info = this.parsePrice(lines);
      if (info == null || info.total <= 0) {
         return;
      }

      if (info.serverPerUnit > 0) {
         return;
      }

      long perUnit = info.total / count;
      if (perUnit <= 0) {
         return;
      }

      tooltip.add(Text.literal("\u00A77Цена за 1 ед.: \u00A7a" + this.formatNumber(perUnit) + "$"));
   }

   private long extractTotalPrice(ItemStack stack) {
      List<String> lines = this.collectTooltipLines(stack);
      if (lines.isEmpty()) {
         return -1L;
      }
      PriceInfo info = this.parsePrice(lines);
      return info == null ? -1L : info.total;
   }

   private List<String> collectTooltipLines(ItemStack stack) {
      List<String> out = new ArrayList<>();
      try {
         List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
         for (Text t : tooltip) {
            if (t != null) {
               out.add(this.stripFormatting(t.getString()));
            }
         }
      } catch (Throwable ignored) {
         out.clear();
         out.add(this.stripFormatting(this.safeName(stack)));
      }
      return out;
   }

   private PriceInfo parsePrice(List<String> lines) {
      long total = -1L;
      int totalScore = -1;
      long serverPerUnit = -1L;

      for (String raw : lines) {
         if (raw == null || raw.isEmpty()) {
            continue;
         }
         String lower = raw.toLowerCase(Locale.ROOT);
         if (!this.hasPriceKeyword(lower)) {
            continue;
         }

         long val = this.parseLineNumber(raw, lower);
         if (val <= 0) {
            continue;
         }

         if (this.isPerUnitLine(lower)) {
            if (serverPerUnit < 0) {
               serverPerUnit = val;
            }
            continue;
         }

         boolean totalKw = lower.contains("всего") || lower.contains("итого")
            || lower.contains("total") || lower.contains("сумм") || lower.contains("общ");
         int score = totalKw ? 5 : 3;
         if (score > totalScore) {
            totalScore = score;
            total = val;
         }
      }

      if (total <= 0 && serverPerUnit <= 0) {
         return null;
      }

      if (total <= 0) {
         total = serverPerUnit;
      }
      return new PriceInfo(total, serverPerUnit);
   }

   private boolean isPerUnitLine(String lower) {
      return lower.contains("за 1 ед") || lower.contains("за ед") || lower.contains("за шт")
         || lower.contains("за 1 шт") || lower.contains("/шт") || lower.contains("шт.")
         || lower.contains(" per ") || lower.contains(" each ") || lower.contains("за 1 ");
   }

   private long parseLineNumber(String raw, String lower) {
      Matcher m = NUM_PATTERN.matcher(raw);
      long best = -1L;
      while (m.find()) {
         int end = m.end(1);
         long val = this.parseDigitsToLong(m.group(1));
         if (val <= 0) {
            continue;
         }
         long mul = this.readSuffixMultiplier(lower, end);
         if (mul != 1L) {
            val *= mul;
         }
         if (val > best) {
            best = val;
         }
      }
      return best;
   }

   private boolean hasPriceKeyword(String ctx) {
      return ctx.contains("цена") || ctx.contains("price") || ctx.contains("стоим")
         || ctx.contains("руб") || ctx.contains("монет") || ctx.contains("coins") || ctx.contains("коин")
         || ctx.contains("buy") || ctx.contains("куп") || ctx.contains("$") || ctx.contains("₽");
   }

   private long readSuffixMultiplier(String lower, int end) {
      if (end >= lower.length()) {
         return 1L;
      }
      char c0 = lower.charAt(end);
      char c1 = (end + 1 < lower.length()) ? lower.charAt(end + 1) : 0;
      if (c0 == 'k' || c0 == 'к') {
         return (c1 == 'k' || c1 == 'к') ? 1_000_000L : 1_000L;
      }
      if (c0 == 'm' || c0 == 'м') {
         return 1_000_000L;
      }
      return 1L;
   }

   private long parseDigitsToLong(String raw) {
      long v = 0L;
      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            v = v * 10L + (long) (c - '0');
         }
      }
      return v;
   }

   private String formatNumber(long n) {
      String s = Long.toString(n);
      StringBuilder b = new StringBuilder();
      int c = 0;
      for (int i = s.length() - 1; i >= 0; i--) {
         b.append(s.charAt(i));
         if (++c % 3 == 0 && i > 0) {
            b.append(' ');
         }
      }
      return b.reverse().toString();
   }

   private String safeName(ItemStack s) {
      try {
         return s.getName().getString();
      } catch (Throwable t) {
         return "";
      }
   }

   private String stripFormatting(String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      StringBuilder out = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '§') {
            i++;
            continue;
         }
         out.append(c);
      }
      return out.toString();
   }

   private String cleanName(String name) {
      if (name == null) {
         return "";
      }

      String stripped = name
         .replaceAll("§.", "")
         .replaceAll("\\[.*?]", "");

      StringBuilder result = new StringBuilder();
      for (String word : stripped.split("\\s+")) {
         if (word.isEmpty() || !isCleanWord(word)) {
            continue;
         }
         if (result.length() > 0) {
            result.append(' ');
         }
         result.append(word);
      }

      return result.toString().trim();
   }

   private boolean isCleanWord(String word) {
      boolean hasLetter = false;
      for (int i = 0; i < word.length(); i++) {
         char c = word.charAt(i);
         if (!isAllowedChar(c)) {
            return false;
         }
         if (isLetter(c)) {
            hasLetter = true;
         }
      }
      return hasLetter;
   }

   private boolean isAllowedChar(char c) {
      return isLetter(c) || (c >= '0' && c <= '9');
   }

   private boolean isLetter(char c) {

      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
         return true;
      }

      if (c >= '\u0410' && c <= '\u044F') {
         return true;
      }

      return c == '\u0401' || c == '\u0451';
   }

   private static final class PriceInfo {
      private final long total;
      private final long serverPerUnit;

      private PriceInfo(long total, long serverPerUnit) {
         this.total = total;
         this.serverPerUnit = serverPerUnit;
      }
   }

   private static final class PricedSlot {
      private final Slot slot;
      private final long price;

      private PricedSlot(Slot slot, long price) {
         this.slot = slot;
         this.price = price;
      }
   }
}
