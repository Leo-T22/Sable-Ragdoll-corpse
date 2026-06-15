package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.corpse.CorpseSavedData.CorpseTarget;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

public final class CorpseCompassTracker {
   private static final int SYNC_INTERVAL_TICKS = 20;
   private static final Map<UUID, GlobalPos> LAST_SENT = new ConcurrentHashMap<>();

   private CorpseCompassTracker() {
   }

   public static void onServerTick(ServerTickEvent.Post event) {
      MinecraftServer server = event.getServer();
      if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
         syncAll(server);
      }
   }

   public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         LAST_SENT.remove(player.getUUID());
         syncAll(player.getServer());
      }
   }

   public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      LAST_SENT.remove(event.getEntity().getUUID());
   }

   public static void reset() {
      LAST_SENT.clear();
   }

   private static void syncAll(@Nullable MinecraftServer server) {
      if (server == null) return;
      Map<UUID, CorpseTarget> best = new HashMap<>();
      for (ServerLevel level : server.getAllLevels()) {
         CorpseSavedData.get(level).collectLatestCorpses(best);
      }
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         CorpseTarget candidate = best.get(player.getUUID());
         GlobalPos target = candidate == null ? null : candidate.pos();
         if (Objects.equals(LAST_SENT.get(player.getUUID()), target)) continue;
         if (target == null) {
            LAST_SENT.remove(player.getUUID());
         } else {
            LAST_SENT.put(player.getUUID(), target);
         }
         CorpseNetworking.sendCorpseTarget(player, Optional.ofNullable(target));
      }
   }
}
