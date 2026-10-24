package dev.ryanhcode.sable.mixin.conduit;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderStateExtension;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.blockentity.state.ConduitRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ConduitRenderer.class)
public class ConduitRendererMixin {
    @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ConduitRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraRenderState;orientation:Lorg/joml/Quaternionf;"))
    private Quaternionf sable$reorientEye(final Quaternionf cameraOrientation,
                                         @Local(argsOnly = true) final ConduitRenderState state) {
        final var transform = ((BlockEntityRenderStateExtension) state).sable$getPlotTransform();
        return transform == null ? cameraOrientation : transform.relativeCameraOrientation(cameraOrientation);
    }
}
