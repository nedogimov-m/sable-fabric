package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(Entity.class)
public abstract class EntityMixin  {

    @Shadow private Level level;

    // Native calculateViewVector is context-free static math. These two instance
    // dispatchers own the entity and call it independently, so each rotates once.
    @ModifyReturnValue(method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 sable$rotateViewVector(final Vec3 original, final float partialTicks) {
        return this.sable$rotateEntityView(original, partialTicks);
    }

    @ModifyReturnValue(method = "getLookAngle()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 sable$rotateLookAngle(final Vec3 original) {
        return this.sable$rotateEntityView(original, 1.0f);
    }

    @Unique
    private Vec3 sable$rotateEntityView(final Vec3 original, final float partialTicks) {
        final Function<SubLevel, Pose3dc> provider;

        if (this.level instanceof final LevelPoseProviderExtension levelPoseProvider) {
            provider = levelPoseProvider::sable$getPose;
        } else {
            provider = SubLevel::logicalPose;
        }

        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation((Entity) (Object) this, provider, partialTicks, EntitySubLevelRotationHelper.Type.CAMERA);

        if (orientation != null) {
            return JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(original)));
        }

        return original;
    }

}
