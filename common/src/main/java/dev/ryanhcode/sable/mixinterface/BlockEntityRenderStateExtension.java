package dev.ryanhcode.sable.mixinterface;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public interface BlockEntityRenderStateExtension {
    record PlotTransform(Quaternionfc orientation, Vec3 globalOrigin) {
        public PlotTransform {
            orientation = new Quaternionf(orientation);
        }

        /** Subtract in double precision before PoseStack stores the translation in floats. */
        public Vec3 cameraRelativeOrigin(final Vec3 cameraPosition) {
            return this.globalOrigin.subtract(cameraPosition);
        }

        public Quaternionf relativeCameraOrientation(final Quaternionfc cameraOrientation) {
            return new Quaternionf(this.orientation).conjugate().mul(cameraOrientation);
        }
    }

    @Nullable PlotTransform sable$getPlotTransform();
    void sable$setPlotTransform(@Nullable PlotTransform transform);
}
