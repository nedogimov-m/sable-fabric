package dev.ryanhcode.sable.mixin.water_occlusion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fluid contact, current accumulation and eye immersion now share this tracker. */
@Mixin(EntityFluidInteraction.class)
public class EntityFluidInteractionMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState sable$occludeBody(final BlockGetter level, final BlockPos pos,
                                        final Operation<FluidState> original,
                                        @Local(argsOnly = true) final Entity entity) {
        final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(entity.level());
        if (container != null && (container.isOccluded(entity.position()) ||
                container.isOccluded(entity.getBoundingBox().getCenter()))) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return original.call(level, pos);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;getHeightForCamera(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float sable$occludeEyes(final FluidState state, final BlockGetter level, final BlockPos pos,
                                   final Operation<Float> original,
                                   @Local(argsOnly = true) final Entity entity) {
        final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(entity.level());
        if (container != null && container.isOccluded(new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()))) {
            return Float.NEGATIVE_INFINITY;
        }
        return original.call(state, level, pos);
    }
}
