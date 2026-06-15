package dev.leo.ragdollcorpse.corpse;

import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.leo.sableplayerragdoll.api.PlayerlessRagdollSession;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentScope;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

public final class CorpseDeathHandler {
   private static final ConcurrentHashMap<UUID, UUID> PENDING_CORPSE = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<UUID, RagdollEquipmentSnapshot> PENDING_EQUIPMENT = new ConcurrentHashMap<>();

   private CorpseDeathHandler() {
   }

   public static void onPlayerDeath(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;

      PlayerlessRagdollSession session;
      if (RagdollAPI.isRagdolled(player)) {
         session = RagdollAPI.detachActive(player, PlayerlessDespawnRule.never());
      } else {
         session = RagdollAPI.spawnPlayerless(
            player.serverLevel(),
            player.position(),
            player.getYRot(),
            player.getGameProfile(),
            Vec3.ZERO,
            PlayerlessDespawnRule.never()
         );
      }

      if (session != null) {
         UUID headId = session.id();
         CorpseRagdollSessions.register(session);
         PENDING_CORPSE.put(player.getUUID(), headId);
         UUID torsoId = RagdollAPI.torsoSubLevelId(headId);
         if (torsoId != null) {
            RagdollAPI.setGrabDisabled(player.serverLevel(), torsoId, true);
         }
         RagdollEquipmentSnapshot snapshot = RagdollAPI.captureEquipment(player, RagdollEquipmentScope.VANILLA);
         PENDING_EQUIPMENT.put(player.getUUID(), snapshot);
         RagdollAPI.applyEquipmentSnapshot(player.serverLevel(), headId, snapshot);
      }
   }

   @Nullable
   public static UUID takePendingCorpse(UUID playerId) {
      return PENDING_CORPSE.remove(playerId);
   }

   public static void mergePendingEquipment(UUID playerId, RagdollEquipmentSnapshot snapshot) {
      PENDING_EQUIPMENT.merge(playerId, snapshot, RagdollEquipmentSnapshot::merge);
   }

   public static RagdollEquipmentSnapshot takePendingEquipment(UUID playerId) {
      RagdollEquipmentSnapshot snapshot = PENDING_EQUIPMENT.remove(playerId);
      return snapshot == null ? RagdollEquipmentSnapshot.empty() : snapshot;
   }


   @Nullable
   public static UUID peekPendingCorpse(UUID playerId) {
      return PENDING_CORPSE.get(playerId);
   }
}
