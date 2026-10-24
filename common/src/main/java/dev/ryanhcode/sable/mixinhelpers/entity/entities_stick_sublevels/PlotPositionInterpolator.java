package dev.ryanhcode.sable.mixinhelpers.entity.entities_stick_sublevels;

import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/** Interpolates native network paths in plot coordinates before world projection. */
public final class PlotPositionInterpolator {
    private final Deque<PositionStep> steps = new ArrayDeque<>();
    private double remainingTicks;
    private Vec3 target = Vec3.ZERO;

    public void setTarget(final PositionPath path, final int defaultTicks) {
        this.steps.clear();
        this.target = path.endPosition();
        if (path instanceof final PositionPath.Stepped stepped) {
            this.steps.addAll(stepped.steps());
        } else {
            this.steps.add(new PositionStep(path.endPosition(), Math.max(0, defaultTicks)));
        }
        this.remainingTicks = this.steps.isEmpty() ? 0 : this.steps.getFirst().tickOffset();
    }

    public Vec3 advance(Vec3 position, double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks) || elapsedTicks < 0) {
            throw new IllegalArgumentException("Invalid interpolation elapsed ticks: " + elapsedTicks);
        }
        while (!this.steps.isEmpty()) {
            final PositionStep step = this.steps.getFirst();
            if (this.remainingTicks <= 0) {
                position = step.position();
                this.steps.removeFirst();
                this.remainingTicks = this.steps.isEmpty() ? 0 : this.steps.getFirst().tickOffset();
                continue;
            }
            if (elapsedTicks == 0) break;
            final double consumed = Math.min(this.remainingTicks, elapsedTicks);
            position = position.lerp(step.position(), consumed / this.remainingTicks);
            this.remainingTicks -= consumed;
            elapsedTicks -= consumed;
        }
        return position;
    }

    public void clear() {
        this.steps.clear();
        this.remainingTicks = 0;
    }

    public Vec3 target() {
        return this.target;
    }
}
