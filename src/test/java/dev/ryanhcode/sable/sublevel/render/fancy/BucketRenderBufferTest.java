package dev.ryanhcode.sable.sublevel.render.fancy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BucketRenderBufferTest {
    @Test
    void adjacentAllocationsNeverOverlapAndFreedRunsAreReused() {
        final var buffer = new BucketRenderBuffer();
        try {
            final var first = buffer.allocate(3);
            final var second = buffer.allocate(2);
            assertEquals(0, first.offset());
            assertEquals(3, second.offset());
            first.writeInt().put(new int[]{11, 12, 13, 14, 15, 16});
            second.writeInt().put(new int[]{21, 22, 23, 24});
            first.flush(); second.flush();
            assertEquals(16, first.writeInt().get(5));
            assertEquals(21, second.writeInt().get(0));
            first.free();
            final var reused = buffer.allocate(3);
            assertEquals(0, reused.offset());
            reused.writeInt().put(new int[]{31, 32, 33, 34, 35, 36});
            reused.flush();
            assertEquals(21, second.writeInt().get(0));
            assertEquals(24, second.writeInt().get(3));
        } finally { buffer.free(); }
    }

    @Test
    void growingNativeStoragePreservesExistingPackedInstanceBytes() {
        final var buffer = new BucketRenderBuffer();
        try {
            final var original = buffer.allocate(1000);
            final var data = original.writeInt();
            for (int i = 0; i < 2000; i++) data.put(i, 0x4a000000 + i);
            original.flush();
            final var appended = buffer.allocate(17);
            assertEquals(1000, appended.offset());
            for (int i = 0; i < 2000; i++) assertEquals(0x4a000000 + i, original.writeInt().get(i));
            appended.writeInt().put(0, 0x12345678);
            appended.flush();
            assertEquals(0x12345678, appended.writeInt().get(0));
            appended.free();
            assertEquals(1000, buffer.allocate(17).offset());
        } finally { buffer.free(); }
    }

    @Test
    void retiredAllocationsCannotWriteAndCannotFreeNewEpochStorage() {
        final var buffer = new BucketRenderBuffer();
        assertThrows(IllegalArgumentException.class, () -> buffer.allocate(0));
        assertThrows(IllegalArgumentException.class, () -> buffer.allocate(-1));
        final var old = buffer.allocate(1);
        buffer.clear();
        assertThrows(IllegalStateException.class, old::write);
        final var current = buffer.allocate(1);
        old.free();
        assertEquals(1, buffer.allocate(1).offset());
        current.free();
        current.free();
        assertThrows(IllegalStateException.class, current::write);
        final var live = buffer.allocate(1);
        buffer.free();
        buffer.free();
        assertThrows(IllegalStateException.class, live::write);
        assertThrows(IllegalStateException.class, () -> buffer.allocate(1));
    }
}
