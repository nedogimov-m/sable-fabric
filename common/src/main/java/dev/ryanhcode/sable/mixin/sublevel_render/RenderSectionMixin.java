package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla.RenderSectionExtension;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.LinkedHashSet;
import java.util.Set;

/** Native section reset notifications replace the removed dirty-field lifecycle. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public class RenderSectionMixin implements RenderSectionExtension {
    @Unique private Set<DirtyListener> sable$listeners;
    @Unique private boolean sable$listening = true;
    @Inject(method = "reset", at = @At("HEAD"))
    private void sable$resetMesh(final CallbackInfo ci) {
        if (this.sable$listening && this.sable$listeners != null) for (final DirtyListener listener : Set.copyOf(this.sable$listeners)) listener.markDirty((SectionRenderDispatcher.RenderSection) (Object) this);
    }
    @Override public void sable$addDirtyListener(final DirtyListener listener) {
        if (this.sable$listeners == null) this.sable$listeners = new LinkedHashSet<>();
        this.sable$listeners.add(listener);
    }
    @Override public void sable$setListening(final boolean listening) { this.sable$listening = listening; }
}
