package dev.ryanhcode.sable.mixinterface;

import dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMesh;
import java.util.List;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import org.jetbrains.annotations.Nullable;

public interface PlotTerrainRenderStateExtension {
    @Nullable WaterOcclusionRenderer.Snapshot sable$getWaterOcclusion();
    void sable$setWaterOcclusion(@Nullable WaterOcclusionRenderer.Snapshot snapshot);
    List<PlotSectionMesh.Snapshot> sable$getPlotTerrain();
    void sable$setPlotTerrain(List<PlotSectionMesh.Snapshot> terrain);
}
