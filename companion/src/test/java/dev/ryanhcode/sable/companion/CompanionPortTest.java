package dev.ryanhcode.sable.companion;

import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanionPortTest {
    @Test
    void chunkLookupPreservesSignedCoordinatesThroughRecordAccessors() {
        List<List<Integer>> calls = new ArrayList<>();
        SableCompanion companion = recordingCompanion("getContaining", calls);
        for (int[] coordinates : new int[][] {{0, 0}, {-1, 2}, {31, -32}, {Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
            assertNull(companion.getContaining(null, new ChunkPos(coordinates[0], coordinates[1])));
            assertEquals(List.of(coordinates[0], coordinates[1]), calls.getLast());
        }
        assertEquals(4, calls.size());
    }

    @Test
    void gridLookupPreservesCoordinatesAndResultThroughRecordAccessors() {
        List<List<Integer>> calls = new ArrayList<>();
        SableCompanion companion = recordingCompanion("isInPlotGrid", calls);
        assertTrue(companion.isInPlotGrid(null, new ChunkPos(-1_024, 2_048)));
        assertEquals(List.of(List.of(-1_024, 2_048)), calls);
    }

    @Test
    void serviceDescriptorLoadsTheUpstreamFallbackWithoutClientInitialization() {
        assertInstanceOf(DefaultSableCompanion.class, SableCompanion.INSTANCE);
    }

    private static SableCompanion recordingCompanion(String target, List<List<Integer>> calls) {
        return (SableCompanion) Proxy.newProxyInstance(SableCompanion.class.getClassLoader(),
                new Class<?>[] {SableCompanion.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    if (method.getName().equals(target) && arguments.length == 3) {
                        calls.add(List.of((Integer) arguments[1], (Integer) arguments[2]));
                        return target.equals("isInPlotGrid") ? Boolean.TRUE : null;
                    }
                    throw new AssertionError("Unexpected method: " + method);
                });
    }
}
