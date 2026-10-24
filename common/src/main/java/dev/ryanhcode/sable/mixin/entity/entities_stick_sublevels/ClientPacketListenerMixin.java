package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.packet_mixin.PacketActuallyInSubLevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @WrapOperation(method = "handleMoveEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FF)V"))
    private void sable$moveAndRotate(final Entity entity, final PositionPath path, final float yRot, final float xRot,
                                    final Operation<Void> original,
                                    @Local(argsOnly = true) final ClientboundMoveEntityPacket packet) {
        this.sable$move(entity, path, yRot, xRot, true, true, sable$actuallyInSubLevel(packet));
    }

    @WrapOperation(method = "handleMoveEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;)V"))
    private void sable$move(final Entity entity, final PositionPath path, final Operation<Void> original,
                            @Local(argsOnly = true) final ClientboundMoveEntityPacket packet) {
        this.sable$move(entity, path, entity.getYRot(), entity.getXRot(), false, true, sable$actuallyInSubLevel(packet));
    }

    @WrapOperation(method = "handleEntityPositionSync", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FF)V"))
    private void sable$sync(final Entity entity, final PositionPath path, final float yRot, final float xRot,
                           final Operation<Void> original,
                           @Local(argsOnly = true) final ClientboundEntityPositionSyncPacket packet) {
        this.sable$move(entity, path, yRot, xRot, true, true, sable$actuallyInSubLevel(packet));
    }

    @WrapOperation(method = "handleEntityPositionSync", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;snapTo(Lnet/minecraft/world/phys/Vec3;FF)V"))
    private void sable$snap(final Entity entity, final Vec3 position, final float yRot, final float xRot,
                           final Operation<Void> original,
                           @Local(argsOnly = true) final ClientboundEntityPositionSyncPacket packet) {
        this.sable$move(entity, PositionPath.of(position), yRot, xRot, true, false, sable$actuallyInSubLevel(packet));
    }

    @WrapOperation(method = "handleEntityPositionSync", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$syncDistance(final Vec3 current, final Vec3 target, final Operation<Double> original,
                                     @Local(argsOnly = true) final ClientboundEntityPositionSyncPacket packet,
                                     @Local final Entity entity) {
        final SubLevel subLevel = Sable.HELPER.getContaining(entity.level(), target);
        if (subLevel != null && !sable$actuallyInSubLevel(packet)) {
            return subLevel.logicalPose().transformPositionInverse(current).distanceToSqr(target);
        }
        return original.call(current, target);
    }

    @WrapOperation(method = "handleTeleportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private boolean sable$teleport(final PositionMoveRotation change, final Set<Relative> relatives,
                                  final Entity entity, final boolean interpolate, final Operation<Boolean> original,
                                  @Local(argsOnly = true) final ClientboundTeleportEntityPacket packet) {
        ((EntityStickExtension) entity).sable$setPlotPosition(null);
        final PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(entity), change, relatives);
        final SubLevel subLevel = Sable.HELPER.getContaining(entity.level(), absolute.position());
        if (subLevel != null && !sable$actuallyInSubLevel(packet)) {
            final PositionMoveRotation projected = new PositionMoveRotation(
                    subLevel.logicalPose().transformPosition(absolute.position()),
                    subLevel.logicalPose().transformNormal(absolute.deltaMovement()), absolute.yRot(), absolute.xRot());
            return original.call(projected, Set.of(), entity, interpolate);
        }
        return original.call(change, relatives, entity, interpolate);
    }

    @Unique
    private static boolean sable$actuallyInSubLevel(final Object packet) {
        return packet instanceof final PacketActuallyInSubLevelExtension extension && extension.sable$isActuallyInSubLevel();
    }

    @Unique
    private void sable$move(final Entity entity, PositionPath path, final float yRot, final float xRot,
                            final boolean hasRotation, final boolean interpolate, final boolean actuallyInSubLevel) {
        final EntityStickExtension extension = (EntityStickExtension) entity;
        final Vec3 position = path.endPosition();
        final SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
        final SubLevel subLevel = Sable.HELPER.getContaining(entity.level(), position);
        final Vec3 plotPosition = extension.sable$getPlotPosition();

        if (!actuallyInSubLevel && subLevel == null && container != null && container.inBounds(BlockPos.containing(position))) {
            return;
        }

        if (subLevel != null && !actuallyInSubLevel) {
            if (!(entity instanceof LivingEntity)) {
                path = sable$projectPath(subLevel, path);
                extension.sable$setPlotPosition(null);
            } else {
                if (plotPosition == null) {
                    extension.sable$setPlotPosition(subLevel.logicalPose().transformPositionInverse(entity.position()));
                } else {
                    final SubLevel previous = Sable.HELPER.getContaining(entity.level(), plotPosition);
                    if (previous != null && previous != subLevel) {
                        extension.sable$setPlotPosition(subLevel.logicalPose().transformPositionInverse(previous.logicalPose().transformPosition(plotPosition)));
                    }
                }
                if (interpolate) {
                    // Native interpolation now owns rotation; the plot position
                    // is advanced once in Entity.tick and projected afterwards.
                    entity.moveOrInterpolateTo(null, yRot, xRot, hasRotation);
                    extension.sable$plotLerpTo(path, entity.getType().updateInterval());
                } else {
                    entity.getInterpolation().cancel();
                    extension.sable$setPlotPosition(position);
                    entity.snapTo(subLevel.logicalPose().transformPosition(position), yRot, xRot);
                }
                return;
            }
        } else {
            final SubLevel previous = Sable.HELPER.getContaining(entity.level(), entity.position());
            if (subLevel != null && actuallyInSubLevel && previous != subLevel) {
                entity.setPos(subLevel.logicalPose().transformPositionInverse(entity.position()));
            } else if (previous != null && subLevel == null) {
                entity.setPos(previous.logicalPose().transformPosition(entity.position()));
            }
            extension.sable$setPlotPosition(null);
        }

        if (interpolate) {
            entity.moveOrInterpolateTo(path, yRot, xRot, hasRotation);
        } else {
            entity.snapTo(path.endPosition(), yRot, xRot);
        }
    }

    @Unique
    private static PositionPath sable$projectPath(final SubLevel subLevel, final PositionPath path) {
        if (path instanceof final PositionPath.Stepped stepped) {
            return PositionPath.stepped(stepped.steps().stream().map(step -> new PositionStep(
                    subLevel.logicalPose().transformPosition(step.position()), step.tickOffset())).toList());
        }
        return PositionPath.of(subLevel.logicalPose().transformPosition(path.endPosition()));
    }
}
