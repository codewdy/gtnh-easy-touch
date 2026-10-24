package io.github.wdy.easytouch.nei;

import java.io.IOException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;

/** Waits for NEI's REQUEST_CONTAINER reply to be applied, not merely received by Netty. */
public final class ContainerSyncBarrier extends ChannelInboundHandlerAdapter implements AutoCloseable {
    private static final String HANDLER_NAME = "gtnheasytouch_container_sync";
    private final Channel channel;
    private final int windowId;
    private final int slotCount;
    private boolean contentsReceived;
    private boolean markerQueued;
    private volatile boolean applied;
    private volatile boolean closed;

    public ContainerSyncBarrier(Channel channel, int windowId, int slotCount) {
        this.channel = channel;
        this.windowId = windowId;
        this.slotCount = slotCount;
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, this);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
        boolean completeReply = false;
        if (!closed && !markerQueued) {
            if (packet instanceof S30PacketWindowItems) {
                S30PacketWindowItems contents = (S30PacketWindowItems) packet;
                contentsReceived = contents.func_148911_c() == windowId
                    && contents.func_148910_d().length == slotCount;
            } else if (contentsReceived && packet instanceof S2FPacketSetSlot) {
                S2FPacketSetSlot slot = (S2FPacketSetSlot) packet;
                completeReply = slot.func_149175_c() == -1 && slot.func_149173_d() == -1;
            }
        }

        // Preserve the real packets. Vanilla queues them for the client tick; observing reception
        // here does NOT mean that the inventory or held stack has been updated yet.
        ctx.fireChannelRead(packet);
        if (completeReply) {
            markerQueued = true;
            ctx.fireChannelRead(new AppliedMarker());
        }
    }

    public boolean applied() { return applied && !closed; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (channel.pipeline().get(HANDLER_NAME) == this) channel.pipeline().remove(this);
    }

    /** Local queue marker after both reply packets; never serialized or sent to the server. */
    private final class AppliedMarker extends Packet {
        @Override
        public void processPacket(INetHandler handler) {
            if (!closed) applied = true;
        }

        @Override public void readPacketData(PacketBuffer data) throws IOException {
            throw new IOException("Local container sync marker cannot be decoded");
        }

        @Override public void writePacketData(PacketBuffer data) throws IOException {
            throw new IOException("Local container sync marker cannot be sent");
        }
    }
}
