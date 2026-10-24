package dev.ryanhcode.sable.mixin.loaded_chunk_debug;

import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.mixinhelpers.loaded_chunk_debug.SableChunkDebugRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkBorderRenderer.class)
public class ChunkBorderRendererMixin {
    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void sable$loadedChunkGizmos(final double x, final double y, final double z, final DebugValueAccess debugValues,
                                        final Frustum frustum, final float partialTick, final CallbackInfo ci) {
        if (SableClientConfig.DEBUG_DRAW_LOADED_CHUNKS.getAsBoolean()) {
            SableChunkDebugRenderer.render(y);
            ci.cancel();
        }
    }
}
