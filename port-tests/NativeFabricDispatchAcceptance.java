import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** Executes the genuine Fabric26.3 receive implementation without opening a GPU. */
public final class NativeFabricDispatchAcceptance {
    private record ProbePayload(int sequence) implements CustomPacketPayload {
        private static final Type<ProbePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sable", "dispatch_probe"));
        @Override public Type<ProbePayload> type() { return TYPE; }
    }

    public static void main(String[] args) throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var addon = (ClientPlayNetworkAddon) unsafe.allocateInstance(ClientPlayNetworkAddon.class);
        var contextType = Class.forName("net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon$ContextImpl");
        var constructor = contextType.getDeclaredConstructor(Minecraft.class, PacketSender.class);
        constructor.setAccessible(true);
        // The tested dispatch does not access the client or connection. All
        // endpoints retain their genuine classes, without opening a window.
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        var context = (ClientPlayNetworking.Context) constructor.newInstance(client, addon);
        var contextField = ClientPlayNetworkAddon.class.getDeclaredField("context");
        contextField.setAccessible(true);
        contextField.set(addon, context);
        var receive = ClientPlayNetworkAddon.class.getDeclaredMethod("receive", ClientPlayNetworking.PlayPayloadHandler.class, CustomPacketPayload.class);
        receive.setAccessible(true);

        Thread thread = Thread.currentThread();
        List<String> order = new ArrayList<>();
        ClientPlayNetworking.PlayPayloadHandler<ProbePayload> handler = (payload, actualContext) -> {
            if (actualContext != context || Thread.currentThread() != thread) throw new AssertionError("Dispatch context/thread changed");
            order.add("handler" + payload.sequence());
        };
        order.add("before");
        receive.invoke(addon, handler, new ProbePayload(1));
        order.add("between");
        receive.invoke(addon, handler, new ProbePayload(2));
        order.add("after");
        if (!order.equals(List.of("before", "handler1", "between", "handler2", "after"))) {
            throw new AssertionError("Native receive deferred or reordered a payload: " + order);
        }

        RuntimeException sentinel = new RuntimeException("native dispatch sentinel");
        ClientPlayNetworking.PlayPayloadHandler<ProbePayload> throwing = (payload, actualContext) -> { throw sentinel; };
        try {
            receive.invoke(addon, throwing, new ProbePayload(3));
            throw new AssertionError("Native receive deferred the exception");
        } catch (InvocationTargetException expected) {
            if (expected.getCause() != sentinel) throw expected;
        }
        System.out.println("PASS actual Fabric26.3 receive: synchronous order, same context/thread, immediate exception");
    }
}
