package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.EntityRenderTransform;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows.SubLevelEntityShadowRenderer;
import java.util.List;
import dev.ryanhcode.sable.mixinterface.entity.entity_rendering.EntityRenderStateExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Positions and orientations cross the extraction/submission boundary as state. */
@Mixin(EntityRenderDispatcher.class)
public class LevelRendererMixin {
    @Redirect(method = "distanceToSqr", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$distanceToCamera(final Vec3 camera, final Vec3 position,
                                         @Local(argsOnly = true) final Entity entity) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(entity);
        return camera.distanceToSqr(subLevel == null ? position : subLevel.renderPose().transformPosition(position));
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void sable$extractTransform(final Entity entity, final float partialTick,
                                        final CallbackInfoReturnable<EntityRenderState> cir) {
        final EntityRenderState state = cir.getReturnValue();
        final ClientSubLevel containing = (ClientSubLevel) Sable.HELPER.getContaining(entity);
        Quaternionf containingRotation = null;
        if (containing != null) {
            final Pose3dc renderPose = containing.renderPose(partialTick);
            final Vec3 position = renderPose.transformPosition(new Vec3(state.x, state.y, state.z));
            state.x = position.x;
            state.y = position.y;
            state.z = position.z;
            containingRotation = new Quaternionf(renderPose.orientation());
        } else if (!entity.isPassenger() && Sable.HELPER.getTrackingSubLevel(entity) instanceof final ClientSubLevel tracking) {
            final Vector3d oldLocal = tracking.lastPose().transformPositionInverse(new Vector3d(entity.xOld, entity.yOld, entity.zOld));
            final Vector3d newLocal = tracking.logicalPose().transformPositionInverse(JOMLConversion.toJOML(entity.position()));
            final Vector3d position = new Vector3d(Mth.lerp(partialTick, oldLocal.x, newLocal.x),
                    Mth.lerp(partialTick, oldLocal.y, newLocal.y), Mth.lerp(partialTick, oldLocal.z, newLocal.z));
            tracking.renderPose(partialTick).transformPosition(position);
            state.x = position.x;
            state.y = position.y;
            state.z = position.z;
        }

        Quaternionf entityRotation = null;
        Vec3 eyeOffset = Vec3.ZERO;
        Vec3 eyeInterpolationOffset = Vec3.ZERO;
        if (EntitySubLevelUtil.shouldKick(entity)) {
            final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(entity,
                    subLevel -> ((ClientSubLevel) subLevel).renderPose(partialTick), partialTick,
                    EntitySubLevelRotationHelper.Type.ENTITY);
            if (orientation != null) {
                entityRotation = new Quaternionf(orientation);
                eyeOffset = entity.getEyePosition().subtract(entity.position());
                eyeInterpolationOffset = Sable.HELPER.getEyePositionInterpolated(entity, partialTick).subtract(entity.getEyePosition(partialTick));
            }
        }

        Quaternionf sleepingRotation = null;
        if (entity instanceof final LivingEntity living && living.getBedOrientation() != null && living.getSleepingPos().isPresent()) {
            final SubLevel bedLevel = Sable.HELPER.getContaining(entity.level(), living.getSleepingPos().get());
            if (bedLevel instanceof final ClientSubLevel clientBed) {
                sleepingRotation = new Quaternionf(clientBed.renderPose(partialTick).orientation());
            }
        }
        ((EntityRenderStateExtension) state).sable$setRenderTransform(new EntityRenderTransform(containingRotation,
                entityRotation, sleepingRotation, eyeOffset, eyeInterpolationOffset));
        final EntityRenderStateExtension extension = (EntityRenderStateExtension) state;
        final float strength = (float) Math.max(0, 1 - state.distanceToCameraSq / 256) * extension.sable$getShadowStrength();
        extension.sable$setShadowVertices(strength <= 0 || state.shadowRadius <= 0 ? List.of()
                : SubLevelEntityShadowRenderer.extract(entity, new Vec3(state.x, state.y, state.z), strength, partialTick, state.shadowRadius));
    }
}
