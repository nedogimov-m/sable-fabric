package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.LivingEntityStickExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Sable diagnostics to the native per-frame gizmo collection. */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "showHitboxes", at = @At("TAIL"))
    private void sable$showPlotHitboxes(final Entity entity, final float partialTick,
                                        final boolean serverSide, final CallbackInfo ci) {
        if (serverSide) return;
        final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(entity);
        if (tracking instanceof final ClientSubLevel clientSubLevel) {
            final Quaterniondc custom = EntitySubLevelUtil.getCustomEntityOrientation(entity, partialTick);
            final Quaterniond rotation = new Quaterniond(custom == null ? JOMLConversion.QUAT_IDENTITY : custom)
                    .rotateY(SubLevelEntityCollision.getHitBoxYaw(clientSubLevel.renderPose()));
            final Vec3 origin = Sable.HELPER.getEyePositionInterpolated(entity, partialTick).subtract(0, entity.getEyeHeight(), 0);
            final AABB bounds = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
            sable$orientedBox(bounds, origin, entity.getEyeHeight(), rotation);
        }

        final EntityStickExtension extension = (EntityStickExtension) entity;
        final Vec3 plotPosition = extension.sable$getPlotPosition();
        if (plotPosition == null) return;
        final SubLevel containing = Sable.HELPER.getContaining(entity.level(), plotPosition);
        if (!(containing instanceof final ClientSubLevel clientSubLevel)) return;
        final Vec3 projected = clientSubLevel.renderPose(partialTick).transformPosition(plotPosition);
        Gizmos.cuboid(entity.getType().getSpawnAABB(projected), GizmoStyle.stroke(0x3300ff00));
        if (entity instanceof final LivingEntityStickExtension living) {
            final Vec3 target = clientSubLevel.renderPose(partialTick).transformPosition(living.sable$getLerpTarget());
            Gizmos.cuboid(entity.getType().getSpawnAABB(target), GizmoStyle.stroke(0x33ff00ff));
        }
    }

    @Unique
    private static void sable$orientedBox(final AABB bounds, final Vec3 origin, final double eyeHeight,
                                          final Quaterniondc rotation) {
        final Vec3[] corners = new Vec3[8];
        for (int i = 0; i < corners.length; i++) {
            final Vector3d corner = new Vector3d((i & 1) == 0 ? bounds.minX : bounds.maxX,
                    ((i & 2) == 0 ? bounds.minY : bounds.maxY) - eyeHeight,
                    (i & 4) == 0 ? bounds.minZ : bounds.maxZ);
            rotation.transform(corner);
            corners[i] = origin.add(corner.x, corner.y + eyeHeight, corner.z);
        }
        for (int i = 0; i < corners.length; i++) {
            for (int axis = 1; axis <= 4; axis <<= 1) {
                if ((i & axis) == 0) Gizmos.line(corners[i], corners[i | axis], 0x66ffff00);
            }
        }
    }
}
