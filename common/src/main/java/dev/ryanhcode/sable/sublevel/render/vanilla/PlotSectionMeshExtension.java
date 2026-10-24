package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client-only extension of the real plot compilation/extraction boundary, registered before rendering. */
public final class PlotSectionMeshExtension {
    private static final List<Factory> FACTORIES = new CopyOnWriteArrayList<>();
    public static void register(Factory factory) { FACTORIES.add(java.util.Objects.requireNonNull(factory)); }
    static List<Collector> begin(SectionPos section, boolean dynamicShading) {
        return FACTORIES.stream().map(factory -> factory.begin(section, dynamicShading)).toList();
    }
    @FunctionalInterface public interface Factory { Collector begin(SectionPos section, boolean dynamicShading); }
    public interface Collector {
        /** The mutable native QuadInstance is valid only during this call; copy every retained value. */
        void quad(BlockState state, float x, float y, float z, BakedQuad quad, QuadInstance instance);
        /** Returns immutable CPU geometry, or null when this section has none. */
        @Nullable Geometry finish();
    }
    public interface Geometry {
        /** Copy owner-dependent values here. A returned snapshot must contain no live level/owner/camera. */
        @Nullable Snapshot extract(ClientSubLevel owner, PlotSectionMesh.Snapshot section, float partialTick);
    }
    @FunctionalInterface public interface Snapshot {
        void submit(SubmitNodeCollector nodes);
        /** Caller-owned Ponder/GUI passes retain base geometry; world-only effects opt out by default. */
        default void submitInGroup(com.mojang.blaze3d.vertex.PoseStack.Pose parent, SubmitNodeCollector nodes) { }
    }
    private PlotSectionMeshExtension() { }
}
