package dev.ryanhcode.sable.mixin.sublevel_render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderStateExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin implements BlockEntityRenderDispatcherExtension {
    @Unique private @Nullable Vec3 sable$cameraPos;

    @ModifyExpressionValue(method = "tryExtractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;cameraPos:Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$localCamera(final Vec3 original, @Local(argsOnly = true) final BlockEntity blockEntity,
                                   @Local(argsOnly = true) final float partialTick) {
        if (this.sable$cameraPos != null) return this.sable$cameraPos;
        final ClientSubLevel subLevel = blockEntity.getLevel() == null ? null : Sable.HELPER.getContainingClient(blockEntity);
        return subLevel == null ? original : subLevel.renderPose(partialTick).transformPositionInverse(original);
    }

    @Inject(method = "tryExtractRenderState", at = @At("RETURN"))
    private void sable$extractPlotTransform(final BlockEntity blockEntity, final float partialTick,
                                            final ModelFeatureRenderer.CrumblingOverlay breakProgress, final boolean offScreen,
                                            final CallbackInfoReturnable<BlockEntityRenderState> cir) {
        final BlockEntityRenderState state = cir.getReturnValue();
        if (state == null) return;
        final BlockEntityRenderStateExtension extension = (BlockEntityRenderStateExtension) state;
        final ClientSubLevel subLevel = blockEntity.getLevel() == null ? null : Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            extension.sable$setPlotTransform(null);
        } else {
            final var pose = subLevel.renderPose(partialTick);
            extension.sable$setPlotTransform(new BlockEntityRenderStateExtension.PlotTransform(
                    new org.joml.Quaternionf(pose.orientation()), pose.transformPosition(Vec3.atLowerCornerOf(blockEntity.getBlockPos()))));
            state.lightCoords = subLevel.scaleLightColor(state.lightCoords);
        }
    }

    @WrapMethod(method = "submit")
    private void sable$submitInPlot(final BlockEntityRenderState state, final PoseStack pose,
                                    final SubmitNodeCollector nodes, final CameraRenderState camera,
                                    final Operation<Void> original) {
        final var transform = ((BlockEntityRenderStateExtension) state).sable$getPlotTransform();
        if (transform == null) {
            original.call(state, pose, nodes, camera);
            return;
        }
        pose.pushPose();
        try {
            pose.rotate(transform.orientation());
            original.call(state, pose, nodes, camera);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public void sable$setCameraPosition(@Nullable final Vec3 position) {
        this.sable$cameraPos = position;
    }
}
