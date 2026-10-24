package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.mixinterface.PlotTerrainRenderStateExtension;
import dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMesh;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import org.jetbrains.annotations.Nullable;

@Mixin(LevelRenderState.class)
public class LevelRenderStateMixin implements PlotTerrainRenderStateExtension {
    @Unique private @Nullable WaterOcclusionRenderer.Snapshot sable$water;
    @Override public @Nullable WaterOcclusionRenderer.Snapshot sable$getWaterOcclusion() { return this.sable$water; }
    @Override public void sable$setWaterOcclusion(final @Nullable WaterOcclusionRenderer.Snapshot snapshot) {
        if (this.sable$water != null) this.sable$water.close();
        this.sable$water = snapshot;
    }
    @Unique private List<PlotSectionMesh.Snapshot> sable$terrain = List.of();
    @Override public List<PlotSectionMesh.Snapshot> sable$getPlotTerrain() { return this.sable$terrain; }
    @Override public void sable$setPlotTerrain(final List<PlotSectionMesh.Snapshot> terrain) { this.sable$terrain = List.copyOf(terrain); }
    @Inject(method = "reset", at = @At("HEAD"))
    private void sable$resetTerrain(final CallbackInfo ci) { this.sable$terrain = List.of(); this.sable$setWaterOcclusion(null); }
}
