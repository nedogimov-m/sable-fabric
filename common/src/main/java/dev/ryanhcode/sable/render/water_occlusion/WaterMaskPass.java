package dev.ryanhcode.sable.render.water_occlusion;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.UniformType;
import foundry.veil.api.client.render.gpu.GpuFramebuffer;
import foundry.veil.api.client.render.gpu.GpuShaderProgram;
import foundry.veil.api.client.render.gpu.GpuUniformBuffer;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Native front/back depth passes with copied, independently uploaded region transforms. */
public final class WaterMaskPass implements AutoCloseable {
    public static final String TRANSFORMS = "SableRegionTransforms";
    private final GpuShaderProgram program;

    public WaterMaskPass(final GpuDevice device, final ShaderSource sources) {
        final Identifier shader = Identifier.fromNamespaceAndPath("sable", "core/water_mask");
        final RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("sable", "water_mask"))
                .withVertexShader(shader).withFragmentShader(shader)
                .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false).withDepthStencilState(DepthStencilState.DEFAULT)
                .withBindGroupLayout(BindGroupLayout.builder().withUniform(TRANSFORMS, UniformType.UNIFORM_BUFFER).build()).build();
        this.program = new GpuShaderProgram(device, pipeline, sources, Runnable::run);
        this.program.createUniformBlock(TRANSFORMS, GpuUniformBuffer.layoutBuilder()
                .field("ModelViewMat", GpuUniformBuffer.ValueType.MAT4)
                .field("ProjMat", GpuUniformBuffer.ValueType.MAT4)
                .field("FaceMode", GpuUniformBuffer.ValueType.FLOAT).build());
    }

    public record Input(VertexArray.Draw mesh, Matrix4fc modelView) {
        public Input { modelView = new Matrix4f(modelView); }
    }

    /** The caller keeps this program lease and each input mesh lease until encoder.submit(). */
    public GpuShaderProgram.Lease render(final CommandEncoder encoder, final GpuFramebuffer target,
                                         final Matrix4fc projection, final List<Input> inputs,
                                         final float faceMode, final double clearDepth) {
        final var lease = this.program.retain();
        try {
            final List<Map<String, com.mojang.renderpearl.api.buffers.GpuBufferSlice>> bindings = new ArrayList<>();
            final var transforms = this.program.getUniformBlock(TRANSFORMS);
            transforms.setMatrix("ProjMat", projection);
            transforms.setFloat("FaceMode", faceMode);
            for (final Input input : inputs) {
                input.mesh.validatePipeline(this.program.definition());
                transforms.setMatrix("ModelViewMat", input.modelView);
                bindings.add(this.program.prepareUniformSnapshots(encoder, this.program.snapshotOwnedUniforms()));
            }
            try (final var pass = target.beginPass(encoder, List.of(), OptionalDouble.of(clearDepth))) {
                for (int i = 0; i < inputs.size(); i++) {
                    this.program.bind(pass, bindings.get(i), Map.of());
                    inputs.get(i).mesh.draw(pass);
                }
            }
            return lease;
        } catch (final RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }

    @Override public void close() { this.program.close(); }
}
