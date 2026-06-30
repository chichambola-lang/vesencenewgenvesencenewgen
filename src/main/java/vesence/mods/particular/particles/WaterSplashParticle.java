package vesence.mods.particular.particles;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public class WaterSplashParticle extends BillboardParticle {
   protected final SpriteProvider spriteProvider;
   private final float startScale;

   public WaterSplashParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites, double velocityX, double velocityY, double velocityZ) {
      super(world, x, y, z, velocityX, velocityY, velocityZ, sprites.getFirst());
      this.spriteProvider = sprites;
      this.startScale = 0.08F + this.random.nextFloat() * 0.05F;
      this.maxAge = 18 + this.random.nextInt(8);
      this.collidesWithWorld = false;
      this.scale = this.startScale;
      this.setColor(0.86F, 0.94F, 1.0F);
      this.setAlpha(0.95F);
      this.updateSprite(sprites);
   }

   @Override
   public void tick() {
      if (this.age++ >= this.maxAge) {
         this.markDead();
         return;
      }

      this.velocityY -= 0.02D;
      this.velocityX *= 0.92D;
      this.velocityY *= 0.88D;
      this.velocityZ *= 0.92D;
      this.move(this.velocityX, this.velocityY, this.velocityZ);

      float life = (float) this.age / (float) this.maxAge;
      this.scale = this.startScale * (0.9F + life * 0.7F);
      this.setAlpha(MathHelper.clamp(1.0F - life, 0.0F, 1.0F));
      this.updateSprite(this.spriteProvider);
   }

   @Override
   public BillboardParticle.RenderType getRenderType() {
      return BillboardParticle.RenderType.PARTICLE_ATLAS_TRANSLUCENT;
   }

   public static final class Factory implements ParticleFactory<SimpleParticleType> {
      private final FabricSpriteProvider sprites;

      public Factory(FabricSpriteProvider sprites) {
         this.sprites = sprites;
      }

      @Override
      public Particle createParticle(SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
         return new WaterSplashParticle(world, x, y, z, this.sprites, velocityX, velocityY, velocityZ);
      }
   }
}
