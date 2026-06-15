package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.RagdollCorpse;
import dev.leo.ragdollcorpse.client.CorpseCompassClient;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CorpseTrackPacket(Optional<GlobalPos> target) implements CustomPacketPayload {
   public static final Type<CorpseTrackPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath(RagdollCorpse.MOD_ID, "corpse_track")
   );

   public static final StreamCodec<ByteBuf, CorpseTrackPacket> STREAM_CODEC = StreamCodec.of(
      (buf, packet) -> {
         buf.writeBoolean(packet.target().isPresent());
         packet.target().ifPresent(pos -> GlobalPos.STREAM_CODEC.encode(buf, pos));
      },
      buf -> new CorpseTrackPacket(buf.readBoolean() ? Optional.of(GlobalPos.STREAM_CODEC.decode(buf)) : Optional.empty())
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(CorpseTrackPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> CorpseCompassClient.set(packet.target()));
   }
}
