package dev.ryanhcode.sable.mixin.camera.new_camera_types;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;update(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.BEFORE))
    public void sable$setupCamera(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        final CameraType cameraType = this.minecraft.options.getCameraType();

        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW || cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            // Disconnect renders the saving screen after clearing the level and camera entity.
            final Entity cameraEntity = this.minecraft.getCameraEntity();
            if (this.minecraft.level == null || cameraEntity == null) {
                this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                return;
            }
            final Entity vehicle = cameraEntity.getVehicle();

            if (vehicle != null) {
                final SubLevel subLevel = Sable.HELPER.getContaining(this.minecraft.level, vehicle.position());

                if (subLevel != null) {
                    return;
                }
            }

            this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }
}
