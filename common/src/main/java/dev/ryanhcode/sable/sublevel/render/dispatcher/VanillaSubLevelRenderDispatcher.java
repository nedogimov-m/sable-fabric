package dev.ryanhcode.sable.sublevel.render.dispatcher;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMesh;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {
    public static boolean isSingleBlock(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return bounds != null && bounds.minX() == bounds.maxX() && bounds.minY() == bounds.maxY() && bounds.minZ() == bounds.maxZ()
                && !subLevel.getLevel().getBlockState(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())).is(SableTags.ALWAYS_CHUNK_RENDERING);
    }

    @Override public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData data) {
        if (data instanceof VanillaSingleSubLevelRenderData != isSingleBlock(subLevel)) {
            data.close();
            return this.createRenderData(subLevel);
        }
        ((VanillaChunkedSubLevelRenderData) data).resize();
        return data;
    }
    @Override public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        return isSingleBlock(subLevel) ? new VanillaSingleSubLevelRenderData(subLevel) : new VanillaChunkedSubLevelRenderData(subLevel);
    }
    @Override public List<PlotSectionMesh.Snapshot> extractTerrain(final Iterable<ClientSubLevel> sublevels, final Camera camera, final float partialTick) {
        final var regions = new RenderRegionCache();
        final var updates = Minecraft.getInstance().options.prioritizeChunkUpdates().get();
        final List<PlotSectionMesh.Snapshot> snapshots = new ArrayList<>();
        for (final ClientSubLevel subLevel : sublevels) {
            if (subLevel.isRemoved()) continue;
            final var data = (VanillaChunkedSubLevelRenderData) subLevel.getRenderData();
            data.compileSections(updates, regions, camera);
            snapshots.addAll(data.extract(camera.position(), partialTick));
        }
        return List.copyOf(snapshots);
    }
    @Override public void onResourceManagerReload(final ResourceManager resources) {
        final var level = Minecraft.getInstance().level;
        final var container = level == null ? null : dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container != null) this.rebuild(container.getAllSubLevels());
    }
    @Override public void addDebugInfo(final Consumer<String> consumer) { consumer.accept("Native plot mesh snapshots (CPU)"); }
    @Override public void free() {
        final var level = Minecraft.getInstance().level;
        final var container = level == null ? null : dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container != null) for (final var subLevel : container.getAllSubLevels()) subLevel.getRenderData().close();
    }
}
