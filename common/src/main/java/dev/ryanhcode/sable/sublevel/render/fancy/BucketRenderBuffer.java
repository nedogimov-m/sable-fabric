package dev.ryanhcode.sable.sublevel.render.fancy;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import foundry.veil.api.client.render.vertex.VertexArray;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.BitSet;
import java.util.Map;
import java.util.WeakHashMap;

/** CPU-owned packed instances uploaded explicitly into a native mesh binding. */
public class BucketRenderBuffer implements NativeResource {
    public static final int QUAD_SIZE = Integer.BYTES * 2;
    private static final int DEFAULT_MAX_QUADS = 1000;
    private static final VertexFormat FORMAT = VertexFormat.builder(1).addAttribute("SableData", GpuFormat.RG32_UINT).build();
    private final BitSet allocated = new BitSet();
    private final Map<VertexArray, Long> uploaded = new WeakHashMap<>();
    private ByteBuffer data = MemoryUtil.memAlloc(DEFAULT_MAX_QUADS * QUAD_SIZE).order(ByteOrder.nativeOrder());
    private int capacity = DEFAULT_MAX_QUADS;
    private int size;
    private long version;
    private long epoch;
    private boolean closed;

    public BucketRenderBuffer() {}

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Plot instance buffer is closed");
    }

    private void resize(final int requested) {
        final int capacity = Math.max(requested, this.capacity + this.capacity / 2);
        this.data = MemoryUtil.memRealloc(this.data, Math.multiplyExact(capacity, QUAD_SIZE)).order(ByteOrder.nativeOrder());
        this.capacity = capacity;
        this.version++;
    }

    public void clear() {
        this.ensureOpen();
        this.allocated.clear();
        this.size = 0;
        this.epoch++;
        this.version++;
    }

    public Slice allocate(final int quadCount) {
        this.ensureOpen();
        if (quadCount <= 0) throw new IllegalArgumentException("Instance allocation must be positive");
        int start = this.allocated.nextClearBit(0);
        while (true) {
            final int end = this.allocated.nextSetBit(start);
            if (end == -1 || end - start >= quadCount) break;
            start = this.allocated.nextClearBit(end);
        }
        final int end = Math.addExact(start, quadCount);
        if (end > this.capacity) this.resize(end);
        this.allocated.set(start, end);
        this.size += quadCount;
        return new Slice(this, start, quadCount, this.epoch);
    }

    public void free(final Slice slice) {
        if (slice.owner != this) throw new IllegalArgumentException("Instance allocation belongs to another buffer");
        if (slice.closed) return;
        if (!this.closed && slice.epoch == this.epoch) {
            this.allocated.clear(slice.offset, slice.offset + slice.length);
            this.size -= slice.length;
        }
        slice.closed = true;
    }

    /** The mesh owns uploaded GPU storage; retained native draws own its submission lifetime. */
    public void bind(final CommandEncoder encoder, final VertexArray mesh) {
        this.ensureOpen();
        if (this.size == 0 || this.uploaded.getOrDefault(mesh, -1L) == this.version) return;
        final ByteBuffer bytes = this.data.duplicate().order(ByteOrder.nativeOrder());
        bytes.clear().limit(this.allocated.length() * QUAD_SIZE);
        mesh.uploadVertexBuffer(encoder, VertexArray.INSTANCE_BUFFER, bytes, FORMAT, VertexArray.DrawUsage.DYNAMIC);
        this.uploaded.put(mesh, this.version);
    }

    @Override
    public void free() {
        if (this.closed) return;
        this.closed = true;
        this.epoch++;
        MemoryUtil.memFree(this.data);
        this.uploaded.clear();
    }

    public static final class Slice implements NativeResource {
        private final BucketRenderBuffer owner;
        private final int offset;
        private final int length;
        private final long epoch;
        private boolean closed;

        private Slice(final BucketRenderBuffer owner, final int offset, final int length, final long epoch) {
            this.owner = owner; this.offset = offset; this.length = length; this.epoch = epoch;
        }
        private void ensureLive() {
            this.owner.ensureOpen();
            if (this.closed || this.epoch != this.owner.epoch) throw new IllegalStateException("Plot instance allocation is retired");
        }
        /** Valid until the next allocation that grows this buffer; write then flush before allocating again. */
        public long write() {
            this.ensureLive();
            return MemoryUtil.memAddress(this.owner.data) + (long) this.offset * QUAD_SIZE;
        }
        public IntBuffer writeInt() { return MemoryUtil.memIntBuffer(this.write(), Math.multiplyExact(this.length, 2)); }
        public void flush() { this.ensureLive(); this.owner.version++; }
        public int offset() { return this.offset; }
        public int length() { return this.length; }
        @Override public void free() { this.owner.free(this); }
    }
}
