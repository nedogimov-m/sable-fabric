package dev.ryanhcode.sable.render.sky_light_shadow;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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

/** Native plot-only reverse-depth shadow pass, including the block atlas's cutout alpha. */
public final class SkyShadowPass implements AutoCloseable {
    private static final String TRANSFORMS = "SableShadowTransforms";
    private final GpuShaderProgram program;
    public SkyShadowPass(final GpuDevice device, final ShaderSource sources) {
        final var shader = Identifier.fromNamespaceAndPath("sable", "core/sky_shadow");
        final var pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("sable", "sky_shadow"))
                .withVertexShader(shader).withFragmentShader(shader)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false).withDepthStencilState(DepthStencilState.DEFAULT)
                .withBindGroupLayout(BindGroupLayout.builder().withUniform(TRANSFORMS, UniformType.UNIFORM_BUFFER)
                        .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER).build()).build();
        this.program = new GpuShaderProgram(device, pipeline, sources, Runnable::run);
        this.program.createUniformBlock(TRANSFORMS, GpuUniformBuffer.layoutBuilder()
                .field("ModelViewMat", GpuUniformBuffer.ValueType.MAT4)
                .field("ProjMat", GpuUniformBuffer.ValueType.MAT4)
                .field("AlphaCutout", GpuUniformBuffer.ValueType.FLOAT).build());
    }
    public record Input(VertexArray.Draw mesh, Matrix4fc modelView, float alphaCutout) {
        public Input { modelView = new Matrix4f(modelView); }
    }
    /** The caller retains the program and mesh leases through encoder.submit(). */
    public GpuShaderProgram.Lease render(final CommandEncoder encoder, final GpuFramebuffer target,
                                         final Matrix4fc projection, final List<Input> inputs,
                                         final GpuTextureView atlas, final GpuSampler sampler) {
        final var lease = this.program.retain();
        try {
            this.program.setTexture("Sampler0", atlas, sampler);
            final var textures = this.program.snapshotTextures();
            final List<Map<String, com.mojang.renderpearl.api.buffers.GpuBufferSlice>> bindings = new ArrayList<>();
            final var transforms = this.program.getUniformBlock(TRANSFORMS);
            transforms.setMatrix("ProjMat", projection);
            for (final var input : inputs) {
                input.mesh.validatePipeline(this.program.definition());
                transforms.setMatrix("ModelViewMat", input.modelView);
                transforms.setFloat("AlphaCutout", input.alphaCutout);
                bindings.add(this.program.prepareUniformSnapshots(encoder, this.program.snapshotOwnedUniforms()));
            }
            try (final var pass = target.beginPass(encoder, List.of(), OptionalDouble.of(0))) {
                for (int i = 0; i < inputs.size(); i++) {
                    this.program.bind(pass, bindings.get(i), textures);
                    inputs.get(i).mesh.draw(pass);
                }
            }
            return lease;
        } catch (final RuntimeException | Error failure) { lease.close(); throw failure; }
    }
    @Override public void close() { this.program.close(); }
}
