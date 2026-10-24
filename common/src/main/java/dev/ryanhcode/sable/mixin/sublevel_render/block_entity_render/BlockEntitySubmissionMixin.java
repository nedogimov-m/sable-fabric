package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderStateExtension;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class BlockEntitySubmissionMixin {
    @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void sable$translatePlotOrigin(final PoseStack pose, final double x, final double y, final double z,
                                           final Operation<Void> original, @Local final BlockEntityRenderState state,
                                           @Local(argsOnly = true) final LevelRenderState levelState) {
        final var transform = ((BlockEntityRenderStateExtension) state).sable$getPlotTransform();
        if (transform == null) {
            original.call(pose, x, y, z);
            return;
        }
        final var origin = transform.cameraRelativeOrigin(levelState.cameraRenderState.pos);
        original.call(pose, origin.x, origin.y, origin.z);
    }
}
