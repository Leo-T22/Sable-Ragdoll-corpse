package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.RagdollCorpse;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CorpseNetworking {
   private CorpseNetworking() {
   }

    public static void register(RegisterPayloadHandlersEvent event) {
       PayloadRegistrar registrar = event.registrar(RagdollCorpse.MOD_ID);
       registrar.playToClient(CorpseTrackPacket.TYPE, CorpseTrackPacket.STREAM_CODEC, CorpseTrackPacket::handle);
       registrar.playToServer(CorpseReleasePacket.TYPE, CorpseReleasePacket.STREAM_CODEC, CorpseReleasePacket::handle);
    }

   public static void sendCorpseTarget(ServerPlayer player, Optional<GlobalPos> target) {
      PacketDistributor.sendToPlayer(player, new CorpseTrackPacket(target));
   }
}
