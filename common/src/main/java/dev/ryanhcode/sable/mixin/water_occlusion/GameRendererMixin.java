package dev.ryanhcode.sable.mixin.water_occlusion;

import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private GpuBufferSlice sable$copyEffectiveWaterProjection(final ProjectionMatrixBuffer buffer, final Matrix4f projection,
                                                            final Operation<GpuBufferSlice> original) {
        SableClient.WATER_OCCLUSION_RENDERER.captureRenderProjection(projection);
        return original.call(buffer, projection);
    }

    @Inject(method = "render", at = @At("HEAD"))
    public void sable$updateWaterOcclusionManager(final CallbackInfo ci) {
        SableClient.WATER_OCCLUSION_RENDERER.update();
        SableSkyLightShadows.prepareFrame();
    }
}
