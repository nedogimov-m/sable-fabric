package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** CPU-owned native tessellation inputs. No live world or mutable tessellator reaches submission. */
public record PlotSectionMesh(SectionPos section, Map<ChunkSectionLayer, List<Vertex>> layers, boolean dynamicShading,
                              List<PlotSectionMeshExtension.Geometry> extensions) {
    private static final CardinalLighting UNSHADED = new CardinalLighting(1, 1, 1, 1, 1, 1);

    private static volatile java.util.function.Function<ChunkSectionLayer, RenderType> worldRenderTypeFactory = PlotSectionMesh::movingType;
    /** Adds plot-specific native pipeline state while detached/group submissions retain their normal type. */
    public static void setWorldRenderTypeFactory(java.util.function.Function<ChunkSectionLayer, RenderType> factory) {
        worldRenderTypeFactory = java.util.Objects.requireNonNull(factory);
    }

    public PlotSectionMesh {
        final Map<ChunkSectionLayer, List<Vertex>> copied = new EnumMap<>(ChunkSectionLayer.class);
        layers.forEach((layer, vertices) -> copied.put(layer, List.copyOf(vertices)));
        layers = Map.copyOf(copied);
        extensions = List.copyOf(extensions);
    }

    public PlotSectionMesh(SectionPos section, Map<ChunkSectionLayer, List<Vertex>> layers, boolean dynamicShading) {
        this(section, layers, dynamicShading, List.of());
    }

    public record Vertex(float x, float y, float z, int color, float u, float v, int light, int overlay,
                         float nx, float ny, float nz, boolean directional) {}

    public static PlotSectionMesh compile(final SectionPos section, final RenderSectionRegion region, final boolean dynamic) {
        final Minecraft minecraft = Minecraft.getInstance();
        final var models = minecraft.getModelManager();
        final BlockAndTintGetter view = new View(region, dynamic);
        final ModelBlockRenderer blocks = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), true, minecraft.getBlockColors());
        final FluidRenderer fluids = new FluidRenderer(models.getFluidStateModelSet());
        final Map<ChunkSectionLayer, Capture> captures = new EnumMap<>(ChunkSectionLayer.class);
        final var extensions = PlotSectionMeshExtension.begin(section, dynamic);
        final BlockPos origin = section.origin();
        BlockModelLighter.enableCaching();
        try {
            for (final BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(15, 15, 15))) {
                final BlockState state = view.getBlockState(pos);
                if (state.isAir()) continue;
                final FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) fluids.tesselate(region, pos,
                        layer -> captures.computeIfAbsent(layer, ignored -> new Capture()).directional(false), state, fluid);
                if (state.getRenderShape() == RenderShape.MODEL) {
                    final boolean opaque = ModelBlockRenderer.forceOpaque(minecraft.options.cutoutLeaves().get(), state);
                    blocks.tesselateBlock((x, y, z, quad, instance) -> {
                        for (var extension : extensions) extension.quad(state, x, y, z, quad, instance);
                        final ChunkSectionLayer layer = opaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                        final Capture capture = captures.computeIfAbsent(layer, ignored -> new Capture()).directional(dynamic);
                        capture.putBlockBakedQuad(x, y, z, dynamic ? SubLevelVertexConsumer.withShadeNormal(quad) : quad, instance);
                    }, SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()),
                            view, pos, state, models.getBlockStateModelSet().get(state), state.getSeed(pos));
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }
        final Map<ChunkSectionLayer, List<Vertex>> layers = new EnumMap<>(ChunkSectionLayer.class);
        captures.forEach((layer, capture) -> layers.put(layer, capture.finish()));
        return new PlotSectionMesh(section, layers, dynamic, extensions.stream().map(PlotSectionMeshExtension.Collector::finish)
                .filter(java.util.Objects::nonNull).toList());
    }

    /** Contains only copied per-frame values and an immutable mesh. */
    public record Snapshot(PlotSectionMesh mesh, Vec3 cameraRelativeOrigin, Quaternionfc orientation, Vector3fc scale,
                           CardinalLighting lighting, int skyLightScale, List<PlotSectionMeshExtension.Snapshot> extensions) {
        public Snapshot {
            orientation = new Quaternionf(orientation);
            scale = new Vector3f(scale);
            extensions = List.copyOf(extensions);
        }

        public Snapshot(PlotSectionMesh mesh, Vec3 origin, Quaternionfc orientation, Vector3fc scale,
                        CardinalLighting lighting, int skyLightScale) {
            this(mesh, origin, orientation, scale, lighting, skyLightScale, List.of());
        }

        public Snapshot withExtensions(dev.ryanhcode.sable.sublevel.ClientSubLevel owner, float partialTick) {
            return new Snapshot(this.mesh, this.cameraRelativeOrigin, this.orientation, this.scale, this.lighting, this.skyLightScale,
                    this.mesh.extensions.stream().map(extension -> extension.extract(owner, this, partialTick))
                            .filter(java.util.Objects::nonNull).toList());
        }

        public void submit(final SubmitNodeCollector nodes) {
            this.submit(new PoseStack(), nodes, true);
        }

        /** Adds this copied plot frame to a caller-owned Ponder/GUI pose and native collector. */
        public void submit(final PoseStack parent, final SubmitNodeCollector nodes) {
            this.submit(parent, nodes, false);
        }

        private void submit(final PoseStack parent, final SubmitNodeCollector nodes, final boolean world) {
            final PoseStack pose = new PoseStack();
            pose.mulPose(parent.last().pose());
            pose.translate(this.cameraRelativeOrigin);
            pose.rotate(this.orientation);
            pose.scale(this.scale.x(), this.scale.y(), this.scale.z());
            for (final var layer : this.mesh.layers.entrySet()) {
                nodes.submitCustomGeometry(pose, world ? worldRenderTypeFactory.apply(layer.getKey()) : movingType(layer.getKey()), (snapshot, consumer) -> {
                    final Vector3f normal = new Vector3f();
                    for (final Vertex vertex : layer.getValue()) {
                        final int color;
                        if (vertex.directional) {
                            normal.set(vertex.nx, vertex.ny, vertex.nz).div(this.scale).normalize().rotate(this.orientation);
                            color = shadeColor(vertex.color, directionalBrightness(this.lighting, normal));
                        } else {
                            color = vertex.color;
                        }
                        final int sky = (int) ((vertex.light >>> 20) * (this.skyLightScale / 15.0f));
                        final int light = vertex.light & 0xfffff | sky << 20;
                        consumer.addVertex(snapshot, vertex.x, vertex.y, vertex.z).setColor(color).setUv(vertex.u, vertex.v)
                                .setLight(light).setOverlay(vertex.overlay).setNormal(snapshot, vertex.nx, vertex.ny, vertex.nz);
                    }
                });
            }
            for (var extension : this.extensions) {
                if (world) extension.submit(nodes);
                else extension.submitInGroup(parent.last().copy(), nodes);
            }
        }
    }

    public static RenderType movingType(final ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    public static float directionalBrightness(final CardinalLighting lighting, final Vector3fc normal) {
        final float down = Math.max(-normal.y(), 0), up = Math.max(normal.y(), 0);
        final float north = Math.max(-normal.z(), 0), south = Math.max(normal.z(), 0);
        final float west = Math.max(-normal.x(), 0), east = Math.max(normal.x(), 0);
        return down * down * down * lighting.down() + up * up * up * lighting.up()
                + north * north * lighting.north() + south * south * lighting.south()
                + west * west * lighting.west() + east * east * lighting.east();
    }

    public static int shadeColor(final int color, final float brightness) {
        return color & 0xff000000 | Math.min(255, (int) (((color >>> 16) & 255) * brightness)) << 16
                | Math.min(255, (int) (((color >>> 8) & 255) * brightness)) << 8
                | Math.min(255, (int) ((color & 255) * brightness));
    }

    static final class Capture implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private boolean directional, currentDirectional, pending;
        private float x, y, z, u, v, nx, ny, nz;
        private int color, light, overlay;

        Capture directional(final boolean value) { this.directional = value; return this; }
        private void flush() {
            if (!this.pending) return;
            this.vertices.add(new Vertex(this.x, this.y, this.z, this.color, this.u, this.v, this.light, this.overlay,
                    this.nx, this.ny, this.nz, this.currentDirectional));
            this.pending = false;
        }
        List<Vertex> finish() {
            this.flush();
            if (this.vertices.size() % 4 != 0) throw new IllegalStateException("Incomplete native plot quad");
            return List.copyOf(this.vertices);
        }
        @Override public VertexConsumer addVertex(final float x, final float y, final float z) {
            this.flush();
            this.x = x; this.y = y; this.z = z;
            this.color = -1; this.light = 0; this.overlay = OverlayTexture.NO_OVERLAY;
            this.u = this.v = this.nx = this.nz = 0; this.ny = 1;
            this.currentDirectional = this.directional; this.pending = true;
            return this;
        }
        @Override public VertexConsumer setColor(final int r, final int g, final int b, final int a) { return this.setColor(a << 24 | r << 16 | g << 8 | b); }
        @Override public VertexConsumer setColor(final int color) { this.color = color; return this; }
        @Override public VertexConsumer setUv(final float u, final float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(final int u, final int v) { this.overlay = u & 0xffff | v << 16; return this; }
        @Override public VertexConsumer setUv2(final int u, final int v) { this.light = u & 0xffff | v << 16; return this; }
        @Override public VertexConsumer setUv3(final float u, final float v) { return this; }
        @Override public VertexConsumer setNormal(final float x, final float y, final float z) { this.nx = x; this.ny = y; this.nz = z; return this; }
        @Override public VertexConsumer setLineWidth(final float width) { return this; }
    }

    private record View(BlockAndTintGetter delegate, boolean dynamic) implements BlockAndTintGetter {
        @Override public CardinalLighting cardinalLighting() { return this.dynamic ? UNSHADED : this.delegate.cardinalLighting(); }
        @Override public int getBlockTint(final BlockPos pos, final ColorResolver resolver) { return this.delegate.getBlockTint(pos, resolver); }
        @Override public BlockEntity getBlockEntity(final BlockPos pos) { return this.delegate.getBlockEntity(pos); }
        @Override public BlockState getBlockState(final BlockPos pos) { return this.delegate.getBlockState(pos); }
        @Override public FluidState getFluidState(final BlockPos pos) { return this.delegate.getFluidState(pos); }
        @Override public LevelLightEngine getLightEngine() { return this.delegate.getLightEngine(); }
        @Override public int getBrightness(final LightLayer layer, final BlockPos pos) { return this.delegate.getBrightness(layer, pos); }
        @Override public int getRawBrightness(final BlockPos pos, final int darken) { return this.delegate.getRawBrightness(pos, darken); }
        @Override public int getHeight() { return this.delegate.getHeight(); }
        @Override public int getMinY() { return this.delegate.getMinY(); }
    }
}
