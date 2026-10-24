package dev.ryanhcode.sable.mixin.particle;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Particle creation and block-hit particles now belong to ClientLevel. */
@Mixin(ClientLevel.class)
public class LevelRendererMixin {
    @Redirect(method = "doAddParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(DDD)D"))
    private double sable$addParticleInternal(final Vec3 vec, final double x, final double y, final double z) {
        return Sable.HELPER.distanceSquaredWithSubLevels((ClientLevel) (Object) this, vec, x, y, z);
    }

    @Redirect(method = "addBreakingParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/TerrainParticle;setPower(F)Lnet/minecraft/client/particle/Particle;"))
    private Particle sable$addCrackParticle(final TerrainParticle particle, final float v, @Local(argsOnly = true) final BlockPos pos, @Local final BlockState state) {
        final Vec3 particlePosition = new Vec3(particle.x, particle.y, particle.z);

        final SubLevel subLevel = Sable.HELPER.getContaining((ClientLevel) (Object) this, particlePosition);
        if (subLevel != null) {
            final Vec3 velocity = new Vec3(particle.xd, particle.yd, particle.zd);
            final Vec3 globalVelocity = subLevel.logicalPose().transformNormal(velocity);

            particle.xd = globalVelocity.x;
            particle.yd = globalVelocity.y;
            particle.zd = globalVelocity.z;

            particle.setPower(v);

            final Vec3 localVelocity = subLevel.logicalPose().transformNormalInverse(new Vec3(particle.xd, particle.yd, particle.zd));

            particle.xd = localVelocity.x;
            particle.yd = localVelocity.y;
            particle.zd = localVelocity.z;
            ((ParticleExtension) particle).sable$setTrackingSubLevel((ClientSubLevel) subLevel, particlePosition);

            return particle;
        } else {
            return particle.setPower(v);
        }
    }

}
