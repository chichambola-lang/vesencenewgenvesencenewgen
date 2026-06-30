package vesence.utils.render.ttf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import vesence.renderengine.render.Renderer2D;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public final class TtfFont {

   private static final AtomicInteger COUNTER = new AtomicInteger(0);
   private static final int GLYPH_PADDING = 2;

   private final Font font;
   private final FontMetrics metrics;
   private final Map<Character, Glyph> glyphs = new HashMap<>();
   private final int ascent;
   private final int height;

   public TtfFont(Font font) {
      this.font = font;
      BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = probe.createGraphics();
      g.setFont(font);
      this.metrics = g.getFontMetrics();
      this.ascent = metrics.getAscent();
      this.height = metrics.getHeight();
      g.dispose();
   }

   public Font getFont() {
      return font;
   }

   public int getHeight() {
      return height;
   }

   public int getStringWidth(String text) {
      if (text == null || text.isEmpty()) return 0;
      int w = 0;
      for (int i = 0; i < text.length(); i++) {
         Glyph g = glyph(text.charAt(i));
         w += g.advance;
      }
      return w;
   }

   private Glyph glyph(char c) {
      Glyph cached = glyphs.get(c);
      if (cached != null) return cached;
      Glyph g = renderGlyph(c);
      glyphs.put(c, g);
      return g;
   }

   private Glyph renderGlyph(char c) {
      int advance = metrics.charWidth(c);
      int boxW = Math.max(1, advance + GLYPH_PADDING * 2);
      int boxH = Math.max(1, height + GLYPH_PADDING * 2);

      BufferedImage img = new BufferedImage(boxW, boxH, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setFont(font);
      g.setColor(new Color(255, 255, 255, 0));
      g.fillRect(0, 0, boxW, boxH);
      g.setColor(Color.WHITE);
      g.drawString(String.valueOf(c), GLYPH_PADDING, GLYPH_PADDING + ascent);
      g.dispose();

      NativeImage native_ = new NativeImage(boxW, boxH, false);
      for (int y = 0; y < boxH; y++) {
         for (int x = 0; x < boxW; x++) {
            native_.setColorArgb(x, y, img.getRGB(x, y));
         }
      }

      String name = "ttf_glyph_" + COUNTER.incrementAndGet();
      final String nameFinal = name;
      Supplier<String> nameSupplier = () -> nameFinal;
      NativeImageBackedTexture tex = new NativeImageBackedTexture(nameSupplier, native_);
      tex.upload();
      Identifier id = Identifier.of("vesence", "ttf_glyph/" + name);
      MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);

      int glId = 0;
      if (tex.getGlTexture() instanceof GlTexture glTex) {
         glId = glTex.getGlId();
      }

      Glyph glyph = new Glyph();
      glyph.glId = glId;
      glyph.identifier = id;
      glyph.width = boxW;
      glyph.height = boxH;
      glyph.advance = advance;
      glyph.offsetX = -GLYPH_PADDING;
      glyph.offsetY = -GLYPH_PADDING;
      return glyph;
   }

   public float drawString(Renderer2D renderer, String text, float x, float y, int color) {
      if (text == null || text.isEmpty()) return 0f;
      float curX = x;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         Glyph g = glyph(c);
         if (g.glId != 0) {
            renderer.drawImage(g.glId, curX + g.offsetX, y + g.offsetY, g.width, g.height, color);
         }
         curX += g.advance;
      }
      return curX - x;
   }

   public float drawCenteredString(Renderer2D renderer, String text, float cx, float y, int color) {
      float w = getStringWidth(text);
      return drawString(renderer, text, cx - w / 2f, y, color);
   }

   public float drawRightString(Renderer2D renderer, String text, float cx, float y, int color) {
      float w = getStringWidth(text);
      return drawString(renderer, text, cx - (w), y, color);
   }

   private static final class Glyph {
      int glId;
      Identifier identifier;
      int width;
      int height;
      int advance;
      int offsetX;
      int offsetY;
   }
}
