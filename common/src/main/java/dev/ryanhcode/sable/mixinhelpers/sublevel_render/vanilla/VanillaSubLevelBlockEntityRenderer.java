package dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Adds extracted plot block entities to the native level submission list. */
public class VanillaSubLevelBlockEntityRenderer implements SubLevelRenderDispatcher.BlockEntityRenderer {
    private final BlockEntityRenderDispatcher dispatcher;
    private final LevelRenderState levelState;
    private final Set<BlockPos> extracted = new HashSet<>();

    public VanillaSubLevelBlockEntityRenderer(final BlockEntityRenderDispatcher dispatcher, final LevelRenderState levelState) {
        this.dispatcher = dispatcher;
        this.levelState = levelState;
        for (final var state : levelState.blockEntityRenderStates) this.extracted.add(state.blockPos);
    }

    public void extractAll(final Iterable<ClientSubLevel> subLevels, final float partialTick) {
        final Vec3 camera = this.levelState.cameraRenderState.pos;
        final PoseStack pose = new PoseStack();
        for (final ClientSubLevel subLevel : subLevels) {
            if (subLevel.isRemoved()) continue;
            for (final var holder : subLevel.getPlot().getLoadedChunks()) {
                for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                    this.renderSingleBE(blockEntity, pose, partialTick, camera.x, camera.y, camera.z);
                }
            }
        }
    }

    @Override
    public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
        return this.dispatcher;
    }

    @Override
    public void renderSingleBE(final BlockEntity blockEntity, final PoseStack ignoredPose, final float partialTick,
                               final double cameraX, final double cameraY, final double cameraZ) {
        if (this.extracted.contains(blockEntity.getBlockPos())) return;
        final BlockEntityRenderer<BlockEntity, ?> renderer = this.dispatcher.getRenderer(blockEntity);
        if (renderer == null) return;
        final ModelFeatureRenderer.CrumblingOverlay overlay = this.breakOverlay(blockEntity, partialTick, cameraX, cameraY, cameraZ);
        final BlockEntityRenderState state = this.dispatcher.tryExtractRenderState(blockEntity, partialTick, overlay, renderer.shouldRenderOffScreen());
        if (state != null && this.extracted.add(state.blockPos)) this.levelState.blockEntityRenderStates.add(state);
    }

    private ModelFeatureRenderer.CrumblingOverlay breakOverlay(final BlockEntity blockEntity, final float partialTick,
                                                               final double cameraX, final double cameraY, final double cameraZ) {
        int progress = -1;
        for (final var breaking : this.levelState.blockBreakingRenderStates) {
            if (breaking.blockPos().equals(blockEntity.getBlockPos())) progress = Math.max(progress, breaking.progress());
        }
        if (progress < 0) return null;
        final PoseStack pose = new PoseStack();
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            final BlockPos pos = blockEntity.getBlockPos();
            pose.translate(pos.getX() - cameraX, pos.getY() - cameraY, pos.getZ() - cameraZ);
        } else {
            final var renderPose = subLevel.renderPose(partialTick);
            final Vec3 origin = renderPose.transformPosition(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
            pose.translate(origin.x - cameraX, origin.y - cameraY, origin.z - cameraZ);
            pose.rotate(new org.joml.Quaternionf(renderPose.orientation()));
        }
        return new ModelFeatureRenderer.CrumblingOverlay(progress, pose.last().copy());
    }
}
