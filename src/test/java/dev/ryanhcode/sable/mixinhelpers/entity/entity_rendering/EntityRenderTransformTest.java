package dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityRenderTransformTest {
    @Test
    void containingFrameKeepsEntityAnchorAndRotatesItsNeighbors() {
        PoseStack pose = new PoseStack();
        pose.translate(-3, 2, -7);
        EntityRenderTransform transform = new EntityRenderTransform(new Quaternionf().rotateZ((float) (Math.PI / 2)),
                null, null, Vec3.ZERO, Vec3.ZERO);
        Vector3f anchorBefore = pose.last().pose().transformPosition(new Vector3f(10, 20, 30));
        transform.applyContaining(pose, 10, 20, 30);
        assertVector(anchorBefore, pose.last().pose().transformPosition(new Vector3f(10, 20, 30)));
        assertVector(new Vector3f(7, 23, 23), pose.last().pose().transformPosition(new Vector3f(11, 20, 30)));
    }

    @Test
    void ridingFramePivotsAtEyeAndAddsInterpolatedEyeOffset() {
        PoseStack pose = new PoseStack();
        EntityRenderTransform transform = new EntityRenderTransform(null, new Quaternionf().rotateZ((float) (Math.PI / 2)),
                null, new Vec3(0, 1.5, 0), new Vec3(2, 0, 1));
        transform.applyEntity(pose);
        assertVector(new Vector3f(2, 1.5f, 1), pose.last().pose().transformPosition(new Vector3f(0, 1.5f, 0)));
        assertVector(new Vector3f(3.5f, 1.5f, 1), pose.last().pose().transformPosition(new Vector3f()));
    }

    @Test
    void billboardCancelsNoncommutingFramesAndExtractionCopiesInputRotations() {
        Quaternionf containing = new Quaternionf().rotateXYZ(.5f, .4f, -.2f);
        Quaternionf riding = new Quaternionf().rotateYXZ(.7f, -.3f, .1f);
        Quaternionf camera = new Quaternionf().rotateXYZ(-.2f, 1.2f, .3f);
        Quaternionf containingBefore = new Quaternionf(containing);
        Quaternionf ridingBefore = new Quaternionf(riding);
        Quaternionf cameraBefore = new Quaternionf(camera);
        EntityRenderTransform transform = new EntityRenderTransform(containing, riding,
                new Quaternionf().rotateX(.3f), Vec3.ZERO, Vec3.ZERO);
        containing.identity();
        riding.identity();
        Quaternionf relative = transform.relativeCameraOrientation(camera);
        Quaternionf restored = new Quaternionf(containingBefore).mul(ridingBefore).mul(relative);
        assertEquals(1, Math.abs(restored.dot(camera)), 1e-6);
        assertEquals(cameraBefore, camera);
        assertEquals(1, Math.abs(new Quaternionf(transform.containingOrientation()).dot(containingBefore)), 1e-6);
        assertEquals(1, Math.abs(new Quaternionf(transform.entityOrientation()).dot(ridingBefore)), 1e-6);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-4);
        assertEquals(expected.y, actual.y, 1e-4);
        assertEquals(expected.z, actual.z, 1e-4);
    }
}
