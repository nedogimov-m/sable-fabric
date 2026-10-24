package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix f3 crosshair when riding entity in sub-level
 */
@Mixin(DebugCrosshairRenderer.class)
public class GuiMixin {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getModelViewStack()Lorg/joml/Matrix4fStack;"))
    private void sable$onRenderCrosshair(final CallbackInfo ci, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        final Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        final Entity entity = camera.entity();

        final float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        final Quaterniond ridingOrientation = EntitySubLevelRotationHelper.getEntityOrientation(entity, (x) -> ((ClientSubLevel) x).renderPose(), pt, EntitySubLevelRotationHelper.Type.CAMERA);
        mountedOrientation.set(ridingOrientation);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;rotateX(F)Lorg/joml/Matrix4f;"))
    private Matrix4f sable$redirectRotateX(final Matrix4fStack stack, final float angle, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        if (mountedOrientation.get() != null) {
            final float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            final Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            final Entity entity = camera.entity();

            return stack.rotateX(-entity.getViewXRot(pt) * (float) (Math.PI / 180.0));
        }

        return stack.rotateX(angle);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;rotateY(F)Lorg/joml/Matrix4f;"))
    private Matrix4f sable$redirectRotateY(final Matrix4fStack stack, final float angle, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        if (mountedOrientation.get() != null) {
            final float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            final Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            final Entity entity = camera.entity();

            stack.rotateY(entity.getViewYRot(pt) * (float) (Math.PI / 180.0));

            return stack.rotate(new Quaternionf(mountedOrientation.get()).conjugate());
        }

        return stack.rotateY(angle);
    }

}
