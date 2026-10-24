package dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** Pose values copied during extraction; submission never queries a live entity. */
public record EntityRenderTransform(@Nullable Quaternionfc containingOrientation,
                                    @Nullable Quaternionfc entityOrientation,
                                    @Nullable Quaternionfc sleepingOrientation,
                                    Vec3 eyeOffset, Vec3 eyeInterpolationOffset) {
    public static final EntityRenderTransform EMPTY = new EntityRenderTransform(null, null, null, Vec3.ZERO, Vec3.ZERO);

    public EntityRenderTransform {
        containingOrientation = containingOrientation == null ? null : new Quaternionf(containingOrientation);
        entityOrientation = entityOrientation == null ? null : new Quaternionf(entityOrientation);
        sleepingOrientation = sleepingOrientation == null ? null : new Quaternionf(sleepingOrientation);
    }

    public void applyContaining(final PoseStack pose, final double x, final double y, final double z) {
        if (this.containingOrientation == null) return;
        pose.translate(x, y, z);
        pose.rotate(this.containingOrientation);
        pose.translate(-x, -y, -z);
    }

    public void applyEntity(final PoseStack pose) {
        if (this.entityOrientation == null) return;
        pose.translate(this.eyeInterpolationOffset);
        pose.translate(this.eyeOffset);
        pose.rotate(this.entityOrientation);
        pose.translate(-this.eyeOffset.x, -this.eyeOffset.y, -this.eyeOffset.z);
    }

    public Quaternionf relativeCameraOrientation(final Quaternionfc cameraOrientation) {
        final Quaternionf orientation = new Quaternionf(cameraOrientation);
        if (this.containingOrientation != null) {
            orientation.premul(new Quaternionf(this.containingOrientation).conjugate());
        }
        if (this.entityOrientation != null) {
            orientation.premul(new Quaternionf(this.entityOrientation).conjugate());
        }
        return orientation;
    }
}
