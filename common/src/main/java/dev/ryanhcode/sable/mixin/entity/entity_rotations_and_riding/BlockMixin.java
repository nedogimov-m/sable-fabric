package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public class BlockMixin {

    @Redirect(method = "restituteMovementAfterCollisions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;with(Lnet/minecraft/core/Direction$Axis;D)Lnet/minecraft/world/phys/Vec3;", ordinal = 2))
    private Vec3 sable$rotateWithEntity(final Vec3 instance, final Direction.Axis axis, final double value) {
        final Entity entity = (Entity) (Object) this;
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, 1.0f);

        if (orientation == null) {
            return instance.with(axis, value);
        }

        final Vector3d up = orientation.transform(OrientedBoundingBox3d.UNIT_Y, new Vector3d());
        final double dot = up.dot(instance.x, instance.y, instance.z);
        // Vanilla now includes block/entity bounce in the replaced component.
        final double restitution = instance.y == 0.0 ? value : dot * value / instance.y;
        final double change = restitution - dot;
        return instance.add(up.x * change, up.y * change, up.z * change);
    }

}
