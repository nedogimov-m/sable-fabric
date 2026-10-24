package dev.ryanhcode.sable.networking;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.packet_mixin.PacketActuallyInSubLevelExtension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Appends the Sable coordinate-space flag to a vanilla movement codec. */
public final class SubLevelPacketCodec {
    private SubLevelPacketCodec() {
    }

    public static <P> StreamCodec<FriendlyByteBuf, P> extend(final StreamCodec<FriendlyByteBuf, P> vanilla) {
        return new StreamCodec<>() {
            @Override
            public P decode(final FriendlyByteBuf buffer) {
                final P packet = vanilla.decode(buffer);
                ((PacketActuallyInSubLevelExtension) packet).sable$setActuallyInSubLevel(buffer.readBoolean());
                return packet;
            }

            @Override
            public void encode(final FriendlyByteBuf buffer, final P packet) {
                vanilla.encode(buffer, packet);
                buffer.writeBoolean(((PacketActuallyInSubLevelExtension) packet).sable$isActuallyInSubLevel());
            }
        };
    }
}
