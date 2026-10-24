package dev.ryanhcode.sable.network;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.packet_mixin.PacketActuallyInSubLevelExtension;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDelta;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Launched only by check_packet_hooks.py through the real Fabric server loader. */
public final class SubLevelPacketHookCheck implements ModInitializer {
    private static int completed;

    @Override
    public void onInitialize() {
        try {
            check("teleport", ClientboundTeleportEntityPacket.STREAM_CODEC, () -> new ClientboundTeleportEntityPacket(73,
                    new PositionMoveRotation(new Vec3(123.5, -12, 42.25), new Vec3(1.25, -.5, 0), 30, -18),
                    Set.of(Relative.X, Relative.Y_ROT), true));
            check("position-sync", ClientboundEntityPositionSyncPacket.STREAM_CODEC, () -> new ClientboundEntityPositionSyncPacket(74,
                    PositionPath.stepped(List.of(new PositionStep(new Vec3(10, 70, 20), 2), new PositionStep(new Vec3(11, 70.5, 21), 3))),
                    91, -10, false));
            check("relative-position", ClientboundMoveEntityPacket.Pos.STREAM_CODEC, () -> new ClientboundMoveEntityPacket.Pos(75,
                    new VecDelta.Linear((short) 127, (short) -214, (short) 19), true));
            check("relative-position-rotation", ClientboundMoveEntityPacket.PosRot.STREAM_CODEC, () -> new ClientboundMoveEntityPacket.PosRot(76,
                    new VecDelta.Linear((short) -19, (short) 54, (short) 22), (byte) 56, (byte) -12, false));
            if (completed != 8) throw new AssertionError("Expected 8 codec checks; got " + completed);
            System.out.println("SABLE_PACKET_HOOK_CHECK_PASS: 8 real Fabric-transformed movement packet round trips");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static <P> void check(String name, StreamCodec<FriendlyByteBuf, P> codec, Supplier<P> factory) {
        for (boolean flag : new boolean[] {false, true}) {
            P packet = factory.get();
            ((PacketActuallyInSubLevelExtension) packet).sable$setActuallyInSubLevel(flag);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.buffer());
            try {
                codec.encode(buffer, packet);
                byte[] encoded = new byte[buffer.readableBytes()];
                buffer.getBytes(0, encoded);
                buffer.writeInt(0x57ab1e);
                P decoded = codec.decode(buffer);
                if (((PacketActuallyInSubLevelExtension) decoded).sable$isActuallyInSubLevel() != flag) {
                    throw new AssertionError(name + " lost coordinate-space flag " + flag);
                }
                if (buffer.readInt() != 0x57ab1e || buffer.isReadable()) {
                    throw new AssertionError(name + " did not consume exactly its own wire data");
                }
                codec.encode(copy, decoded);
                byte[] reencoded = new byte[copy.readableBytes()];
                copy.readBytes(reencoded);
                if (!java.util.Arrays.equals(encoded, reencoded)) throw new AssertionError(name + " changed vanilla payload");
                System.out.println("PASS " + name + " flag=" + flag + " bytes=" + encoded.length);
                completed++;
            } finally {
                buffer.release();
                copy.release();
            }
        }
    }
}
