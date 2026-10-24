package dev.ryanhcode.sable.sublevel.render.vanilla;

import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plot-owned section snapshots compiled using the native model and fluid tessellators. */
public class VanillaChunkedSubLevelRenderData implements SubLevelRenderData {
    private final ClientSubLevel subLevel;
    private final Set<Long> sections = new LinkedHashSet<>();
    private final Set<Long> dirty = new LinkedHashSet<>();
    private final Map<Long, PlotSectionMesh> meshes = new HashMap<>();
    private boolean dynamicShading;

    public VanillaChunkedSubLevelRenderData(final ClientSubLevel subLevel) {
        this.subLevel = subLevel;
        this.dynamicShading = SableDynamicDirectionalShading.isEnabled();
        this.resize();
    }

    public void resize() {
        final var bounds = this.subLevel.getPlot().getBoundingBox();
        final Set<Long> resized = new LinkedHashSet<>();
        if (bounds != null && bounds.volume() > 0) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
                    for (int y = bounds.minY() >> 4; y <= bounds.maxY() >> 4; y++) resized.add(SectionPos.asLong(x, y, z));
                }
            }
        }
        this.meshes.keySet().retainAll(resized);
        this.dirty.retainAll(resized);
        for (final long section : resized) if (!this.meshes.containsKey(section)) this.dirty.add(section);
        this.sections.clear();
        this.sections.addAll(resized);
    }

    @Override public void rebuild() { this.dirty.addAll(this.sections); }
    @Override public boolean isSectionCompiled(final int x, final int y, final int z) {
        final long section = SectionPos.asLong(x, y, z);
        return !this.sections.contains(section) || this.meshes.containsKey(section);
    }
    @Override public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        final long section = SectionPos.asLong(x, y, z);
        if (this.sections.contains(section)) this.dirty.add(section);
    }
    @Override public void compileSections(final PrioritizeChunkUpdates updates, final RenderRegionCache regions, final Camera camera) {
        final boolean dynamic = SableDynamicDirectionalShading.isEnabled();
        if (dynamic != this.dynamicShading) {
            this.dynamicShading = dynamic;
            this.rebuild();
        }
        for (final long node : List.copyOf(this.dirty)) {
            final SectionPos section = SectionPos.of(node);
            final var region = regions.createRegion(this.subLevel.getLevel(), node);
            this.meshes.put(node, region == null ? new PlotSectionMesh(section, Map.of(), dynamic) : PlotSectionMesh.compile(section, region, dynamic));
            this.dirty.remove(node);
        }
    }

    public List<PlotSectionMesh.Snapshot> extract(final Vec3 cameraPosition, final float partialTick) {
        final var pose = this.subLevel.renderPose(partialTick);
        final List<PlotSectionMesh.Snapshot> snapshots = new ArrayList<>();
        final int skyLightScale = this.subLevel.getLatestSkyLightScale();
        final var lighting = this.subLevel.getLevel().cardinalLighting();
        for (final PlotSectionMesh mesh : this.meshes.values()) {
            if (mesh.layers().isEmpty() && mesh.extensions().isEmpty()) continue;
            final Vec3 origin = pose.transformPosition(Vec3.atLowerCornerOf(mesh.section().origin())).subtract(cameraPosition);
            snapshots.add(new PlotSectionMesh.Snapshot(mesh, origin, new Quaternionf(pose.orientation()), new Vector3f(pose.scale()), lighting, skyLightScale)
                    .withExtensions(this.subLevel, partialTick));
        }
        return List.copyOf(snapshots);
    }

    @Override public int getVisibleSectionCount() { return this.meshes.size(); }
    @Override public ClientSubLevel getSubLevel() { return this.subLevel; }
    @Override public void close() { this.sections.clear(); this.dirty.clear(); this.meshes.clear(); }
}
