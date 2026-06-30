package vesence.utils.render.text;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.renderengine.providers.GlBackend;
import vesence.renderengine.render.Renderer2D;

@Environment(EnvType.CLIENT)
public final class FontRegistry {
   private static final Map<String, MsdfFont> REGISTERED_FONTS = new HashMap<>();
   private static final Map<String, FontObject> FONT_OBJECTS = new HashMap<>();
   private static GlBackend backend;
   private static boolean backendConfigured = false;
   private static boolean rendererFontsInitialized = false;
   public static FontObject SF_MEDIUM;
   public static FontObject SF_SEMI;
   public static FontObject SF_BOLD;
   public static FontObject SU_MEDIUM;
   public static FontObject MONTSERRAT;
   public static FontObject ICONS;
   public static FontObject VESENCE;
   public static FontObject MON;
   public static FontObject NUC;
   public static FontObject LOGO;
   public static FontObject DELUXE;
   public static FontObject ICON;
   public static FontObject RELAKE;
   public static FontObject TEST;
   public static FontObject NOTIFY;

   private FontRegistry() {
   }

   public static synchronized void initialize(GlBackend glBackend, Renderer2D renderer) {
      configureBackend(glBackend);
      Objects.requireNonNull(renderer, "renderer");
      if (!rendererFontsInitialized) {
         renderer.registerTextRenderer(SF_MEDIUM, createTextRenderer(SF_MEDIUM));
         renderer.registerTextRenderer(SF_SEMI, createTextRenderer(SF_SEMI));
         renderer.registerTextRenderer(SF_BOLD, createTextRenderer(SF_BOLD));
         renderer.registerTextRenderer(SU_MEDIUM, createTextRenderer(SU_MEDIUM));
         renderer.registerTextRenderer(MONTSERRAT, createTextRenderer(MONTSERRAT));
         renderer.registerTextRenderer(ICONS, createTextRenderer(ICONS));
         renderer.registerTextRenderer(NOTIFY, createTextRenderer(NOTIFY));
         renderer.registerTextRenderer(TEST, createTextRenderer(TEST));
         renderer.registerTextRenderer(VESENCE, createTextRenderer(VESENCE));
         renderer.registerTextRenderer(LOGO, createTextRenderer(LOGO));
         renderer.registerTextRenderer(MON, createTextRenderer(MON));
         renderer.registerTextRenderer(NUC, createTextRenderer(NUC));
         renderer.registerTextRenderer(DELUXE, createTextRenderer(DELUXE));
         renderer.registerTextRenderer(RELAKE, createTextRenderer(RELAKE));
         renderer.registerTextRenderer(ICON, createTextRenderer(ICON));
         rendererFontsInitialized = true;
      }
   }

   public static synchronized FontObject register(String id, String jsonResourcePath, String textureResourcePath) {
      ensureBackendConfigured();
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(jsonResourcePath, "jsonResourcePath");
      Objects.requireNonNull(textureResourcePath, "textureResourcePath");
      if (REGISTERED_FONTS.containsKey(id)) {
         throw new IllegalStateException("Font already registered: " + id);
      } else {
         MsdfFont font = MsdfFont.load(backend, jsonResourcePath, textureResourcePath);
         REGISTERED_FONTS.put(id, font);
         FontObject fontObject = new FontObject(id);
         FONT_OBJECTS.put(id, fontObject);
         return fontObject;
      }
   }

   public static synchronized TextRenderer createTextRenderer(FontObject fontObject) {
      ensureBackendConfigured();
      MsdfFont msdfFont = resolve(fontObject);
      return new TextRenderer(backend, msdfFont);
   }

   public static synchronized float centeredBaselineOffset(FontObject fontObject, int codepoint, float size) {
      ensureBackendConfigured();
      if (fontObject != null && !(size <= 0.0F)) {
         MsdfFont font = resolve(fontObject);
         MsdfFont.Glyph glyph = font.glyph(codepoint);
         if (glyph != null && glyph.renderable) {
            float emSize = Math.max(1.0E-6F, font.emSize());
            float scale = size / emSize;
            return (glyph.planeTop + glyph.planeBottom) * 0.5F * scale;
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   public static synchronized FontObject get(String id) {
      ensureBackendConfigured();
      FontObject fontObject = FONT_OBJECTS.get(id);
      if (fontObject == null) {
         throw new IllegalArgumentException("Font not registered: " + id);
      } else {
         return fontObject;
      }
   }

   static synchronized MsdfFont resolve(FontObject fontObject) {
      ensureBackendConfigured();
      MsdfFont font = REGISTERED_FONTS.get(fontObject.id);
      if (font == null) {
         throw new IllegalStateException("Font not registered: " + fontObject.id);
      } else {
         return font;
      }
   }

   private static void configureBackend(GlBackend glBackend) {
      Objects.requireNonNull(glBackend, "backend");
      if (backendConfigured) {
         if (backend != glBackend) {
            throw new IllegalStateException("FontRegistry already initialized with a different backend instance");
         }
      } else {
         backend = glBackend;
         backendConfigured = true;
         registerBuiltinFonts();
      }
   }

   private static void registerBuiltinFonts() {

      SF_MEDIUM = register("SF_MEDIUM", "assets/vesence/fonts/name_tag.json", "assets/vesence/fonts/name_tag.png");
      SF_SEMI = register("SF_SEMI", "assets/vesence/fonts/sf_semibold.json", "assets/vesence/fonts/sf_semibold.png");
      SF_BOLD = register("SF_BOLD", "assets/vesence/fonts/sf_bold.json", "assets/vesence/fonts/sf_bold.png");
      SU_MEDIUM = register("SU", "assets/vesence/fonts/suisseintl_medium.json", "assets/vesence/fonts/suisseintl_medium.png");
      MONTSERRAT = register("MONTSERRAT", "assets/vesence/fonts/montserrat_semibold.json", "assets/vesence/fonts/montserrat_semibold.png");

      ICONS = register("icons", "assets/vesence/fonts/icons.json", "assets/vesence/fonts/icons.png");
      NOTIFY = register("notify", "assets/vesence/fonts/notify.json", "assets/vesence/fonts/notify.png");
      TEST = register("test", "assets/vesence/fonts/productsans_medium.json", "assets/vesence/fonts/productsans_medium.png");
      VESENCE = register("vesence", "assets/vesence/fonts/vesence.json", "assets/vesence/fonts/vesence.png");
      LOGO = register("logo", "assets/vesence/fonts/logo.json", "assets/vesence/fonts/logo.png");
      MON = register("mon", "assets/vesence/fonts/mon_icons.json", "assets/vesence/fonts/mon_icons.png");
      NUC = register("nuc", "assets/vesence/fonts/nuc_icons.json", "assets/vesence/fonts/nuc_icons.png");
      DELUXE = register("deluxe", "assets/vesence/fonts/test.json", "assets/vesence/fonts/test.png");
      ICON = register("icon", "assets/vesence/fonts/icon_2.json", "assets/vesence/fonts/icon_2.png");
      RELAKE = register("relake", "assets/vesence/fonts/relake.json", "assets/vesence/fonts/relake.png");
   }

    public static synchronized void reset() {
      REGISTERED_FONTS.clear();
      FONT_OBJECTS.clear();
      backend = null;
      backendConfigured = false;
      rendererFontsInitialized = false;
   }

    private static void ensureBackendConfigured() {
      if (!backendConfigured || backend == null) {
         throw new IllegalStateException("FontRegistry.initialize(backend, renderer) must be called before use");
      }
   }
}
