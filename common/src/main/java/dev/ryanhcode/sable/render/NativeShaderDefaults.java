package dev.ryanhcode.sable.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import foundry.veil.api.client.render.gpu.GpuFramebuffer;
import foundry.veil.api.client.render.gpu.GpuUniformBuffer;

import java.util.OptionalDouble;
import java.util.function.Consumer;

/** Real disabled-state resources, prepared before opening any world or GUI render pass. */
public final class NativeShaderDefaults implements AutoCloseable {
    private final GpuUniformBuffer parameters;
    private final GpuFramebuffer depth;
    private final GpuSampler sampler;

    public NativeShaderDefaults(GpuDevice device, String label, GpuUniformBuffer.Layout layout, Consumer<GpuUniformBuffer> initialize) {
        this.parameters = new GpuUniformBuffer(device, label + " disabled parameters", layout);
        try {
            this.depth = GpuFramebuffer.builder(label + " disabled depth", 1, 1).depth(GpuFormat.D32_FLOAT).build(device);
            try {
                this.sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
                try {
                    initialize.accept(this.parameters);
                    var encoder = device.createCommandEncoder();
                    this.parameters.upload(encoder);
                    this.depth.clear(encoder, null, 0);
                    encoder.submit();
                } catch (RuntimeException | Error failure) { this.sampler.close(); throw failure; }
            } catch (RuntimeException | Error failure) { this.depth.close(); throw failure; }
        } catch (RuntimeException | Error failure) { this.parameters.close(); throw failure; }
    }
    public GpuUniformBuffer parameters() { return this.parameters; }
    public GpuFramebuffer depth() { return this.depth; }
    public GpuSampler sampler() { return this.sampler; }
    @Override public void close() { this.parameters.close(); this.depth.close(); this.sampler.close(); }
}
