package dev.ryanhcode.sable.fabric.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.Direction;
import dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMesh;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public class SableSubLevelRenderPlatformImpl implements SableSubLevelRenderPlatform {

    @Override
    public void tesselateBlock(final SingleBlockSubLevelWrapper blockAndTintGetter, final BlockStateModel bakedModel, final BlockState blockState, final BlockPos pos, final PoseStack poseStack, final VertexConsumer vertexConsumer, final RandomSource randomSource, final long seed, final int packedOverlay, @Nullable final RenderType renderType) {
        final var renderer = new ModelBlockRenderer(false, true, Minecraft.getInstance().getBlockColors());
        renderer.tesselateBlock((x, y, z, quad, instance) -> {
            if (renderType != null && PlotSectionMesh.movingType(quad.materialInfo().layer()) != renderType) return;
            poseStack.pushPose();
            final int oldOverlay = instance.overlayCoords();
            try {
                poseStack.translate(x, y, z);
                instance.setOverlayCoords(packedOverlay);
                vertexConsumer.putBakedQuad(poseStack.last(), quad, instance);
            } finally {
                instance.setOverlayCoords(oldOverlay);
                poseStack.popPose();
            }
        }, 0, 0, 0, blockAndTintGetter, pos, blockState, bakedModel, seed);
    }

    @Override
    public List<RenderType> getRenderLayers(final SingleBlockSubLevelWrapper blockAndTintGetter, final BlockStateModel bakedModel, final BlockState blockState, final BlockPos pos, final RandomSource randomSource) {
        final var layers = new LinkedHashSet<RenderType>();
        final List<BlockStateModelPart> parts = new ArrayList<>();
        randomSource.setSeed(blockState.getSeed(pos));
        bakedModel.collectParts(randomSource, parts);
        for (final var part : parts) {
            for (final var quad : part.getQuads(null)) layers.add(PlotSectionMesh.movingType(quad.materialInfo().layer()));
            for (final var face : Direction.values()) {
                for (final var quad : part.getQuads(face)) layers.add(PlotSectionMesh.movingType(quad.materialInfo().layer()));
            }
        }
        return List.copyOf(layers);
    }

    @Override
    public void tryAddFlywheelVisual(final BlockEntity blockEntity) {
        // no-op
    }
}
