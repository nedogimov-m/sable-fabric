package dev.ryanhcode.sable.mixinterface;

import org.jetbrains.annotations.Nullable;

/** A copied plot transform for immutable native outline and breaking-block records. */
public interface PlotBlockRenderStateExtension {
    @Nullable BlockEntityRenderStateExtension.PlotTransform sable$getPlotBlockTransform();
    void sable$setPlotBlockTransform(@Nullable BlockEntityRenderStateExtension.PlotTransform transform);
}
