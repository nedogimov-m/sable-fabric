package dev.ryanhcode.sable.mixin.block_decal_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderStateExtension;
import dev.ryanhcode.sable.mixinterface.PlotBlockRenderStateExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow private @Nullable ClientLevel level;

    @WrapOperation(method = "extractBlockDestroyAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(DDD)D"))
    private double sable$projectBreakingDistance(final BlockPos pos, final double x, final double y, final double z,
                                                final Operation<Double> original,
                                                @Local(argsOnly = true) final LevelRenderState state) {
        final var subLevel = this.level == null ? null : (ClientSubLevel) Sable.HELPER.getContaining(this.level, pos);
        return subLevel == null ? original.call(pos, x, y, z)
                : subLevel.renderPose(state.worldPartialTicks).transformPosition(Vec3.atCenterOf(pos)).distanceToSqr(x, y, z);
    }

    @Inject(method = "extractBlockDestroyAnimation", at = @At("TAIL"))
    private void sable$snapshotBreakingTransforms(final Camera camera, final LevelRenderState state, final CallbackInfo ci) {
        for (final var breaking : state.blockBreakingRenderStates) {
            this.sable$snapshotTransform((PlotBlockRenderStateExtension) (Object) breaking, breaking.blockPos(), state.worldPartialTicks);
        }
    }

    @Inject(method = "extractBlockOutline", at = @At("TAIL"))
    private void sable$snapshotOutlineTransform(final Camera camera, final LevelRenderState state, final CallbackInfo ci) {
        final var outline = state.blockOutlineRenderState;
        if (outline != null) this.sable$snapshotTransform((PlotBlockRenderStateExtension) (Object) outline, outline.pos(), state.worldPartialTicks);
    }

    @Unique
    private void sable$snapshotTransform(final PlotBlockRenderStateExtension state, final BlockPos pos, final float partialTick) {
        final var subLevel = this.level == null ? null : (ClientSubLevel) Sable.HELPER.getContaining(this.level, pos);
        if (subLevel == null) {
            state.sable$setPlotBlockTransform(null);
        } else {
            final var pose = subLevel.renderPose(partialTick);
            state.sable$setPlotBlockTransform(new BlockEntityRenderStateExtension.PlotTransform(new Quaternionf(pose.orientation()),
                    pose.transformPosition(Vec3.atLowerCornerOf(pos))));
        }
    }
}
