package dev.ryanhcode.sable.sublevel.system.ticket;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;

import java.util.Iterator;
import java.util.UUID;

/**
 * Owns one Minecraft ticket per inhabited chunk, with independent sub-level expiry.
 * Minecraft 26.3 deduplicates tickets by type and level, so UUID ownership must be
 * retained here rather than attached to individual Minecraft tickets.
 */
public final class ForcedSubLevelChunkTickets {
    private final Long2ObjectMap<ObjectArraySet<InhabitedChunkTicket>> owners = new Long2ObjectOpenHashMap<>();
    private final TicketType type;
    private final int level;

    public ForcedSubLevelChunkTickets(final TicketType type, final int level) {
        this.type = type;
        this.level = level;
    }

    /** @return whether this is a newly tracked owner of the chunk. */
    public boolean inhabit(final TicketStorage storage, final UUID owner, final long gameTime, final long chunk) {
        ObjectArraySet<InhabitedChunkTicket> residents = this.owners.get(chunk);
        if (residents == null) {
            residents = new ObjectArraySet<>(1);
            this.owners.put(chunk, residents);
            storage.addTicket(chunk, new Ticket(this.type, this.level));
        }

        final InhabitedChunkTicket candidate = new InhabitedChunkTicket(owner, gameTime);
        for (final InhabitedChunkTicket resident : residents) {
            if (resident.equals(candidate)) {
                resident.setLastInhabitedTick(gameTime);
                return false;
            }
        }
        residents.add(candidate);
        return true;
    }

    public void expire(final TicketStorage storage, final long gameTime) {
        final var chunks = this.owners.long2ObjectEntrySet().iterator();
        while (chunks.hasNext()) {
            final var entry = chunks.next();
            final Iterator<InhabitedChunkTicket> residents = entry.getValue().iterator();
            while (residents.hasNext()) {
                if (residents.next().lastInhabitedTick() < gameTime - 20) {
                    residents.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                storage.removeTicket(entry.getLongKey(), new Ticket(this.type, this.level));
                chunks.remove();
            }
        }
    }
}
