package io.github.wdy.easytouch;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import io.github.wdy.easytouch.nei.ContainerSyncBarrier;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContainerSyncBarrierTest {
    private EmbeddedChannel channel;
    private ContainerSyncBarrier barrier;

    @Before
    public void attachToPacketQueue() {
        channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        // EmbeddedChannel has a consuming inbound collector at its tail; mirror NetworkManager
        // before that collector so our barrier sees the same decoded packets as in the game.
        channel.pipeline().addFirst("packet_handler", new ChannelInboundHandlerAdapter());
        barrier = new ContainerSyncBarrier(channel, 7, 46);
    }

    @After
    public void close() {
        barrier.close();
        channel.finish();
    }

    @Test
    public void replyMustBeAppliedOnClientBeforeCraftingCanBegin() {
        S30PacketWindowItems contents = contents(7, 46);
        S2FPacketSetSlot cursor = cursor();
        channel.writeInbound(contents, cursor);
        assertFalse(barrier.applied()); // Netty has received both; vanilla has not applied either.

        List<String> applied = new ArrayList<>();
        INetHandlerPlayClient handler = (INetHandlerPlayClient) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { INetHandlerPlayClient.class },
            (proxy, method, args) -> { applied.add(method.getName()); return null; });

        Packet queuedContents = (Packet) channel.readInbound();
        assertSame(contents, queuedContents);
        queuedContents.processPacket(handler);
        assertFalse(barrier.applied()); // Full slot snapshot alone is insufficient.
        Packet queuedCursor = (Packet) channel.readInbound();
        assertSame(cursor, queuedCursor);
        queuedCursor.processPacket(handler);
        assertFalse(barrier.applied());
        Packet marker = (Packet) channel.readInbound();
        assertFalse(marker.hasPriority()); // Cannot jump ahead of the queued real packets.
        marker.processPacket(handler);
        assertTrue(barrier.applied());
        assertEquals(Arrays.asList("handleWindowItems", "handleSetSlot"), applied);
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound()); // No synthetic network messages sent to the server.
    }

    @Test
    public void delayedCursorSnapshotPreventsEarlyStart() {
        channel.writeInbound(contents(7, 46));
        assertNotNull(channel.readInbound());
        assertNull(channel.readInbound());
        assertFalse(barrier.applied());
        channel.writeInbound(new S2FPacketSetSlot(7, 4, null));
        assertNotNull(channel.readInbound());
        assertNull(channel.readInbound());
        channel.writeInbound(cursor());
        assertNotNull(channel.readInbound());
        ((Packet) channel.readInbound()).processPacket(null);
        assertTrue(barrier.applied());
    }

    @Test
    public void unrelatedWindowAndWrongSlotCountCannotReleaseBarrier() {
        channel.writeInbound(cursor(), contents(8, 46), cursor(), contents(7, 45), cursor());
        for (int i = 0; i < 5; i++) assertNotNull(channel.readInbound());
        assertNull(channel.readInbound());
        assertFalse(barrier.applied());
    }

    @Test
    public void cancellingBeforeQueuedReplyIsAppliedIgnoresLateMarker() {
        channel.writeInbound(contents(7, 46), cursor());
        channel.readInbound();
        channel.readInbound();
        Packet marker = (Packet) channel.readInbound();
        barrier.close();
        marker.processPacket(null);
        assertFalse(barrier.applied());
        assertNull(channel.pipeline().context(barrier));
        channel.writeInbound(contents(7, 46), cursor());
        assertNotNull(channel.readInbound());
        assertNotNull(channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    public void repeatedSnapshotsQueueOnlyOneCompletion() {
        channel.writeInbound(contents(7, 46), cursor(), contents(7, 46), cursor());
        int packets = 0;
        while (channel.readInbound() != null) packets++;
        assertEquals(5, packets);
    }

    private static S30PacketWindowItems contents(int windowId, int size) {
        return new S30PacketWindowItems(windowId, Collections.<ItemStack>nCopies(size, null));
    }

    private static S2FPacketSetSlot cursor() { return new S2FPacketSetSlot(-1, -1, null); }
}
