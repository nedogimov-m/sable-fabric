package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.EntityRenderTransform;
import dev.ryanhcode.sable.mixinterface.entity.entity_rendering.EntityRenderStateExtension;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows.SubLevelEntityShadowRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies extracted plot and riding frames to native submitted geometry. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @WrapMethod(method = "submit")
    private void sable$submitInPlotFrame(final EntityRenderState state, final CameraRenderState camera,
                                         final double x, final double y, final double z, final PoseStack pose,
                                         final SubmitNodeCollector nodes, final Operation<Void> original) {
        final EntityRenderTransform transform = ((EntityRenderStateExtension) state).sable$getRenderTransform();
        if (transform.containingOrientation() == null) {
            original.call(state, camera, x, y, z, pose, nodes);
            return;
        }
        pose.pushPose();
        try {
            transform.applyContaining(pose, x, y, z);
            original.call(state, camera, x, y, z, pose, nodes);
        } finally {
            pose.popPose();
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void sable$submitBody(final EntityRenderer<?, ?> renderer, final EntityRenderState state,
                                 final PoseStack pose, final SubmitNodeCollector nodes, final CameraRenderState camera,
                                 final Operation<Void> original) {
        pose.pushPose();
        try {
            ((EntityRenderStateExtension) state).sable$getRenderTransform().applyEntity(pose);
            original.call(renderer, state, pose, nodes, camera);
        } finally {
            pose.popPose();
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"))
    private void sable$submitFlame(final SubmitNodeCollector nodes, final PoseStack pose, final EntityRenderState state,
                                  final Quaternionf cameraOrientation, final Operation<Void> original) {
        final EntityRenderTransform transform = ((EntityRenderStateExtension) state).sable$getRenderTransform();
        final Quaternionf orientation = transform.containingOrientation() == null ? cameraOrientation
                : new Quaternionf(transform.containingOrientation()).conjugate().mul(cameraOrientation);
        original.call(nodes, pose, state, orientation);
    }

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE))
    private void sable$submitPlotShadows(final EntityRenderState state, final CameraRenderState camera,
                                         final double x, final double y, final double z, final PoseStack pose,
                                         final SubmitNodeCollector nodes, final CallbackInfo ci) {
        final EntityRenderStateExtension extension = (EntityRenderStateExtension) state;
        final List<SubLevelEntityShadowRenderer.Vertex> vertices = extension.sable$getShadowVertices();
        if (vertices.isEmpty()) return;
        pose.pushPose();
        try {
            final var containing = extension.sable$getRenderTransform().containingOrientation();
            if (containing != null) pose.rotate(new Quaternionf(containing).conjugate());
            nodes.submitCustomGeometry(pose, RenderTypes.entityShadow(Identifier.withDefaultNamespace("textures/misc/shadow.png")),
                    (snapshot, consumer) -> {
                        for (final var vertex : vertices) {
                            consumer.addVertex(snapshot, vertex.x(), vertex.y(), vertex.z()).setColor(vertex.color())
                                    .setUv(vertex.u(), vertex.v()).setOverlay(OverlayTexture.NO_OVERLAY)
                                    .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0, 1, 0);
                        }
                    });
        } finally {
            pose.popPose();
        }
    }
}
