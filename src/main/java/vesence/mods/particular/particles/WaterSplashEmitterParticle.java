package vesence.mods.particular.particles;

import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import vesence.mods.particular.ParticularParticleTypes;

public class WaterSplashEmitterParticle extends NoRenderParticle {
   private final float width;
   private final float speed;
   private boolean spawned;

   public WaterSplashEmitterParticle(ClientWorld world, double x, double y, double z, double width, double speed, double ignored) {
      super(world, x, y, z, 0.0D, 0.0D, 0.0D);
      this.width = Math.max(0.2F, (float) width);
      this.speed = Math.max(0.0F, (float) speed);
      this.maxAge = 4;
      this.collidesWithWorld = false;
   }

   @Override
   public void tick() {
      if (this.age++ >= this.maxAge) {
         this.markDead();
         return;
      }
      if (!this.spawned) {
         this.spawned = true;
         this.spawnChildren();
      }
   }

   private void spawnChildren() {
      int ringCount = Math.max(8, MathHelper.ceil(this.width * 10.0F));
      double radius = Math.max(0.18D, this.width * 0.25D);
      double splashUp = 0.06D + this.speed * 0.05D;

      for (int i = 0; i < ringCount; i++) {
         double angle = (Math.PI * 2.0D * i) / ringCount;
         double px = this.x + Math.cos(angle) * radius;
         double pz = this.z + Math.sin(angle) * radius;
         double vx = Math.cos(angle) * (0.02D + this.speed * 0.02D);
         double vz = Math.sin(angle) * (0.02D + this.speed * 0.02D);

         this.world.addParticleClient(ParticularParticleTypes.WATER_SPLASH, px, this.y, pz, vx, splashUp, vz);
         this.world.addParticleClient(ParticularParticleTypes.WATER_SPLASH_FOAM, px, this.y + 0.03D, pz, vx * 0.55D, splashUp * 0.35D, vz * 0.55D);
      }

      this.world.addParticleClient(ParticularParticleTypes.WATER_SPLASH_RING, this.x, this.y, this.z, 0.0D, 0.02D + this.speed * 0.01D, 0.0D);

      int droplets = Math.max(3, MathHelper.ceil(this.width * 2.0F));
      for (int i = 0; i < droplets; i++) {
         double vx = (this.random.nextDouble() - 0.5D) * 0.04D;
         double vy = 0.05D + this.random.nextDouble() * 0.05D + this.speed * 0.03D;
         double vz = (this.random.nextDouble() - 0.5D) * 0.04D;
         this.world.addParticleClient(ParticleTypes.FALLING_WATER, this.x, this.y + 0.02D, this.z, vx, vy, vz);
      }
   }

   public static final class Factory implements ParticleFactory<SimpleParticleType> {
      @Override
      public Particle createParticle(SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
         return new WaterSplashEmitterParticle(world, x, y, z, velocityX, velocityY, velocityZ);
      }
   }
}
