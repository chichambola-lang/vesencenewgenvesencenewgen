package vesence.module.impl.visuals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.AttackEvent;
import vesence.event.render.EventRender3D;
import vesence.event.render.EventScreen;
import vesence.module.impl.combat.AttackAura;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

/**
 * KillEffect — полный порт rich.modules.impl.render.KillEffect из automine.
 * Эффект «FragEffect»: расширяющаяся сканирующая волна вокруг убитой цели,
 * взрыв белых частиц, экранная виньетка и последовательность «charm»-звуков.
 *
 * <p>Оригинал использует кастомный GPU depth-scan шейдер; здесь используется
 * геометрический fallback (renderWaveFallback) — визуально идентичен.
 */
@IModule(name = "KillEffect", description = "Эффект убийства с виньеткой, волной и звуками", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class KillEffect extends Module {

    public static KillEffect INSTANCE;

    private static final Identifier VIGNETTE_TEXTURE = Identifier.of("vesence", "images/world/lightarroundscreen.png");
    private static final long BASE_EFFECT_DURATION_MS = 5200L;

    // ── Charm sounds ──
    private static final SoundEvent FRAG_EFFECT_ECHO_MAIN = SoundEvent.of(Identifier.of("vesence", "frag_effect_echo_main"));
    private static final SoundEvent FRAG_EFFECT_KNOCK_MAIN = SoundEvent.of(Identifier.of("vesence", "frag_effect_knock_main"));
    private static final SoundEvent FRAG_EFFECT_PULSE = SoundEvent.of(Identifier.of("vesence", "frag_effect_pulse"));
    private static final SoundEvent FRAG_EFFECT_SPARKS_COLLISION = SoundEvent.of(Identifier.of("vesence", "frag_effect_sparks_collision"));

    // ── Settings ──
    public final SliderSetting speed = new SliderSetting("Скорость", 100.0, 25.0, 200.0, 1.0);
    public final MultiBooleanSetting effectTargets = new MultiBooleanSetting("Цели эффекта",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Друзья", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Животные", true));
    public final BooleanSetting screenOverlay = new BooleanSetting("Экранная картинка", true);
    public final BooleanSetting scanlines = new BooleanSetting("Сканлайны", true);

    // ── State ──
    private boolean animate;
    private float activeEffectSpeedMultiplier = 1.0f;
    private long effectStartTime;

    private final DeathMemoryTracker deathTracker = new DeathMemoryTracker(950L, this::onTrackedKill);
    private final List<ScanWave> waves = new CopyOnWriteArrayList<>();
    private final List<FragParticle> particles = new CopyOnWriteArrayList<>();
    private final List<PendingSound> pendingSounds = new ArrayList<>();
    private vesence.renderengine.render.pipeline.KillEffectScanPipeline scanPipeline;

    // ── Render pipelines ──
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/killeffect_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());
    private static final Identifier GLOW_TEX = Identifier.of("vesence", "textures/targetesp/bloom.png");
    private static final RenderLayer GLOW_LAYER = RenderLayer.of(
            "vesence_killeffect_frag_glow",
            RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(524288).translucent()
                    .texture("Sampler0", GLOW_TEX).build());

    public KillEffect() {
        INSTANCE = this;
        this.addSettings(new Setting[]{this.speed, this.effectTargets, this.screenOverlay, this.scanlines});
    }

    @Override
    public void onDisable() {
        resetState();
        if (this.scanPipeline != null) {
            try { this.scanPipeline.close(); } catch (Exception ignored) {}
            this.scanPipeline = null;
        }
        super.onDisable();
    }

    // ═══ Event: атака — запоминаем цель ══════════════════════════════════════
    @EventInit
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (!target.isAlive() || target.getHealth() <= 0.0f || !shouldTriggerFor(target)) return;
        this.deathTracker.remember(target);
    }

    // ═══ Event: тик — трекинг цели Aura, память смерти, частицы, звуки ═══════
    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }
        LivingEntity auraTarget = AttackAura.target;
        if (auraTarget != null && auraTarget.isAlive() && auraTarget.getHealth() > 0.0f && shouldTriggerFor(auraTarget)) {
            this.deathTracker.remember(auraTarget);
        }
        this.deathTracker.tick();
        tickParticles();
        tickSounds();
    }

    // ═══ Event: 3D рендер — частицы (волны рисуются GPU-шейдером из миксина) ═
    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        if (this.particles.isEmpty()) return;

        MatrixStack ms = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks = event.getTickDelta();

        BufferAllocator alloc = new BufferAllocator(524288);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(alloc);
        try {
            VertexConsumer buffer = immediate.getBuffer(GLOW_LAYER);
            for (FragParticle p : this.particles) {
                p.render(buffer, ms, cameraPos, partialTicks);
            }
            immediate.draw();
        } finally {
            alloc.close();
        }
    }

    /**
     * GPU-рендер сканирующих волн поверх мира. Вызывается из
     * {@code WorldRendererMixin} после отрисовки мира (когда depth-буфер готов).
     * Реконструирует мировые позиции из глубины → рисует сферу-волну на
     * геометрии мира и мобах.
     */
    public void renderScanWaves(org.joml.Matrix4f projection, org.joml.Matrix4f view, Vec3d cameraPos) {
        if (!this.enable || this.waves.isEmpty()) return;
        if (mc.player == null || mc.world == null) return;

        if (this.scanPipeline == null) {
            this.scanPipeline = new vesence.renderengine.render.pipeline.KillEffectScanPipeline();
        }

        int accent = accentColor();
        try {
            for (ScanWave wave : this.waves) {
                wave.renderGpu(this.scanPipeline, projection, view, cameraPos, accent, this.scanlines.get());
            }
        } catch (Exception ignored) {
        }
    }

    // ═══ Event: 2D рендер — виньетка ═════════════════════════════════════════
    @EventInit
    public void onRender2D(EventScreen event) {
        if (!this.screenOverlay.get()) return;
        float effectProgress = effectProgress();
        if (effectProgress <= 0.0f) return;
        float alphaProgress = effectEnvelope(380L, 1100L);
        if (alphaProgress <= 0.0f) return;

        float scaleAnimation = quintOut(effectProgress);
        float scaleProgress = Math.min((scaleAnimation > 0.5f ? 1.0f - scaleAnimation : scaleAnimation) * 4.25f, 1.0f);
        float width = event.viewportWidth();
        float height = event.viewportHeight();
        float extendX = width * 0.5f * (1.0f - scaleProgress);
        float extendY = height * 0.5f * (1.0f - scaleProgress);

        int baseColor = lighten(accentColor(), 0.22f);
        int color = ColorUtil.interpolate(baseColor, 0xFFFFFFFF, effectProgress);
        int alpha = Math.round(80.0f * alphaProgress);
        int tint = (alpha << 24) | (color & 0x00FFFFFF);

        event.renderer().drawImage(VIGNETTE_TEXTURE, -extendX, -extendY, width + extendX * 2.0f, height + extendY * 2.0f, tint);
    }

    // ═══ Kill callback ═══════════════════════════════════════════════════════
    private void onTrackedKill(LivingEntity entity) {
        if (!matchesEffectTarget(entity)) return;
        Vec3d killPosition = entity.getEyePos();
        float effectSpeed = speedMultiplier();
        restartEffect(effectSpeed);
        this.waves.add(new ScanWave(killPosition, effectSpeed));
        spawnBurst(killPosition, 480, 900, 1.9f, 96, 0.012f);
        scheduleCharmSounds();
    }

    private void scheduleCharmSounds() {
        long offset = scaleDuration(150L);
        scheduleSound(FRAG_EFFECT_PULSE, 1.0f, 0L);
        scheduleSound(FRAG_EFFECT_KNOCK_MAIN, 1.0f, offset);
        scheduleSound(FRAG_EFFECT_SPARKS_COLLISION, 0.2f, offset * 2L);
        scheduleSound(FRAG_EFFECT_ECHO_MAIN, 0.6f, offset * 3L);
    }

    private void restartEffect(float effectSpeedMultiplier) {
        this.animate = true;
        this.activeEffectSpeedMultiplier = MathHelper.clamp(effectSpeedMultiplier, 0.25f, 2.0f);
        this.effectStartTime = System.currentTimeMillis();
    }

    // ═══ Target filtering ════════════════════════════════════════════════════
    private boolean shouldTriggerFor(LivingEntity entity) {
        return entity != null && entity != mc.player && entity.isAlive()
                && entity.getHealth() > 0.0f && matchesEffectTarget(entity);
    }

    private boolean matchesEffectTarget(LivingEntity entity) {
        if (entity == null || entity == mc.player) return false;
        if (entity instanceof PlayerEntity player) {
            if (FriendStorage.isFriend(player.getName().getString())) {
                return this.effectTargets.get("Друзья");
            }
            return this.effectTargets.get("Игроки");
        }
        if (entity instanceof AnimalEntity) {
            return this.effectTargets.get("Животные");
        }
        if (entity instanceof MobEntity) {
            return this.effectTargets.get("Мобы");
        }
        return false;
    }

    // ═══ Effect envelope math ════════════════════════════════════════════════
    private float effectProgress() {
        long duration = effectDurationMs();
        float progress = this.animate ? Math.min((float) (System.currentTimeMillis() - effectStartTime) / (float) duration, 1.0f) : 0.0f;
        if (progress >= 1.0f) this.animate = false;
        return progress;
    }

    private float effectEnvelope(long attackMs, long releaseMs) {
        if (!this.animate) return 0.0f;
        long duration = effectDurationMs();
        long elapsed = Math.min(System.currentTimeMillis() - effectStartTime, duration);
        if (elapsed <= 0L) return 0.0f;
        long attack = MathHelper.clamp(scaleDuration(attackMs), 1L, duration);
        long release = MathHelper.clamp(scaleDuration(releaseMs), 1L, duration);
        long releaseStart = Math.max(duration - release, attack);
        if (elapsed < attack) return sineOut((float) elapsed / (float) attack);
        if (elapsed >= releaseStart) {
            float rp = MathHelper.clamp((float) (elapsed - releaseStart) / (float) release, 0.0f, 1.0f);
            return 1.0f - sineInOut(rp);
        }
        return 1.0f;
    }

    private void resetState() {
        this.animate = false;
        this.activeEffectSpeedMultiplier = speedMultiplier();
        this.pendingSounds.clear();
        this.deathTracker.clear();
        this.particles.clear();
        this.waves.clear();
    }

    private int accentColor() {
        int c = ColorUtil.fade();
        if ((c >> 24 & 0xFF) == 0) c |= 0xFF000000;
        return c;
    }

    private float speedMultiplier() {
        return MathHelper.clamp((float) (double) this.speed.get() / 100.0f, 0.25f, 2.0f);
    }

    private long effectDurationMs() {
        return scaleDuration(BASE_EFFECT_DURATION_MS);
    }

    private long scaleDuration(long durationMs) {
        if (durationMs <= 0L) return 0L;
        return Math.max(1L, Math.round(durationMs / MathHelper.clamp(this.activeEffectSpeedMultiplier, 0.25f, 2.0f)));
    }

    // ═══ Sounds ══════════════════════════════════════════════════════════════
    private void scheduleSound(SoundEvent sound, float volume, long delayMs) {
        if (sound == null) return;
        if (delayMs <= 0L) {
            playSound(sound, volume);
            return;
        }
        this.pendingSounds.add(new PendingSound(sound, volume, System.currentTimeMillis() + delayMs));
    }

    private void tickSounds() {
        long now = System.currentTimeMillis();
        Iterator<PendingSound> it = this.pendingSounds.iterator();
        while (it.hasNext()) {
            PendingSound s = it.next();
            if (now < s.playAt) continue;
            playSound(s.sound, s.volume);
            it.remove();
        }
    }

    private void playSound(SoundEvent sound, float volume) {
        if (mc.getSoundManager() == null) return;
        SoundInstance instance = PositionedSoundInstance.ui(sound, 1.0f, volume);
        mc.getSoundManager().play(instance);
    }

    // ═══ Particles ═══════════════════════════════════════════════════════════
    private void spawnBurst(Vec3d center, int lifetimeMin, int lifetimeMax, float maxFlight, int count, float gravity) {
        float maxXz = maxFlight / (lifetimeMin / 50.0f) * 1.12f;
        float maxY = maxFlight / (lifetimeMin / 50.0f) * 0.31f;
        for (int i = 0; i < count; i++) {
            this.particles.add(new FragParticle(center, lifetimeMin + ThreadLocalRandom.current().nextInt(lifetimeMax - lifetimeMin), maxXz, maxY, gravity));
        }
    }

    private void tickParticles() {
        Iterator<FragParticle> it = this.particles.iterator();
        while (it.hasNext()) {
            FragParticle p = it.next();
            p.tick();
            if (p.isExpired()) it.remove();
        }
    }

    // ═══ Easings ═════════════════════════════════════════════════════════════
    private static float quintOut(float v) {
        float t = MathHelper.clamp(v, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - t, 5.0);
    }

    private static float expoInOut(float v) {
        float t = MathHelper.clamp(v, 0.0f, 1.0f);
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return t < 0.5f ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0f
                : (2.0f - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0f;
    }

    private static float sineOut(float v) {
        float t = MathHelper.clamp(v, 0.0f, 1.0f);
        return (float) Math.sin(t * Math.PI / 2.0);
    }

    private static float sineInOut(float v) {
        float t = MathHelper.clamp(v, 0.0f, 1.0f);
        return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
    }

    private static int lighten(int color, float amount) {
        float c = MathHelper.clamp(amount, 0.0f, 1.0f);
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >> 24 & 0xFF;
        r = (int) MathHelper.clamp(r + (255 - r) * c, 0, 255);
        g = (int) MathHelper.clamp(g + (255 - g) * c, 0, 255);
        b = (int) MathHelper.clamp(b + (255 - b) * c, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ═══ ScanWave — расширяющаяся волна-кольцо ═══════════════════════════════
    private static final class ScanWave {
        private final Vec3d center;
        private final long startedAt = System.currentTimeMillis();
        private final float growthDuration;

        ScanWave(Vec3d center, float speedMultiplier) {
            this.center = center;
            this.growthDuration = Math.max(280.0f, 5600.0f / MathHelper.clamp(speedMultiplier, 0.25f, 2.0f));
        }

        boolean isFinished() {
            return (System.currentTimeMillis() - startedAt) >= growthDuration;
        }

        private float timeProgress(float duration) {
            return MathHelper.clamp((float) (System.currentTimeMillis() - startedAt) / duration, 0.0f, 1.0f);
        }

        private static float waveFn(float v) {
            v = MathHelper.clamp(v, 0.0f, 1.0f);
            return v > 0.5f ? (1.0f - v) * 2.0f : v * 2.0f;
        }

        private float viewDistance() {
            int rd = mc.options.getViewDistance().getValue();
            return Math.max(96.0f, (rd + 1) * 16.0f);
        }

        void renderGpu(vesence.renderengine.render.pipeline.KillEffectScanPipeline pipeline,
                       org.joml.Matrix4f projection, org.joml.Matrix4f view, Vec3d cameraPos,
                       int accent, boolean scanlines) {
            float vd = viewDistance();

            int outer = lighten(accent, 0.45f);
            int mid = lighten(accent, 0.18f);
            int inner = darken(accent, 0.18f);
            int scanCol = scanlines ? lighten(accent, 0.6f) : 0;
            int flickOuter = lighten(accent, 0.7f);
            int flickMid = ColorUtil.interpolate(accent, 0xFFFFFFFF, 0.28f);
            int flickInner = darken(accent, 0.08f);
            int flickScan = scanlines ? darken(accent, 0.35f) : 0;

            // Основная волна
            float baseRadius = 1.0f + (vd - 1.0f) * quintOut(timeProgress(growthDuration));
            float baseWave = Math.min(waveFn(1.0f - timeProgress(growthDuration)) * 1.75f, 1.0f);
            pipeline.renderWave(mc, projection, view, cameraPos, center, baseRadius, baseRadius, 30.0f,
                    scaleColor(outer, baseWave), scaleColor(mid, baseWave), scaleColor(inner, baseWave), scaleColor(scanCol, baseWave));

            // Flick-волна (быстрее, резче)
            float flickRadius = 1.0f + (vd - 1.0f) * expoInOut(timeProgress(growthDuration * 0.5f));
            float flickAlphaProg = 1.0f - timeProgress(growthDuration * 0.5f);
            float flickWave = Math.min(waveFn(flickAlphaProg) * 2.0f, 1.0f);
            pipeline.renderWave(mc, projection, view, cameraPos, center, flickRadius, flickRadius / 1.5f, 40.0f,
                    scaleColor(flickOuter, flickAlphaProg), scaleColor(flickMid, flickWave), scaleColor(flickInner, flickWave), scaleColor(flickScan, flickWave));
        }

        private static int scaleColor(int color, float factor) {
            float c = MathHelper.clamp(factor, 0.0f, 1.0f);
            int r = (int) ((color >> 16 & 0xFF) * c);
            int g = (int) ((color >> 8 & 0xFF) * c);
            int b = (int) ((color & 0xFF) * c);
            int a = (int) (255.0f * c);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int darken(int color, float amount) {
            float f = 1.0f - MathHelper.clamp(amount, 0.0f, 1.0f);
            int r = (int) ((color >> 16 & 0xFF) * f);
            int g = (int) ((color >> 8 & 0xFF) * f);
            int b = (int) ((color & 0xFF) * f);
            int a = color >> 24 & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    // ═══ FragParticle — белая искра (порт FragEffectParticle) ════════════════
    private static final class FragParticle {
        private Vec3d pos;
        private Vec3d prevPos;
        private Vec3d motion;
        private final int maxTime;
        private final float gravity;
        private final long startTime = System.currentTimeMillis();
        private final float baseSize;
        private final float baseBright;

        FragParticle(Vec3d spawn, int maxTime, float xzSpeedMax, float ySpeedMax, float gravity) {
            this.pos = spawn;
            this.prevPos = spawn;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            this.motion = new Vec3d(
                    (r.nextFloat() - 0.5f) * 2f * xzSpeedMax,
                    (r.nextFloat() - 0.5f) * 2f * ySpeedMax - ySpeedMax / 4.0f + ySpeedMax / 4.0f,
                    (r.nextFloat() - 0.5f) * 2f * xzSpeedMax);
            this.maxTime = maxTime;
            this.gravity = gravity;
            this.baseSize = 0.035f + r.nextFloat() * 0.045f;
            this.baseBright = expoInOut(r.nextFloat());
        }

        void tick() {
            this.prevPos = this.pos;
            this.pos = this.pos.add(this.motion);
            this.motion = new Vec3d(this.motion.x * 0.99, (this.motion.y - gravity) * 0.994, this.motion.z * 0.99);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > maxTime;
        }

        private float alpha() {
            return 1.0f - Math.min((float) (System.currentTimeMillis() - startTime) / maxTime, 1.0f);
        }

        void render(VertexConsumer buffer, MatrixStack ms, Vec3d cameraPos, float partialTicks) {
            float a = alpha();
            if (a <= 0.001f) return;
            Vec3d rp = new Vec3d(
                    MathHelper.lerp(partialTicks, prevPos.x, pos.x),
                    MathHelper.lerp(partialTicks, prevPos.y, pos.y),
                    MathHelper.lerp(partialTicks, prevPos.z, pos.z));
            int bright = (int) (baseBright * 255) & 0xFF;
            int color = 0xFF000000 | (bright << 16) | (bright << 8) | bright;

            ms.push();
            ms.translate(rp.x - cameraPos.x, rp.y - cameraPos.y, rp.z - cameraPos.z);
            ms.multiply(mc.gameRenderer.getCamera().getRotation());
            org.joml.Matrix4f matrix = ms.peek().getPositionMatrix();
            appendGlow(buffer, matrix, baseSize, color, a * 0.38f);
            appendGlow(buffer, matrix, baseSize * 0.22f, color, a * 0.9f);
            ms.pop();
        }

        private void appendGlow(VertexConsumer b, org.joml.Matrix4f m, float size, int color, float alphaMul) {
            float half = size * 0.5f;
            int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, bl = color & 0xFF;
            int a = (int) MathHelper.clamp(255.0f * alphaMul, 0, 255);
            if (a <= 1) return;
            b.vertex(m, -half, -half, 0.0f).texture(0.0f, 1.0f).color(r, g, bl, a);
            b.vertex(m, -half, half, 0.0f).texture(0.0f, 0.0f).color(r, g, bl, a);
            b.vertex(m, half, half, 0.0f).texture(1.0f, 0.0f).color(r, g, bl, a);
            b.vertex(m, half, -half, 0.0f).texture(1.0f, 1.0f).color(r, g, bl, a);
        }
    }

    // ═══ DeathMemoryTracker — детект смерти (порт) ═══════════════════════════
    private static final class DeathMemoryTracker {
        private static final int MAX_MEMORIES = 12;
        private final long maxMemoryMs;
        private final java.util.function.Consumer<LivingEntity> onTrigger;
        private final List<DeathMemory> memories = new ArrayList<>();

        DeathMemoryTracker(long maxMemoryMs, java.util.function.Consumer<LivingEntity> onTrigger) {
            this.maxMemoryMs = maxMemoryMs;
            this.onTrigger = onTrigger;
        }

        void remember(LivingEntity entity) {
            if (entity == mc.player || entity.age < 2 || !entity.isAlive()) return;
            for (DeathMemory m : this.memories) {
                if (m.matches(entity)) {
                    m.refresh(entity, this.maxMemoryMs);
                    return;
                }
            }
            if (this.memories.size() >= MAX_MEMORIES) this.memories.remove(0);
            this.memories.add(new DeathMemory(entity, this.maxMemoryMs));
        }

        void tick() {
            Iterator<DeathMemory> it = this.memories.iterator();
            while (it.hasNext()) {
                DeathMemory m = it.next();
                if (!m.isActive()) { it.remove(); continue; }
                if (m.shouldDiscard()) { it.remove(); continue; }
                if (!m.triggered && m.shouldTrigger()) {
                    this.onTrigger.accept(m.entity);
                    m.markTriggered();
                }
                if (!m.isActive()) it.remove();
            }
        }

        void clear() {
            this.memories.clear();
        }

        private static final class DeathMemory {
            private LivingEntity entity;
            private long expiresAt;
            private boolean triggered;

            DeathMemory(LivingEntity entity, long durationMs) {
                this.entity = entity;
                this.expiresAt = System.currentTimeMillis() + durationMs;
            }

            boolean matches(LivingEntity other) {
                return this.entity.getId() == other.getId();
            }

            void refresh(LivingEntity entity, long durationMs) {
                if (isConfirmedDead(this.entity)) return;
                this.entity = entity;
                this.triggered = false;
                this.expiresAt = System.currentTimeMillis() + durationMs;
            }

            boolean shouldTrigger() {
                return isConfirmedDead(this.entity);
            }

            boolean shouldDiscard() {
                if (isConfirmedDead(this.entity)) return false;
                if (mc.world == null || this.entity.isRemoved()) return true;
                return mc.world.getEntityById(this.entity.getId()) == null;
            }

            void markTriggered() {
                this.triggered = true;
                this.expiresAt = System.currentTimeMillis() + 300L;
            }

            boolean isActive() {
                return System.currentTimeMillis() <= this.expiresAt;
            }

            private static boolean isConfirmedDead(LivingEntity e) {
                return e != null && (e.getHealth() <= 0.0f || e.isDead() || e.deathTime > 0);
            }
        }
    }

    private record PendingSound(SoundEvent sound, float volume, long playAt) {
    }
}
