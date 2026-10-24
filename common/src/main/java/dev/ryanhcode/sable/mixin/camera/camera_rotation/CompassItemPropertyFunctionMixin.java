package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(CompassAngleState.class)
public abstract class CompassItemPropertyFunctionMixin {

    /**
     * @author RyanH
     * @reason Take into account sub-levels
     */
    @Overwrite
    private static double getAngleFromEntityToPos(final ItemOwner owner, final BlockPos pos) {
        Vec3 localPos = Vec3.atCenterOf(pos);
        double entityX = owner.position().x;
        double entityZ = owner.position().z;

        final ActiveSableCompanion helper = Sable.HELPER;
        SubLevel subLevel = helper.getContaining(owner.level(), owner.position());

        if (subLevel == null && owner instanceof final Entity entity) {
            final Entity vehicle = entity.getVehicle();

            if (vehicle != null) {
                subLevel = helper.getContaining(vehicle);

                if (subLevel != null) {
                    final Vec3 localEntityPos = subLevel.lastPose().transformPositionInverse(entity.position());
                    entityX = localEntityPos.x;
                    entityZ = localEntityPos.z;
                }
            }
        }

        if (subLevel != null) {
            localPos = subLevel.lastPose().transformPositionInverse(localPos);
        }

        return Math.atan2(localPos.z() - entityZ, localPos.x() - entityX) / (float) (Math.PI * 2);
    }

}
