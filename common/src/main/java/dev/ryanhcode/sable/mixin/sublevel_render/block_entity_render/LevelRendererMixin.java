package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plot block entities join native extraction before render-thread submission. */
@Mixin(LevelExtractor.class)
public class LevelRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Nullable private ClientLevel level;
    @Shadow @Final private LevelRenderState levelRenderState;

    @Inject(method = "extract", at = @At("TAIL"))
    private void sable$extractPlotBlockEntities(final DeltaTracker deltaTracker, final Camera camera,
                                                final float partialTick, final CallbackInfo ci) {
        if (this.level == null) return;
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;
        new VanillaSubLevelBlockEntityRenderer(this.minecraft.getBlockEntityRenderDispatcher(), this.levelRenderState)
                .extractAll(container.getAllSubLevels(), this.levelRenderState.worldPartialTicks);
    }
}
