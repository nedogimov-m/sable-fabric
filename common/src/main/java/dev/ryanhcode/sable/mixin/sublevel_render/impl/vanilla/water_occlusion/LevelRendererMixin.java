package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla.water_occlusion;

import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.mixinterface.PlotTerrainRenderStateExtension;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private GameRenderer gameRenderer;
    @Inject(method = "render", at = @At("HEAD"))
    private void sable$renderWaterMasks(final CallbackInfo ci) {
        SableClient.WATER_OCCLUSION_RENDERER.renderMasks(
                ((PlotTerrainRenderStateExtension) this.levelRenderState).sable$getWaterOcclusion(), this.gameRenderer.mainRenderTarget());
    }
}
