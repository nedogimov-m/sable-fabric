package dev.ryanhcode.sable.fabric.mixin.block_outline_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinterface.PlotBlockRenderStateExtension;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both high-contrast and normal outline submissions use the same extracted plot pose. */
@Mixin(value = LevelRenderer.class, priority = 400)
public abstract class LevelRendererMixin {
    @WrapOperation(method = "submitBlockOutline", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void sable$placeOutline(final PoseStack pose, final double x, final double y, final double z,
                                    final Operation<Void> original, @Local final BlockOutlineRenderState outline,
                                    @Local(argsOnly = true) final LevelRenderState scene) {
        final var transform = ((PlotBlockRenderStateExtension) (Object) outline).sable$getPlotBlockTransform();
        if (transform == null) {
            original.call(pose, x, y, z);
            return;
        }
        final var origin = transform.cameraRelativeOrigin(scene.cameraRenderState.pos);
        original.call(pose, origin.x, origin.y, origin.z);
        pose.rotate(transform.orientation());
    }
}
