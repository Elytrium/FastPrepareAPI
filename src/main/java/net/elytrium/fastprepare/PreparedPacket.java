/*
 * Copyright (C) 2021 - 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.fastprepare;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

public class PreparedPacket {

  private final ByteBuf[] packets = new ByteBuf[ProtocolVersion.values().length];
  private final ProtocolVersion minVersion;
  private final ProtocolVersion maxVersion;
  private final PreparedPacketFactory factory;
  private boolean disposed;

  public PreparedPacket(ProtocolVersion minVersion, ProtocolVersion maxVersion, PreparedPacketFactory factory) {
    this.minVersion = minVersion;
    this.maxVersion = maxVersion;
    this.factory = factory;
  }

  public <T> PreparedPacket prepare(T packet) {
    if (packet == null) {
      return this;
    }

    return this.prepare((version) -> packet, ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MAXIMUM_VERSION);
  }

  public <T> PreparedPacket prepare(T[] packets) {
    return this.prepare(Arrays.asList(packets));
  }

  public <T> PreparedPacket prepare(List<T> packets) {
    if (packets == null) {
      return this;
    }

    for (T packet : packets) {
      this.prepare((version) -> packet, ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MAXIMUM_VERSION);
    }

    return this;
  }

  public <T> PreparedPacket prepare(T packet, ProtocolVersion from) {
    if (packet == null) {
      return this;
    }

    return this.prepare((version) -> packet, from, ProtocolVersion.MAXIMUM_VERSION);
  }

  public <T> PreparedPacket prepare(T packet, ProtocolVersion from, ProtocolVersion to) {
    if (packet == null) {
      return this;
    }

    return this.prepare((version) -> packet, from, to);
  }

  public <T> PreparedPacket prepare(T[] packets, ProtocolVersion from) {
    return this.prepare(Arrays.asList(packets), from);
  }

  public <T> PreparedPacket prepare(T[] packets, ProtocolVersion from, ProtocolVersion to) {
    return this.prepare(Arrays.asList(packets), from, to);
  }

  public <T> PreparedPacket prepare(List<T> packets, ProtocolVersion from) {
    if (packets == null) {
      return this;
    }

    for (T packet : packets) {
      this.prepare(packet, from);
    }

    return this;
  }

  public <T> PreparedPacket prepare(List<T> packets, ProtocolVersion from, ProtocolVersion to) {
    if (packets == null) {
      return this;
    }

    for (T packet : packets) {
      this.prepare(packet, from, to);
    }

    return this;
  }

  public <T> PreparedPacket prepare(Function<ProtocolVersion, T> packet) {
    return this.prepare(packet, ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MAXIMUM_VERSION);
  }

  public <T> PreparedPacket prepare(Function<ProtocolVersion, T> packet, ProtocolVersion from) {
    return this.prepare(packet, from, ProtocolVersion.MAXIMUM_VERSION);
  }

  public <T> PreparedPacket prepare(Function<ProtocolVersion, T> packet, ProtocolVersion originalFrom, ProtocolVersion originalTo) {
    ProtocolVersion from = originalFrom.compareTo(this.minVersion) > 0 ? originalFrom : this.minVersion;
    ProtocolVersion to = originalTo.compareTo(this.maxVersion) < 0 ? originalTo : this.maxVersion;
    if (from.compareTo(to) > 0) {
      return this;
    }
    for (ProtocolVersion protocolVersion : EnumSet.range(from, to)) {
      T minecraftPacket = packet.apply(protocolVersion);
      Preconditions.checkArgument(minecraftPacket instanceof MinecraftPacket);
      ByteBuf buf = this.factory.encodeSingle((MinecraftPacket) minecraftPacket, protocolVersion);
      int versionKey = protocolVersion.ordinal();
      if (this.packets[versionKey] == null) {
        this.packets[versionKey] = Unpooled.directBuffer();
      }

      this.packets[versionKey].writeBytes(buf);
      buf.release();
    }

    return this;
  }

  public ByteBuf getPackets(ProtocolVersion version) {
    return this.packets[version.ordinal()];
  }

  public PreparedPacket build() {
    ByteBuf[] packets = this.packets;
    for (int i = 0, packetsLength = packets.length; i < packetsLength; i++) {
      ByteBuf buf = packets[i];
      if (buf != null) {
        packets[i] = buf.capacity(buf.readableBytes());
      }
    }

    return this;
  }

  public void release() {
    if (this.disposed) {
      return;
    }

    this.disposed = true;
    for (ByteBuf packet : this.packets) {
      if (packet != null) {
        packet.release();
      }
    }
  }
}
