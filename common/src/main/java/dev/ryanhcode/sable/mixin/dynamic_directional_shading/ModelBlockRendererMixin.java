package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
    /** Lighting/culling use the real face; only the emitted normal honors the model's shade override. */
    @ModifyVariable(method = "putQuadWithTint", at = @At("HEAD"), argsOnly = true)
    private BakedQuad sable$dynamicShadeNormal(final BakedQuad quad) {
        return SableDynamicDirectionalShading.isEnabled()
                && ((ModelBlockRendererCacheExtension) BlockModelLighter.CACHE.get()).sable$getOnSubLevel()
                ? SubLevelVertexConsumer.withShadeNormal(quad) : quad;
    }
}
