package dev.ryanhcode.sable.mixinhelpers;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable world-space box corners extracted before the native gizmo draw. */
public final class PlotBoxGizmo implements Gizmo {
    private final Vec3[] corners;
    private final GizmoStyle style;

    public PlotBoxGizmo(final AABB bounds, final Pose3dc pose, final GizmoStyle style) {
        this.style = style;
        this.corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            this.corners[i] = pose.transformPosition(new Vec3((i & 1) == 0 ? bounds.minX : bounds.maxX,
                    (i & 2) == 0 ? bounds.minY : bounds.maxY, (i & 4) == 0 ? bounds.minZ : bounds.maxZ));
        }
    }

    @Override
    public void emit(final GizmoPrimitives primitives, final float opacity) {
        if (this.style.hasStroke()) {
            for (int i = 0; i < 8; i++) {
                for (int axis = 1; axis <= 4; axis <<= 1) {
                    if ((i & axis) == 0) primitives.addLine(this.corners[i], this.corners[i | axis],
                            this.style.multipliedStroke(opacity), this.style.strokeWidth());
                }
            }
        }
        if (this.style.hasFill()) {
            this.face(primitives, opacity, 0, 2, 3, 1);
            this.face(primitives, opacity, 4, 5, 7, 6);
            this.face(primitives, opacity, 0, 1, 5, 4);
            this.face(primitives, opacity, 2, 6, 7, 3);
            this.face(primitives, opacity, 0, 4, 6, 2);
            this.face(primitives, opacity, 1, 3, 7, 5);
        }
    }

    private void face(final GizmoPrimitives primitives, final float opacity, final int a, final int b, final int c, final int d) {
        primitives.addQuad(this.corners[a], this.corners[b], this.corners[c], this.corners[d], this.style.multipliedFill(opacity));
    }
}
