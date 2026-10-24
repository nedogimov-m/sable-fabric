package dev.ryanhcode.sable.mixin.plot.lighting;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSectionRegion.class)
public class RenderChunkRegionMixin implements SubLevelContainerHolder {


    @Unique private ClientLevel sable$level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$rememberLevel(final ClientLevel level, final int x, final int y, final int z,
                                     final SectionCopy[] sections, final CallbackInfo ci) {
        this.sable$level = level;
    }

    @Override
    public SubLevelContainer sable$getPlotContainer() {
        return SubLevelContainer.getContainer(this.sable$level);
    }
}
