package dev.ryanhcode.sable.mixin.block_decal_render;

import dev.ryanhcode.sable.mixinterface.BlockEntityRenderStateExtension;
import dev.ryanhcode.sable.mixinterface.PlotBlockRenderStateExtension;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({BlockBreakingRenderState.class, BlockOutlineRenderState.class})
public class PlotBlockRenderStateMixin implements PlotBlockRenderStateExtension {
    @Unique private @Nullable BlockEntityRenderStateExtension.PlotTransform sable$plotBlockTransform;

    @Override
    public @Nullable BlockEntityRenderStateExtension.PlotTransform sable$getPlotBlockTransform() {
        return this.sable$plotBlockTransform;
    }

    @Override
    public void sable$setPlotBlockTransform(@Nullable final BlockEntityRenderStateExtension.PlotTransform transform) {
        this.sable$plotBlockTransform = transform;
    }
}
