package dev.ryanhcode.sable.mixin.respawn_point.sleeping;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinterface.entity.entity_rendering.EntityRenderStateExtension;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;bedOrientation:Lnet/minecraft/core/Direction;", opcode = 180, ordinal = 0))
    private void sable$rotateWithBed(final LivingEntityRenderState state, final PoseStack pose,
                                    final SubmitNodeCollector nodes, final CameraRenderState camera, final CallbackInfo ci) {
        final Quaternionfc orientation = ((EntityRenderStateExtension) state).sable$getRenderTransform().sleepingOrientation();
        if (orientation != null) pose.rotate(orientation);
    }
}
