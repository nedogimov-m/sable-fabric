package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import foundry.veil.api.client.render.CullFrustum;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import dev.ryanhcode.sable.sublevel.render.vanilla.PlotSectionMesh;
import java.util.List;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.lwjgl.system.NativeResource;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Renders sub-levels into the world.
 */
@ApiStatus.Internal
public interface SubLevelRenderDispatcher extends NativeResource, ResourceManagerReloadListener {

    /**
     * @return The current sub-level renderer instance
     */
    static SubLevelRenderDispatcher get() {
        return SubLevelRenderer.getDispatcher();
    }

    /**
     * Resizes the specified render data.
     *
     * @param subLevel   The sub-level to resize
     * @param renderData The current render data
     * @return The new render data to use
     */
    SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData);

    /**
     * Creates a new render data instance for the specified sub-level.
     *
     * @param subLevel The sub-level to create render data for
     * @return A new render data instance
     */
    SubLevelRenderData createRenderData(final ClientSubLevel subLevel);

    /**
     * Rebuilds the specified sub-levels when F3+A is pressed.
     *
     * @param sublevels The sub-levels to rebuild
     */
    default void rebuild(final Iterable<ClientSubLevel> sublevels) {
        for (final ClientSubLevel sublevel : sublevels) {
            sublevel.getRenderData().rebuild();
        }
    }

    /** Extracts immutable native plot mesh inputs on the world thread. */
    List<PlotSectionMesh.Snapshot> extractTerrain(Iterable<ClientSubLevel> sublevels, Camera camera, float partialTick);

    void addDebugInfo(final Consumer<String> consumer);

    default void preRenderChunks(final Camera camera) {
    }

    interface BlockEntityRenderer {

        default void renderBlockEntities(final Collection<BlockEntity> blockEntities, final PoseStack poseStack, final float partialTick, final double cameraX, final double cameraY, final double cameraZ) {
            for (final BlockEntity blockEntity : blockEntities) {
                this.renderSingleBE(blockEntity, poseStack, partialTick, cameraX, cameraY, cameraZ);
            }
        }

        void renderSingleBE(final BlockEntity blockEntity, final PoseStack poseStack, final float partialTick, final double cameraX, final double cameraY, final double cameraZ);

        BlockEntityRenderDispatcher getBlockEntityRenderDispatcher();
    }
}
