package dev.ryanhcode.sable.mixin.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native resource reloads own pipeline recreation; the removed shader-instance map is not used. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("minecraft") Minecraft sable$getMinecraft();
}
