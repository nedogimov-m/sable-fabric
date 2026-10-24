package dev.ryanhcode.sable.mixin.sublevel_render.fancy;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import dev.ryanhcode.sable.mixinterface.sublevel_render.fancy.ProgramExtension;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve native include source text before its temporary shaderc allocation is released. */
@Mixin(ShaderSource.CachedIncludeSource.class)
public class ProgramMixin implements ProgramExtension {
    @Unique private String sable$source;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sable$captureSource(final long includeResultPtr, final CallbackInfo ci) {
        this.sable$source = java.util.Objects.requireNonNullElse(ShadercIncludeResult.create(includeResultPtr).contentString(), "");
    }

    @Override public String sable$getSource() { return this.sable$source; }
}
