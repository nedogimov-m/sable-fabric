package dev.ryanhcode.sable.render.dynamic_biome;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Native pipeline construction replaces subclassing the old mutable RenderType. */
public final class DynamicBiomeTintRenderTypes {
    private DynamicBiomeTintRenderTypes() {}
    public static RenderType create(final String name, final VertexFormat format, final PrimitiveTopology topology,
                                     final int bufferSize, final VeilRenderTypeBuilder.State state) {
        return VeilRenderType.create("dynamic_biome_tint/" + name, format, topology, bufferSize, true, false, false, state);
    }
}
