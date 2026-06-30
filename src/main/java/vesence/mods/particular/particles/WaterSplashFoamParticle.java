package vesence.mods.particular.particles;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class WaterSplashFoamParticle extends WaterSplashParticle {
   public WaterSplashFoamParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites, double velocityX, double velocityY, double velocityZ) {
      super(world, x, y, z, sprites, velocityX, velocityY, velocityZ);
      this.setColor(1.0F, 1.0F, 1.0F);
      this.setAlpha(0.85F);
   }

   public static final class Factory implements ParticleFactory<SimpleParticleType> {
      private final FabricSpriteProvider sprites;

      public Factory(FabricSpriteProvider sprites) {
         this.sprites = sprites;
      }

      @Override
      public Particle createParticle(SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
         return new WaterSplashFoamParticle(world, x, y, z, this.sprites, velocityX, velocityY, velocityZ);
      }
   }
}
