package dev.ryanhcode.sable.mixinterface;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockEntityPlotTransformTest {
    @Test
    void cameraRelativeTranslationPreservesFractionsAtLargePlotCoordinates() {
        final Vec3 camera = new Vec3(12.125, 64.625, -3.75);
        final Vec3 worldOrigin = new Vec3(12.375, 64.9375, -3.5625);
        final var transform = new BlockEntityRenderStateExtension.PlotTransform(new Quaternionf(), worldOrigin);
        final PoseStack pose = new PoseStack();
        pose.translate(transform.cameraRelativeOrigin(camera));
        assertVector(new Vector3f(.25f, .3125f, .1875f), pose.last().pose().transformPosition(new Vector3f()));

        // Reconstruct the old path: native plot translation followed by a world correction.
        // Its float cancellation loses the fractions even though both inputs are doubles.
        final PoseStack oldPose = new PoseStack();
        oldPose.translate(30_000_000 - camera.x, 64 - camera.y, 30_000_000 - camera.z);
        oldPose.translate(worldOrigin.x - 30_000_000, worldOrigin.y - 64, worldOrigin.z - 30_000_000);
        assertNotEquals(.25f, oldPose.last().pose().m30());
        assertNotEquals(.1875f, oldPose.last().pose().m32());
    }

    @Test
    void worldTranslationPrecedesPlotRotationAndPreservesDistantWorldFractions() {
        final var transform = new BlockEntityRenderStateExtension.PlotTransform(
                new Quaternionf().rotateZ((float) (Math.PI / 2)), new Vec3(30_000_000.375, 80.25, -30_000_000.125));
        final PoseStack pose = new PoseStack();
        pose.translate(transform.cameraRelativeOrigin(new Vec3(30_000_000.125, 79, -30_000_000.5)));
        pose.rotate(transform.orientation());
        assertVector(new Vector3f(.25f, 1.25f, .375f), pose.last().pose().transformPosition(new Vector3f()));
        assertVector(new Vector3f(.25f, 2.25f, .375f), pose.last().pose().transformPosition(new Vector3f(1, 0, 0)));
    }

    @Test
    void conduitEyeCancelsPlotRotationWithoutMutatingCameraOrExtractionInput() {
        final Quaternionf plot = new Quaternionf().rotateXYZ(.3f, -.7f, .2f);
        final Quaternionf expectedPlot = new Quaternionf(plot);
        final Quaternionf camera = new Quaternionf().rotateYXZ(.9f, -.1f, -.4f);
        final Quaternionf expectedCamera = new Quaternionf(camera);
        final var transform = new BlockEntityRenderStateExtension.PlotTransform(plot, Vec3.ZERO);
        plot.identity();
        final Quaternionf restored = new Quaternionf(expectedPlot).mul(transform.relativeCameraOrientation(camera));
        assertEquals(1, Math.abs(restored.dot(expectedCamera)), 1e-6);
        assertEquals(expectedCamera, camera);
        assertEquals(1, Math.abs(new Quaternionf(transform.orientation()).dot(expectedPlot)), 1e-6);
    }

    private static void assertVector(final Vector3f expected, final Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-5);
        assertEquals(expected.y, actual.y, 1e-5);
        assertEquals(expected.z, actual.z, 1e-5);
    }
}
