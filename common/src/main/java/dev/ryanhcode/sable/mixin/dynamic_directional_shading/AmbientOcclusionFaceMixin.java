package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both native AO and flat lighting share this directional brightness calculation. */
@Mixin(BlockModelLighter.class)
public class AmbientOcclusionFaceMixin {
    @Inject(method = "getDirectionalBrightness", at = @At("HEAD"), cancellable = true)
    private static void sable$dynamicDirectionalBrightness(final CardinalLighting lighting, final BakedQuad quad,
                                                           final Direction direction, final CallbackInfoReturnable<Float> cir) {
        if (SableDynamicDirectionalShading.isEnabled()
                && ((ModelBlockRendererCacheExtension) BlockModelLighter.CACHE.get()).sable$getOnSubLevel()) {
            cir.setReturnValue(1.0f);
        }
    }
}
