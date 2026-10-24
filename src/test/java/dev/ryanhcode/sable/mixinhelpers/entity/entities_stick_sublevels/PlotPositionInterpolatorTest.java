package dev.ryanhcode.sable.mixinhelpers.entity.entities_stick_sublevels;

import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlotPositionInterpolatorTest {
    @Test
    void linearTargetsHonorPauseFractionalTicksAndFinishExactly() {
        PlotPositionInterpolator interpolation = new PlotPositionInterpolator();
        interpolation.setTarget(PositionPath.of(new Vec3(3, 6, -9)), 3);
        Vec3 position = interpolation.advance(Vec3.ZERO, 0);
        assertPosition(0, 0, 0, position);
        position = interpolation.advance(position, .5);
        assertPosition(.5, 1, -1.5, position);
        position = interpolation.advance(position, 1.5);
        assertPosition(2, 4, -6, position);
        position = interpolation.advance(position, 20);
        assertPosition(3, 6, -9, position);
        assertEquals(position, interpolation.target());
        assertEquals(position, interpolation.advance(position, 1));
    }

    @Test
    void steppedPathsKeepCornerAndTickOffsetsAcrossLargeAdvance() {
        PlotPositionInterpolator interpolation = new PlotPositionInterpolator();
        interpolation.setTarget(PositionPath.stepped(List.of(
                new PositionStep(new Vec3(2, 0, 0), 2),
                new PositionStep(new Vec3(2, 4, 0), 4))), 3);
        Vec3 position = interpolation.advance(Vec3.ZERO, 3);
        assertPosition(2, 1, 0, position);
        position = interpolation.advance(position, 2);
        assertPosition(2, 3, 0, position);
        assertPosition(2, 4, 0, interpolation.advance(position, 1));
    }

    @Test
    void retargetsStartFromCurrentPositionAndZeroDurationSnaps() {
        PlotPositionInterpolator interpolation = new PlotPositionInterpolator();
        interpolation.setTarget(PositionPath.of(new Vec3(10, 0, 0)), 10);
        Vec3 position = interpolation.advance(Vec3.ZERO, 2);
        interpolation.setTarget(PositionPath.of(new Vec3(2, 4, 0)), 2);
        position = interpolation.advance(position, .5);
        assertPosition(2, 1, 0, position);
        interpolation.setTarget(PositionPath.stepped(List.of(
                new PositionStep(new Vec3(5, 5, 5), 0),
                new PositionStep(new Vec3(5, 7, 5), 2))), 3);
        position = interpolation.advance(position, 0);
        assertPosition(5, 5, 5, position);
        assertPosition(5, 6, 5, interpolation.advance(position, 1));
        interpolation.setTarget(PositionPath.of(new Vec3(8, 8, 8)), 0);
        assertPosition(8, 8, 8, interpolation.advance(position, 0));
    }

    @Test
    void clearCancelsPendingMovementAndRejectsInvalidTime() {
        PlotPositionInterpolator interpolation = new PlotPositionInterpolator();
        interpolation.setTarget(PositionPath.of(new Vec3(10, 0, 0)), 10);
        interpolation.clear();
        assertEquals(Vec3.ZERO, interpolation.advance(Vec3.ZERO, 100));
        assertThrows(IllegalArgumentException.class, () -> interpolation.advance(Vec3.ZERO, -1));
        assertThrows(IllegalArgumentException.class, () -> interpolation.advance(Vec3.ZERO, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> interpolation.advance(Vec3.ZERO, Double.POSITIVE_INFINITY));
    }

    private static void assertPosition(double x, double y, double z, Vec3 actual) {
        assertEquals(x, actual.x, 1e-10);
        assertEquals(y, actual.y, 1e-10);
        assertEquals(z, actual.z, 1e-10);
    }
}
