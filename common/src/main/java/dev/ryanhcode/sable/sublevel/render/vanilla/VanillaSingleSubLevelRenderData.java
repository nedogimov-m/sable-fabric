package dev.ryanhcode.sable.sublevel.render.vanilla;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Single-block plots use the same native snapshot representation and batching as other plots. */
public class VanillaSingleSubLevelRenderData extends VanillaChunkedSubLevelRenderData {
    public VanillaSingleSubLevelRenderData(final ClientSubLevel subLevel) { super(subLevel); }

    public @Nullable BlockEntity getRenderBlockEntity() {
        final var bounds = this.getSubLevel().getPlot().getBoundingBox();
        return bounds == null ? null : this.getSubLevel().getLevel().getBlockEntity(
                new net.minecraft.core.BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
    }
}
