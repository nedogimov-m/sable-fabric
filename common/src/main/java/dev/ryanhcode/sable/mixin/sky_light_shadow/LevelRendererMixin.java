package dev.ryanhcode.sable.mixin.sky_light_shadow;

import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
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
    @Inject(method = "render", at = @At("HEAD"))
    private void sable$renderPlotShadowMap(final CallbackInfo ci) {
        SableSkyLightShadows.renderShadowMap(this.levelRenderState);
    }
}
