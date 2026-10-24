package dev.ryanhcode.sable.mixin.debug_render;

import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinhelpers.PlotBoxGizmo;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture native gizmo geometry before LevelExtractor hands its collector to the renderer. */
@Mixin(LevelExtractor.class)
public class LevelRendererMixin {
    @Shadow private @Nullable ClientLevel level;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private SimpleGizmoCollector mainThreadGizmos;

    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void sable$extractDebugGizmos(final CallbackInfo ci) {
        if (this.level == null) return;
        try (final var ignored = Gizmos.withCollector(this.mainThreadGizmos)) {
            SableClient.GIZMO_HANDLER.emitGizmos(this.levelRenderState.worldPartialTicks);
            final Minecraft minecraft = Minecraft.getInstance();
            if (!minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) || minecraft.showOnlyReducedInfo()) return;
            final var container = SubLevelContainer.getContainer(this.level);
            if (container == null) return;
            for (final var subLevel : container.getAllSubLevels()) {
                final var bounds = subLevel.boundingBox();
                Gizmos.cuboid(new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                        GizmoStyle.stroke(0xb3808080));
                final var renderPose = ((ClientSubLevel) subLevel).renderPose(this.levelRenderState.worldPartialTicks);
                final var plotBounds = subLevel.getPlot().getBoundingBox();
                final var center = renderPose.rotationPoint();
                Gizmos.addGizmo(new PlotBoxGizmo(new AABB(center.x() - .125, center.y() - .125, center.z() - .125,
                        center.x() + .125, center.y() + .125, center.z() + .125), renderPose, GizmoStyle.stroke(0xffb3b380)));
                if (plotBounds != null) Gizmos.addGizmo(new PlotBoxGizmo(new AABB(plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(),
                        plotBounds.maxX() + 1.0, plotBounds.maxY() + 1.0, plotBounds.maxZ() + 1.0), renderPose, GizmoStyle.stroke(0xffe68080)));
                if (ClientSableInterpolationState.RENDER_INTERPOLATION_BOUNDS) {
                    final var size = bounds.size(new Vector3d()).mul(.5);
                    for (final var snapshot : ((ClientSubLevel) subLevel).getInterpolator().buffer) {
                        final var position = snapshot.pose().position();
                        Gizmos.cuboid(new AABB(position.x() - size.x, position.y() - size.y, position.z() - size.z,
                                position.x() + size.x, position.y() + size.y, position.z() + size.z), GizmoStyle.stroke(0x8000ffff));
                    }
                }
            }
        }
    }
}
