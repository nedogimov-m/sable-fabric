package dev.ryanhcode.sable.mixin.entity.entity_rendering.shadows;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinterface.entity.entity_rendering.EntityRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the actual renderer's shadow strength during native extraction. */
@Mixin(EntityRenderer.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "extractShadow", at = @At("HEAD"))
    private void sable$resetShadowStrength(final EntityRenderState state, final Minecraft minecraft,
                                           final Level level, final CallbackInfo ci) {
        ((EntityRenderStateExtension) state).sable$setShadowStrength(0);
    }

    @WrapOperation(method = "extractShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getShadowStrength(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)F"))
    private float sable$captureShadowStrength(final EntityRenderer<?, ?> renderer, final EntityRenderState state,
                                              final Operation<Float> original) {
        final float strength = original.call(renderer, state);
        ((EntityRenderStateExtension) state).sable$setShadowStrength(strength);
        return strength;
    }
}
