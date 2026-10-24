package dev.ryanhcode.sable.mixin.entity.arrows_hit_blocks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes the delta movement that arrows get & the direction they face when they hit blocks
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Entity {

    @Shadow
    protected abstract boolean isInGround();

    public AbstractArrowMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @WrapOperation(method = "onHitBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;setPos(Lnet/minecraft/world/phys/Vec3;)V"))
    private void sable$setHitPosition(final AbstractArrow arrow, final Vec3 position, final Operation<Void> original,
                                      @Local(argsOnly = true) final BlockHitResult blockHitResult) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), blockHitResult.getLocation());

        if (subLevel == null) {
            original.call(arrow, position);
            return;
        }

        final Vec3 localPosition = subLevel.logicalPose().transformPositionInverse(this.position());
        final Vec3 difference = blockHitResult.getLocation().subtract(localPosition);

        if (!this.level().isClientSide() && !this.isInGround()) {
            final Vec3 localImpulse = subLevel.logicalPose().transformNormalInverse(this.getDeltaMovement());
            RigidBodyHandle.of((ServerSubLevel) subLevel).applyImpulseAtPoint(localPosition, localImpulse);
        }

        final Vec3 nudge = difference.normalize().scale(0.05F);
        original.call(arrow, localPosition.subtract(nudge));

        // Native26.3 clears velocity after placing the embedded arrow. Retain
        // its local impact direction for orientation before that native clear.
        final Vec3 vec3 = difference;
        final double d = vec3.horizontalDistance();
        this.setXRot((float) (Mth.atan2(vec3.y, d) * 57.2957763671875));
        this.setYRot((float) (Mth.atan2(vec3.x, vec3.z) * 57.2957763671875));

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Inject(method = "startFalling", at = @At("TAIL"))
    private void sable$startFalling(final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        
        if (subLevel != null) {
            EntitySubLevelUtil.kickEntity(subLevel, this);
        }
    }

    @Redirect(method = "shouldFall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean sable$noCollision(final Level level, final AABB aabb) {
        final boolean original = level.noCollision(this, aabb);

        if (!original) return false;

        return CanFallAtleastHelper.canFallAtleastWithSubLevels(level, aabb) == null;
    }
}
