package dev.ryanhcode.sable.mixin.sublevel_render;

import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public interface RenderSectionAccessor {
    @Accessor("sectionMesh") AtomicReference<SectionMesh> sable$getSectionMeshReference();
}
