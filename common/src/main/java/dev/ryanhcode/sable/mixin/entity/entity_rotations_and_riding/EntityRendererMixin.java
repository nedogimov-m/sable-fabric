package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.EntityRenderTransform;
import dev.ryanhcode.sable.mixinterface.entity.entity_rendering.EntityRenderStateExtension;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    // The four-argument overload delegates to this native owner for both the
    // score and name submissions. Wrap it once with the actual extracted state.
    @WrapMethod(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V")
    private void sable$submitNameDisplay(final EntityRenderState state, final PoseStack pose,
                                        final SubmitNodeCollector nodes, final CameraRenderState camera,
                                        final int yOffset, final Operation<Void> original) {
        final EntityRenderTransform transform = ((EntityRenderStateExtension) state).sable$getRenderTransform();
        if (transform.containingOrientation() == null && transform.entityOrientation() == null) {
            original.call(state, pose, nodes, camera, yOffset);
            return;
        }
        // Native name-tag collection reads the orientation immediately and copies
        // the resulting pose matrix. Keep the shared camera snapshot untouched.
        final CameraRenderState relative = new CameraRenderState();
        relative.orientation = transform.relativeCameraOrientation(camera.orientation);
        original.call(state, pose, nodes, relative, yOffset);
    }
}
