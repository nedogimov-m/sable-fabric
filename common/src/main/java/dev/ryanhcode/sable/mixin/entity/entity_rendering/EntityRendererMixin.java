package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow protected abstract boolean affectedByCulling(Entity entity);
    @Shadow protected abstract AABB getBoundingBoxForCulling(Entity entity, float partialTick);

    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    public final int getPackedLightCoords(final int original, final Entity arg, final float f) {
        final Vec3 lightProbeOffset = arg.getLightProbePosition(f).subtract(arg.getEyePosition(f));
        final Vector3d lightProbePosition = JOMLConversion.toJOML(Sable.HELPER.getEyePositionInterpolated(arg, f)).add(lightProbeOffset.x, lightProbeOffset.y, lightProbeOffset.z);
        final BlockPos blockpos = BlockPos.containing(lightProbePosition.x, lightProbePosition.y, lightProbePosition.z);
        return LightCoordsUtil.pack(sable$getSubLevelAccountedBlockLight(original, arg.level(), LightLayer.BLOCK, blockpos, lightProbePosition),
                sable$getSubLevelAccountedSkyLight(original, arg.level(), LightLayer.SKY, blockpos, lightProbePosition));
    }

    @Redirect(method = "getSkyLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$getSkyLightLevel(final Level instance, final LightLayer lightLayer, final BlockPos blockPos) {
        return sable$getSubLevelAccountedSkyLight(-1, instance, lightLayer, blockPos, JOMLConversion.atCenterOf(blockPos));
    }

    @Unique
    private static int sable$getSubLevelAccountedSkyLight(final int original, final Level instance, final LightLayer lightLayer, final BlockPos blockPos, final Vector3dc probePosition) {
        final Iterable<SubLevel> all = Sable.HELPER.getAllIntersecting(instance, new BoundingBox3d(blockPos));

        int baseBrightness = original == -1 ? instance.getBrightness(lightLayer, blockPos) : LightCoordsUtil.sky(original);
        final BlockPos.MutableBlockPos localPosition = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos heightmapPos = new BlockPos.MutableBlockPos();
        final Vector3d tempProbePosition = new Vector3d();

        for (final SubLevel subLevel : all) {
            final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;

            clientSubLevel.renderPose().transformPositionInverse(probePosition, tempProbePosition);
            localPosition.set(tempProbePosition.x, tempProbePosition.y, tempProbePosition.z);

            final Level level = subLevel.getLevel();
            heightmapPos.setWithOffset(localPosition, Direction.UP);
            final LevelPlot plot = subLevel.getPlot();
            boolean isAboveGround = false;

            while (heightmapPos.getY() >= plot.getBoundingBox().minY()) {
                if (!level.getBlockState(heightmapPos).isAir()) {
                    isAboveGround = true;
                    break;
                }

                heightmapPos.move(Direction.DOWN);
            }

            if (isAboveGround) {
                if (lightLayer == LightLayer.BLOCK) {
                    baseBrightness = Math.max(baseBrightness, level.getBrightness(lightLayer, localPosition));
                } else if (lightLayer == LightLayer.SKY) {
                    final int brightness = clientSubLevel.scaleSkyLight(level.getBrightness(lightLayer, localPosition));
                    baseBrightness = Math.min(baseBrightness, brightness);
                }
            }
        }

        return baseBrightness;
    }

    @Redirect(method = "getBlockLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$getBlockLightLevel(final Level instance, final LightLayer lightLayer, final BlockPos blockPos) {
        return sable$getSubLevelAccountedBlockLight(-1, instance, lightLayer, blockPos, JOMLConversion.atCenterOf(blockPos));
    }

    @Unique
    private static int sable$getSubLevelAccountedBlockLight(final int original, final Level instance, final LightLayer lightLayer, final BlockPos blockPos, final Vector3dc lightProbePosition) {
        final Iterable<SubLevel> all = Sable.HELPER.getAllIntersecting(instance, new BoundingBox3d(blockPos).expand(2.0));

        int l = original == -1 ? instance.getBrightness(lightLayer, blockPos) : LightCoordsUtil.block(original);
        final BlockPos.MutableBlockPos probeBlockPos = new BlockPos.MutableBlockPos();
        final Vector3d tempProbePosition = new Vector3d();

        for (final SubLevel subLevel : all) {
            final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;
            clientSubLevel.renderPose().transformPositionInverse(lightProbePosition, tempProbePosition);
            l = Math.max(l, subLevel.getLevel().getBrightness(lightLayer, probeBlockPos.set(tempProbePosition.x, tempProbePosition.y, tempProbePosition.z)));
        }
        return l;
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void sable$shouldRender(final E entity, final Frustum frustum, final double pCamX, final double pCamY, final double pCamZ, final float partialTick, final CallbackInfoReturnable<Boolean> cir) {
        if (!this.affectedByCulling(entity)) {
            cir.setReturnValue(true);
            return;
        }

        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(entity);

        if (subLevel != null) {
            final AABB aabb = new BoundingBox3d(this.getBoundingBoxForCulling(entity, partialTick))
                    .transform(subLevel.renderPose(partialTick), new BoundingBox3d()).toMojang().inflate(0.5);

            cir.setReturnValue(frustum.isVisible(aabb));

            return;
        }

        // on fast moving sub-levels
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);

        if (trackingSubLevel != null) {
            final Vec3 positionInterpolated = Sable.HELPER.getEyePositionInterpolated(entity, partialTick)
                    .subtract(0.0, entity.getEyeHeight(), 0.0);


            AABB aABB = this.getBoundingBoxForCulling(entity, partialTick).inflate(0.5);
            if (aABB.hasNaN() || aABB.getSize() == 0.0) {
                aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0, entity.getX() + 2.0, entity.getY() + 2.0, entity.getZ() + 2.0);
            }

            final Vec3 nativePosition = new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                    Mth.lerp(partialTick, entity.yOld, entity.getY()), Mth.lerp(partialTick, entity.zOld, entity.getZ()));
            aABB = aABB.move(positionInterpolated.subtract(nativePosition));

            if (frustum.isVisible(aABB)) {
                cir.setReturnValue(true);
            } else {
                if (entity instanceof final Leashable leashable) {
                    final Entity entity2 = leashable.getLeashHolder();
                    if (entity2 != null) {
                        AABB holderBounds = entity2.getInterpolatedBoundingBox(partialTick);
                        final ClientSubLevel holderLevel = Sable.HELPER.getContainingClient(entity2);
                        if (holderLevel != null) {
                            holderBounds = new BoundingBox3d(holderBounds).transform(holderLevel.renderPose(partialTick), new BoundingBox3d()).toMojang();
                        }
                        cir.setReturnValue(frustum.isVisible(holderBounds));
                        return;
                    }
                }

                cir.setReturnValue(false);
            }
        }
    }
}
