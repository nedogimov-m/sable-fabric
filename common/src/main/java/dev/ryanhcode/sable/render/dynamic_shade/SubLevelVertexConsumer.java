package dev.ryanhcode.sable.render.dynamic_shade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * Preserve model shade overrides as normals when shading is evaluated after plot rotation.
 */
public class SubLevelVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;

    public SubLevelVertexConsumer(final VertexConsumer delegate) {
        this.delegate = delegate;
    }


    @Override
    public VertexConsumer addVertex(final float f, final float g, final float h) {
        this.delegate.addVertex(f, g, h);
        return this;
    }

    @Override
    public VertexConsumer setColor(final int i, final int j, final int k, final int l) {
        this.delegate.setColor(i, j, k, l);
        return this;
    }

    @Override
    public VertexConsumer setColor(final int color) {
        this.delegate.setColor(color);
        return this;
    }

    @Override
    public VertexConsumer setUv(final float f, final float g) {
        this.delegate.setUv(f, g);
        return this;
    }

    @Override
    public VertexConsumer setUv1(final int i, final int j) {
        this.delegate.setUv1(i, j);
        return this;
    }

    @Override
    public VertexConsumer setUv2(final int i, final int j) {
        this.delegate.setUv2(i, j);
        return this;
    }

    @Override
    public VertexConsumer setUv3(final float u, final float v) {
        this.delegate.setUv3(u, v);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(final float width) {
        this.delegate.setLineWidth(width);
        return this;
    }

    @Override
    public VertexConsumer setNormal(final float pX, final float pY, final float pZ) {
        this.delegate.setNormal(pX, pY, pZ);
        return this;
    }

    @Override
    public void putBlockBakedQuad(final float x, final float y, final float z, final BakedQuad quad, final QuadInstance instance) {
        this.delegate.putBlockBakedQuad(x, y, z, withShadeNormal(quad), instance);
    }

    @Override
    public void putBakedQuad(final PoseStack.Pose pose, final BakedQuad quad, final QuadInstance instance) {
        this.delegate.putBakedQuad(pose, withShadeNormal(quad), instance);
    }

    @Override
    public void putBakedQuadWithGlint(final PoseStack.Pose pose, final BakedQuad quad, final QuadInstance instance, final PoseStack.Pose glintPose) {
        this.delegate.putBakedQuadWithGlint(pose, withShadeNormal(quad), instance, glintPose);
    }

    public static BakedQuad withShadeNormal(final BakedQuad quad) {
        final var direction = quad.materialInfo().shadeDirectionOverride();
        if (direction == null || direction == quad.direction()) return quad;
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), direction, quad.materialInfo());
    }
}
