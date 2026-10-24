package dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class SodiumWorldRendererMixin {
    @Shadow private @Nullable ClientLevel level;
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void sable$dirtyPlotSection(final int x, final int y, final int z, final boolean playerChanged, final CallbackInfo ci) {
        final var container = this.level == null ? null : SubLevelContainer.getContainer(this.level);
        if (container == null || !container.inBounds(x, z)) return;
        final var subLevel = Sable.HELPER.getContainingClient(x, z);
        if (subLevel != null) subLevel.getRenderData().setDirty(x, y, z, playerChanged);
        ci.cancel();
    }
}
