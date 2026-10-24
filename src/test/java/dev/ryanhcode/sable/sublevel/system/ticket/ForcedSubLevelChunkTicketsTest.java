package dev.ryanhcode.sable.sublevel.system.ticket;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ForcedSubLevelChunkTicketsTest {
    private static final int LEVEL = 31;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TicketType type() {
        return new TicketType(TicketType.NO_TIMEOUT,
                TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    }

    @Test
    void oneOwnerExpiringDoesNotUnloadAnotherOwner() {
        final TicketStorage storage = new TicketStorage();
        final ForcedSubLevelChunkTickets owners = new ForcedSubLevelChunkTickets(type(), LEVEL);
        final long chunk = ChunkPos.pack(3, -4);
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        assertTrue(owners.inhabit(storage, first, 0, chunk));
        assertTrue(owners.inhabit(storage, second, 10, chunk));
        assertFalse(owners.inhabit(storage, second, 15, chunk));
        assertEquals(1, storage.getTickets(chunk).size());
        assertEquals(LEVEL, storage.getTicketLevelAt(chunk, true));
        assertEquals(LEVEL, storage.getTicketLevelAt(chunk, false));

        owners.expire(storage, 21);
        assertEquals(1, storage.getTickets(chunk).size());
        owners.expire(storage, 35);
        assertEquals(1, storage.getTickets(chunk).size(), "An owner remains valid through exactly 20 ticks");
        owners.expire(storage, 36);
        assertTrue(storage.getTickets(chunk).isEmpty());
    }

    @Test
    void refreshedOwnerAndOtherChunkExpireIndependently() {
        final TicketStorage storage = new TicketStorage();
        final ForcedSubLevelChunkTickets owners = new ForcedSubLevelChunkTickets(type(), LEVEL);
        final UUID owner = UUID.randomUUID();
        final long first = ChunkPos.pack(-30, 14);
        final long second = ChunkPos.pack(14, -30);

        owners.inhabit(storage, owner, 0, first);
        owners.inhabit(storage, owner, 0, second);
        assertFalse(owners.inhabit(storage, owner, 20, first));
        owners.expire(storage, 21);
        assertEquals(1, storage.getTickets(first).size());
        assertTrue(storage.getTickets(second).isEmpty());
        owners.expire(storage, 41);
        assertTrue(storage.getTickets(first).isEmpty());
        assertTrue(owners.inhabit(storage, owner, 42, first));
        assertEquals(1, storage.getTickets(first).size());
    }

    @Test
    void releasingSubLevelPreservesOtherMinecraftTickets() {
        final TicketStorage storage = new TicketStorage();
        final ForcedSubLevelChunkTickets owners = new ForcedSubLevelChunkTickets(type(), LEVEL);
        final long chunk = ChunkPos.pack(0, 0);
        final Ticket foreign = new Ticket(TicketType.FORCED, LEVEL);
        storage.addTicket(chunk, foreign);
        owners.inhabit(storage, UUID.randomUUID(), 0, chunk);
        assertEquals(2, storage.getTickets(chunk).size());

        owners.expire(storage, 21);
        assertEquals(1, storage.getTickets(chunk).size());
        assertSame(foreign, storage.getTickets(chunk).getFirst());
    }
}
