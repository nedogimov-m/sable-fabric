package dev.ryanhcode.sable.mixin.block_decal_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinterface.PlotBlockRenderStateExtension;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Submit the extracted plot decal in world space before native model offsets are applied. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapOperation(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void sable$placeBreakingBlock(final PoseStack pose, final double x, final double y, final double z,
                                          final Operation<Void> original, @Local final BlockBreakingRenderState breaking,
                                          @Local(argsOnly = true) final LevelRenderState scene) {
        final var transform = ((PlotBlockRenderStateExtension) (Object) breaking).sable$getPlotBlockTransform();
        if (transform == null) {
            original.call(pose, x, y, z);
            return;
        }
        final var origin = transform.cameraRelativeOrigin(scene.cameraRenderState.pos);
        original.call(pose, origin.x, origin.y, origin.z);
        pose.rotate(transform.orientation());
    }
}
