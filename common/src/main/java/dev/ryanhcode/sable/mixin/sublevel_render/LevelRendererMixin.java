package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.PlotTerrainRenderStateExtension;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelRendererMixin {
    @Shadow private @Nullable ClientLevel level;
    @Shadow @Final private LevelRenderState levelRenderState;

    @Inject(method = "allChanged", at = @At("TAIL"))
    private void sable$rebuildPlotMeshes(final CallbackInfo ci) {
        final ClientSubLevelContainer container = this.level == null ? null : SubLevelContainer.getContainer(this.level);
        if (container != null) SubLevelRenderDispatcher.get().rebuild(container.getAllSubLevels());
    }
    @Inject(method = "extract", at = @At("TAIL"))
    private void sable$extractPlotTerrain(final DeltaTracker delta, final Camera camera, final float partialTick, final CallbackInfo ci) {
        if (this.level != null) ((PlotTerrainRenderStateExtension) this.levelRenderState).sable$setWaterOcclusion(
                SableClient.WATER_OCCLUSION_RENDERER.extract(this.level, this.levelRenderState.cameraRenderState, this.levelRenderState.worldPartialTicks));
        final ClientSubLevelContainer container = this.level == null ? null : SubLevelContainer.getContainer(this.level);
        if (container != null) ((PlotTerrainRenderStateExtension) this.levelRenderState).sable$setPlotTerrain(
                SubLevelRenderDispatcher.get().extractTerrain(container.getAllSubLevels(), camera, this.levelRenderState.worldPartialTicks));
    }
}
