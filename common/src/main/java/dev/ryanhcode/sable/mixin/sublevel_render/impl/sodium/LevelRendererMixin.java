package dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.PlotTerrainRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelRenderer.class, priority = 1002)
public class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private GameRenderer gameRenderer;
    @Inject(method = "render", at = @At("HEAD"))
    private void sable$renderWaterMasks(final CallbackInfo ci) {
        SableClient.WATER_OCCLUSION_RENDERER.renderMasks(
                ((PlotTerrainRenderStateExtension) this.levelRenderState).sable$getWaterOcclusion(), this.gameRenderer.mainRenderTarget());
    }
    @Inject(method = "submitFeatures", at = @At("TAIL"))
    private void sable$submitPlotTerrain(final LevelRenderState scene, final SubmitNodeCollector nodes, final boolean outline, final CallbackInfo ci) {
        for (final var terrain : ((PlotTerrainRenderStateExtension) scene).sable$getPlotTerrain()) terrain.submit(nodes);
    }
    @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
    private void sable$isPlotSectionCompiled(final BlockPos pos, final long frameTime, final CallbackInfoReturnable<Boolean> cir) {
        final var level = Minecraft.getInstance().level;
        final var container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) return;
        final var subLevel = Sable.HELPER.getContainingClient(pos);
        final var section = SectionPos.of(pos);
        cir.setReturnValue(subLevel != null && subLevel.getRenderData().isSectionCompiled(section.x(), section.y(), section.z()));
    }
}
