package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle.EntityRidingSubLevelVehicleHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(EntityType.class)
public class EntityTypeMixin {

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "loadPassengersRecursive",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"))
    private static boolean sable$startRidingEntity(final Entity passenger, final Entity vehicle,
            final boolean force, final boolean sendEvent,
            final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        final SubLevel vehicleSubLevel = Sable.HELPER.getContaining(vehicle);
        if (vehicleSubLevel != null && EntitySubLevelUtil.shouldKick(passenger)) {
            final Vec3 position = EntityRidingSubLevelVehicleHelper.kickRidingEntity(passenger, vehicleSubLevel);
            passenger.setPos(position);
        }
        return original.call(passenger, vehicle, force, sendEvent);
    }
}
