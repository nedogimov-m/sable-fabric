package dev.ryanhcode.sable.fabric.mixin.dynamic_directional_shading;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
    @WrapMethod(method = "compile")
    private SectionCompiler.Results sable$compileInPlot(final SectionPos sectionPos, final RenderSectionRegion region,
                                                        final VertexSorting sorting, final SectionBufferBuilderPack buffers,
                                                        final Operation<SectionCompiler.Results> original) {
        final var container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
        final var cache = (ModelBlockRendererCacheExtension) BlockModelLighter.CACHE.get();
        final boolean previous = cache.sable$getOnSubLevel();
        cache.sable$setOnSubLevel(container != null && container.getPlot(sectionPos.chunk()) != null);
        try {
            return original.call(sectionPos, region, sorting, buffers);
        } finally {
            cache.sable$setOnSubLevel(previous);
        }
    }
}
