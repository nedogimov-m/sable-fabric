package dev.ryanhcode.sable.mixin.tracking_points;

import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.world.level.storage.ValueInput;
import dev.ryanhcode.sable.mixinterface.tracking_points.EntityTrackingPointExtension;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityTrackingPointExtension {

    @Unique private boolean sable$skipRestoredVehicle;

    @Override
    public boolean sable$shouldSkipRestoredVehicle() {
        return this.sable$skipRestoredVehicle;
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void sable$beginLoad(final ValueInput input, final CallbackInfo ci) {
        this.sable$skipRestoredVehicle = false;
    }

    @Shadow private Level level;

    @Shadow public abstract void setPosRaw(double d, double e, double f);

    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V", shift = At.Shift.AFTER))
    private void sable$load(final ValueInput compoundTag, final CallbackInfo ci) {
        if (this.level instanceof ServerLevel && compoundTag.read("LoginPoint", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad((ServerLevel) this.level);
            final SubLevelTrackingPointSavedData.TakenLoginPoint point = data.take(compoundTag.read("LoginPoint", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), true);

            if (point != null) {
                final Vector3dc position = point.position();
                this.setPosRaw(position.x(), position.y(), position.z());

                if (point.subLevelId() != null && this instanceof final PlayerFreezeExtension extension) {
                    extension.sable$freezeTo(point.subLevelId(), point.localAnchor().add(0.0, 0.2, 0.0));
                }

                // kick them out of vehicle
                this.sable$skipRestoredVehicle = true;
            }
        }
    }
}
