package vesence;

import java.io.File;

import lombok.Generated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.particle.SimpleParticleType;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.impl.EventChangeWorld;
import vesence.event.render.RenderEvent;
import vesence.mods.particular.ParticularParticleTypes;
import vesence.mods.particular.particles.WaterSplashEmitterParticle;
import vesence.mods.particular.particles.WaterSplashFoamParticle;
import vesence.mods.particular.particles.WaterSplashParticle;
import vesence.mods.particular.particles.WaterSplashRingParticle;
import vesence.module.api.GuiManager;
import vesence.module.api.ModuleManager;
import vesence.module.impl.combat.auraComponent.neural.AimCaptureHud;
import vesence.module.impl.combat.auraComponent.rotationComponent.ComponentManager;

import vesence.renderengine.GlContextManager;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.GlBackend;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.cfg.ConfigManager;
import vesence.utils.commands.CommandBootstrap;
import vesence.utils.commands.CommandHandler;
import vesence.utils.config.friend.FriendManager;
import vesence.utils.render.animation.util.AnimationSystem;

import vesence.utils.render.ResourceManager;
import vesence.utils.render.text.FontRegistry;
import vesence.ui.clickgui.GuiClient;
import vesence.utils.render.math.animation.anim2.Animation;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim2.Easing;

@Environment(EnvType.CLIENT)
public class Vesence implements ClientModInitializer {
    public static Vesence get;
    public ModuleManager manager;
    public final String name = "Vesence Recode";
    public static Animation alpha = new Animation(Easing.EASE_OUT_SINE, 1500L);
    public static Animation2 animation = new Animation2();
    public static Animation2 alphaPC = new Animation2();
    public final String version = "v1";
    public final File root = new File("C:\\", "Vesence\\");
    public static final String rootRes = "vesence";
    private static volatile boolean modInitialized = false;
    private static volatile boolean initialized = false;
    public GuiManager guiManager;
    public FriendManager friendManager;
    public ComponentManager componentManager;
    public ConfigManager configManager;
    private static GlBackend backend;
    private static Renderer2D renderer2D;
    private static volatile boolean fontsInitialized = false;
    private static volatile long renderFrameId = 0L;

    @Generated
    public static boolean isModInitialized() {
        return modInitialized;
    }

    @Generated
    public static boolean areFontsInitialized() {
        return initialized;
    }

    @Generated
    public static Renderer2D getRenderer() {
        return renderer2D;
    }

    @Generated
    public static long getRenderFrameId() {
        return renderFrameId;
    }

    @Generated
    public ModuleManager getManager() {
        return this.manager;
    }

    @Generated
    public String getName() {
        return this.name;
    }

    @Generated
    public String getVersion() {
        return this.version;
    }

    @Generated
    public File getRoot() {
        return this.root;
    }

    @Generated
    public String getRootRes() {
        return this.rootRes;
    }

    @Generated
    public GuiManager getGuiManager() {
        return this.guiManager;
    }

    @Generated
    public ComponentManager getComponentManager() {
        return this.componentManager;
    }

    @Generated
    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    @Generated
    public FriendManager getFriendManager() {
        return this.friendManager;
    }

    @Generated
    public void setManager(ModuleManager manager) {
        this.manager = manager;
    }

    @Generated
    public void setGuiManager(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Generated
    public void setComponentManager(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    @Generated
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Generated
    public void setFriendManager(FriendManager friendManager) {
        this.friendManager = friendManager;
    }

    public void onInitializeClient() {
        get = this;

        vesence.module.impl.visuals.custompet.CustomPetRegistry.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                vesence.module.impl.visuals.custompet.CustomPetRegistry.CUSTOM_PET,
                vesence.module.impl.visuals.custompet.CustomPetRenderer::new);

        ParticularParticleTypes.register();
        ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
        registry.register(ParticularParticleTypes.WATER_SPLASH, WaterSplashParticle.Factory::new);
        registry.register(ParticularParticleTypes.WATER_SPLASH_FOAM, WaterSplashFoamParticle.Factory::new);
        registry.register(ParticularParticleTypes.WATER_SPLASH_RING, WaterSplashRingParticle.Factory::new);
        registry.register(ParticularParticleTypes.WATER_SPLASH_EMITTER, (ParticleFactory<SimpleParticleType>)(type, world, x, y, z, vx, vy, vz, random) ->
              new WaterSplashEmitterParticle(world, x, y, z, vx, vy, vz));

        manager = new ModuleManager();
        manager.init();

        // Инициализация движка живых рук (порт Hold My Items)
        vesence.hmi.LuaTestHMI.init();

        friendManager = new FriendManager();
        FriendManager.init();
        componentManager = new ComponentManager();
        componentManager.init();

        configManager = new ConfigManager();
        guiManager = new GuiManager();
        guiManager.init();

        CommandBootstrap.initialize();
        CommandHandler.getInstance().initialize();
        EventManager.register(CommandHandler.getInstance());
        EventManager.register(new AimCaptureHud());
        vesence.utils.waypoint.WaypointManager.getInstance().load();
        EventManager.register(vesence.utils.waypoint.WaypointManager.getInstance());
        GuiClient.registerEventHandlers();
        vesence.ui.MainScreen.registerEventHandler();
        EventManager.register(this);

        if (configManager != null) {
            configManager.load();
            if (configManager.findConfig("default") != null) {
                configManager.loadConfig("default");
            } else {
                configManager.saveConfig("default");
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (configManager != null) {
                configManager.autoSave();
            }
        }));

        modInitialized = true;
    }

    public static void ensureRendererInitialized() {
        GlContextManager.onRenderFrame();
        if (!initialized) {
            onInit();
        }
    }

    private static synchronized void onInit() {
        if (!initialized) {
            backend = new GlBackend();
            renderer2D = new Renderer2D(backend);
            GlContextManager.initialize();
            FontRegistry.initialize(backend, renderer2D);
            initialized = true;
            System.out.println("[Vesence] MSDF font system initialized successfully");
        }
    }

    public static void invalidateGlContext() {
        System.out.println("[Vesence] OpenGL context lost - invalidating all cached GL resources");
        if (backend != null) {
            try { backend.destroy(); } catch (Exception e) { System.err.println("[Vesence] Error destroying backend: " + e.getMessage()); }
            backend = null;
        }
        if (renderer2D != null) {
            renderer2D.reset();
            renderer2D = null;
        }
        ResourceManager.clearCache();
        Renderer2D.clearImageTextureCache();
        FontRegistry.reset();

        initialized = false;
        fontsInitialized = false;
    }

    public static void onRender() {
        GlContextManager.onRenderFrame();
        if (modInitialized) {
            GlState.Snapshot snapshot = GlState.push();
            try {
                ensureRendererInitialized();
                if (!initialized || renderer2D == null) {
                    return;
                }

                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null || client.getWindow() == null) {
                    return;
                }

                int width = client.getWindow().getFramebufferWidth();
                int height = client.getWindow().getFramebufferHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }

                renderFrameId++;
                AnimationSystem.getInstance().tick();
                boolean rendererBegun = false;

                try {
                    renderer2D.begin(width, height);
                    rendererBegun = true;
                    try {
                        EventManager.call(new RenderEvent(client, renderer2D, FontRegistry.SF_MEDIUM, width, height));
                    } finally {
                        if (rendererBegun) {
                            renderer2D.end();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Vesence] Error in onRender: " + e.getMessage());
                } finally {
                }
            } finally {
                GlState.pop(snapshot);
            }
        }
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        if (configManager != null) {
            configManager.autoSave();
            System.out.println("[Vesence] Config auto-saved on world change");
        }
    }
}
