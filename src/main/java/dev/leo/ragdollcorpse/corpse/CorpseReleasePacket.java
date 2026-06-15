package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.RagdollCorpse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CorpseReleasePacket() implements CustomPacketPayload {
   public static final Type<CorpseReleasePacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath(RagdollCorpse.MOD_ID, "corpse_release")
   );

   public static final StreamCodec<ByteBuf, CorpseReleasePacket> STREAM_CODEC = StreamCodec.unit(new CorpseReleasePacket());

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(CorpseReleasePacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof CorpseMenu menu) {
            CorpseSavedData.get(player.serverLevel()).markForRelease(menu.getHeadId());
            player.closeContainer();
         }
      });
   }
}
