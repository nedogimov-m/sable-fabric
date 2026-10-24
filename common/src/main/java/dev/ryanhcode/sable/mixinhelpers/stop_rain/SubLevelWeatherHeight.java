package dev.ryanhcode.sable.mixinhelpers.stop_rain;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;

public final class SubLevelWeatherHeight {
    private SubLevelWeatherHeight() {}

    public static int getSubLevelHeight(final Level level, final int pX, final int yOffset, final int pZ) {
        final LevelAccelerator accelerator = new LevelAccelerator(level);

        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        final Vector3d checkingPos = new Vector3d();
        final Vector3d localUp = new Vector3d(0, 1, 0);

        final BoundingBox3dc minMaxBB = new BoundingBox3d(pX, level.getMinY(), pZ, pX + 1, (level.getMaxY() + 1), pZ + 1);
        int maxHeight = Integer.MIN_VALUE;
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, minMaxBB)) {
            subLevel.logicalPose().transformPositionInverse(checkingPos.set(pX + 0.5f, subLevel.boundingBox().maxY(), pZ + 0.5f));
            subLevel.logicalPose().transformNormalInverse(localUp.set(0, 1, 0));

            final double checkingDistance = subLevel.boundingBox().maxY() - subLevel.boundingBox().minY();
            for (int i = 0; i < checkingDistance; i++) {
                checkingPos.sub(localUp);

                final BlockState gatheredState = accelerator.getBlockState(mutableBlockPos.set(checkingPos.x, checkingPos.y, checkingPos.z));
                if (Heightmap.Types.MOTION_BLOCKING.isOpaque().test(gatheredState)) {
                    subLevel.logicalPose().transformPosition(checkingPos);
                    maxHeight = (int) Math.max(maxHeight, checkingPos.y + yOffset);
                    break;
                }
            }
        }

        return maxHeight;
    }

}
