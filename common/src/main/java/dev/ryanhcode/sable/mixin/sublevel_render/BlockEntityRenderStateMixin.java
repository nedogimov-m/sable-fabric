package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.mixinterface.BlockEntityRenderStateExtension;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntityRenderState.class)
public class BlockEntityRenderStateMixin implements BlockEntityRenderStateExtension {
    @Unique private @Nullable PlotTransform sable$plotTransform;

    @Override public @Nullable PlotTransform sable$getPlotTransform() { return this.sable$plotTransform; }
    @Override public void sable$setPlotTransform(@Nullable final PlotTransform transform) { this.sable$plotTransform = transform; }
}
