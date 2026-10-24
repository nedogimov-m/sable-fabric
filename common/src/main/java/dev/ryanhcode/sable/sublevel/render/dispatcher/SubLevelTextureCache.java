package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelShaderProcessor;
import foundry.veil.api.client.render.gpu.GpuShaderProgram;
import foundry.veil.api.client.render.gpu.GpuUniformBuffer;
import foundry.veil.api.client.render.shader.ShaderPreDefinitions;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.lwjgl.system.NativeResource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Thread-safe CPU sprite table with explicit native uniform uploads and definition ownership. */
public class SubLevelTextureCache implements NativeResource {
    private static final int SPRITE_SIZE = 8 * Float.BYTES;
    private static final int DEFAULT_SPRITE_COUNT = 32;
    private final Object2IntOpenHashMap<PackedTexture> textures = new Object2IntOpenHashMap<>();
    private final ShaderPreDefinitions definitions;
    private int capacity = DEFAULT_SPRITE_COUNT;
    private ByteBuffer data;
    private boolean dirty = true;
    private boolean closed;
    public SubLevelTextureCache() { this(new ShaderPreDefinitions()); }
    /** The consuming ShaderManager must use the same definitions instance. */
    public SubLevelTextureCache(final ShaderPreDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions);
        definitions.set(FancySubLevelShaderProcessor.BUFFER_SIZE, Integer.toString(this.capacity));
    }
    public ShaderPreDefinitions shaderDefinitions() { return this.definitions; }
    public synchronized int getTextureId(final BakedQuad quad) {
        if (this.closed) throw new IllegalStateException("Sprite cache is closed");
        final var texture = new PackedTexture(UVPair.unpackU(quad.packedUV0()), UVPair.unpackV(quad.packedUV0()),
                UVPair.unpackU(quad.packedUV1()), UVPair.unpackV(quad.packedUV1()),
                UVPair.unpackU(quad.packedUV2()), UVPair.unpackV(quad.packedUV2()),
                UVPair.unpackU(quad.packedUV3()), UVPair.unpackV(quad.packedUV3()));
        if (this.textures.containsKey(texture)) return this.textures.getInt(texture);
        final int id = this.textures.size();
        if (id >= 4096) throw new IllegalStateException("Packed plot texture IDs have only 12 bits");
        this.textures.put(texture, id);
        this.dirty = true;
        return id;
    }
    public synchronized void flush() {
        if (this.closed) throw new IllegalStateException("Sprite cache is closed");
        if (!this.dirty) return;
        if (this.textures.size() > this.capacity) {
            this.capacity = Math.max(this.textures.size(), this.capacity + this.capacity / 2);
            this.definitions.set(FancySubLevelShaderProcessor.BUFFER_SIZE, Integer.toString(this.capacity));
        }
        this.data = ByteBuffer.allocateDirect(this.capacity * SPRITE_SIZE).order(ByteOrder.nativeOrder());
        for (final var entry : this.textures.object2IntEntrySet()) {
            final var texture = entry.getKey();
            this.data.position(entry.getIntValue() * SPRITE_SIZE);
            this.data.putFloat(texture.u0).putFloat(texture.u1).putFloat(texture.u2).putFloat(texture.u3)
                    .putFloat(texture.v0).putFloat(texture.v1).putFloat(texture.v2).putFloat(texture.v3);
        }
        this.data.clear();
        this.dirty = false;
    }
    public synchronized GpuUniformBuffer.Snapshot snapshot() {
        this.flush();
        final var layout = GpuUniformBuffer.layoutBuilder().array("sableSprites", GpuUniformBuffer.ValueType.VEC4, 2 * this.capacity).build();
        return new GpuUniformBuffer.Snapshot(layout, this.data);
    }
    /** Upload a copied per-frame table before beginning the consuming render pass. */
    public GpuBufferSlice prepare(final CommandEncoder encoder) {
        return encoder.transientMemory().uploadGpu(this.snapshot().bytes(),
                RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
    }
    public void bind(final CommandEncoder encoder, final GpuShaderProgram shader) {
        shader.setUniformBlock("SableSprites", this.prepare(encoder));
    }
    @Override public synchronized void free() { this.closed = true; this.textures.clear(); this.data = null; }
    private record PackedTexture(float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) { }
}
