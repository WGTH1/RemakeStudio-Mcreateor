<#-- @formatter:off -->
package ${package}.client.particle;

import ${package}.aaaparticle.${name}AAAParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;

public class ${name}AAAParticleProvider implements ParticleProvider<SimpleParticleType> {
	@Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
		return new TriggerParticle(level, x, y, z);
	}

	private static class TriggerParticle extends NoRenderParticle {
		private boolean spawned;

		protected TriggerParticle(ClientLevel level, double x, double y, double z) {
			super(level, x, y, z);
			this.lifetime = 1;
		}

		@Override public void tick() {
			if (!this.spawned) {
				// Vanilla particles do not carry a source entity; never guess a nearby player.
				${name}AAAParticle.spawn(this.level, this.x, this.y, this.z);
				this.spawned = true;
			}
			this.remove();
		}
	}
}
<#-- @formatter:on -->
