package dev.ryanhcode.sable.mixin.entity.entity_leashing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(Leashable.class)
public interface LeashableMixin {

    @WrapOperation(method = "leashDistanceTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getCenter()Lnet/minecraft/world/phys/Vec3;"), require = 2, expect = 2)
    private Vec3 sable$globalLeashCenter(final AABB bounds, final Operation<Vec3> original) {
        return Sable.HELPER.projectOutOfSubLevel(((Entity) (Object) this).level(), original.call(bounds));
    }

    @WrapOperation(method = "getHolderMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getKnownMovement()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$globalLeashMovement(final Entity entity, final Operation<Vec3> original) {
        return sable$toGlobalMovement(entity, original.call(entity));
    }

    @WrapOperation(method = "checkElasticInteractions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getKnownMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$globalLeashedMovement(final Entity entity, final Operation<Vec3> original) {
        return sable$toGlobalMovement(entity, original.call(entity));
    }

    @Unique
    private static Vec3 sable$toGlobalMovement(final Entity entity, final Vec3 movement) {
        final SubLevel subLevel = Sable.HELPER.getContaining(entity);
        return subLevel != null ? subLevel.logicalPose().transformNormal(movement) : movement;
    }

    @WrapOperation(method = "computeElasticInteraction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Leashable;computeDampenedSpringInteraction(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<?> sable$globalLeashSpring(final Vec3 holderAnchor, final Vec3 leashAnchor, final double elasticDistance,
                                                       final Vec3 movement, final Vec3 leashOffset, final Operation<Optional<?>> original,
                                                       @Local(argsOnly = true, ordinal = 0) final Entity leashed,
                                                       @Local(argsOnly = true, ordinal = 1) final Entity holder) {
        final SubLevel subLevel = Sable.HELPER.getContaining(leashed);
        final Vec3 offset = subLevel != null ? subLevel.logicalPose().transformNormal(leashOffset) : leashOffset;
        return original.call(Sable.HELPER.projectOutOfSubLevel(holder.level(), holderAnchor),
                Sable.HELPER.projectOutOfSubLevel(leashed.level(), leashAnchor), elasticDistance, movement, offset);
    }

    @WrapOperation(method = "checkElasticInteractions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void sable$localLeashImpulse(final Entity entity, final Vec3 impulse, final Operation<Void> original) {
        final SubLevel subLevel = Sable.HELPER.getContaining(entity);
        original.call(entity, subLevel != null ? subLevel.logicalPose().transformNormalInverse(impulse) : impulse);
    }
}
