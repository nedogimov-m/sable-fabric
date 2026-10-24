package dev.ryanhcode.sable.mixin.debug_render;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.PlotBoxGizmo;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block-position debug boxes are now emitted through native gizmos. */
@Mixin(Gizmos.class)
public class DebugRendererMixin {
    @Inject(method = "cuboid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/gizmos/GizmoStyle;)Lnet/minecraft/gizmos/GizmoProperties;", at = @At("HEAD"), cancellable = true)
    private static void sable$projectBlockBox(final BlockPos pos, final GizmoStyle style, final CallbackInfoReturnable<GizmoProperties> cir) {
        final var result = sable$projectBox(pos, 0, style);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "cuboid(Lnet/minecraft/core/BlockPos;FLnet/minecraft/gizmos/GizmoStyle;)Lnet/minecraft/gizmos/GizmoProperties;", at = @At("HEAD"), cancellable = true)
    private static void sable$projectInflatedBlockBox(final BlockPos pos, final float inflation, final GizmoStyle style,
                                                     final CallbackInfoReturnable<GizmoProperties> cir) {
        final var result = sable$projectBox(pos, inflation, style);
        if (result != null) cir.setReturnValue(result);
    }

    @Unique
    private static @Nullable GizmoProperties sable$projectBox(final BlockPos pos, final float inflation, final GizmoStyle style) {
        final var subLevel = Sable.HELPER.getContainingClient(pos);
        return subLevel == null ? null : Gizmos.addGizmo(new PlotBoxGizmo(new AABB(pos).inflate(inflation), subLevel.renderPose(), style));
    }
}
