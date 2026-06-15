package dev.leo.ragdollcorpse.corpse;

import dev.leo.sableplayerragdoll.api.PlayerlessRagdollSession;
import dev.leo.sableplayerragdoll.physics.RagdollDeferredSync;
import dev.leo.sableplayerragdoll.physics.RagdollExpireHelper;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

public final class CorpseRagdollSessions {
   private static final int MAINTENANCE_INTERVAL_TICKS = 20;
   private static final ConcurrentHashMap<UUID, PlayerlessRagdollSession> SESSIONS = new ConcurrentHashMap<>();
   private static final Set<UUID> SMOKE_EMITTED = ConcurrentHashMap.newKeySet();

   private CorpseRagdollSessions() {
   }

   static void register(PlayerlessRagdollSession session) {
      SESSIONS.put(session.id(), session);
   }

   public static void onLevelTick(LevelTickEvent.Post event) {
      if (!(event.getLevel() instanceof ServerLevel level)) return;
      if (level.getGameTime() % MAINTENANCE_INTERVAL_TICKS != 0L) return;
      CorpseSavedData.get(level).tickLoadedCorpses(level);
   }

   public static void reset() {
      SESSIONS.clear();
      SMOKE_EMITTED.clear();
   }

   static boolean tryRelease(ServerLevel level, UUID headId, List<UUID> partIds) {
      if (SMOKE_EMITTED.add(headId)) {
         emitSmoke(level, headId);
      }
      boolean gone = releaseNow(headId, partIds, level);
      if (gone) {
         SMOKE_EMITTED.remove(headId);
      }
      return gone;
   }

   static boolean hasKnownBody(ServerLevel level, UUID headId, List<UUID> partIds) {
      if (SESSIONS.containsKey(headId)) return true;

      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (container.getSubLevel(headId) instanceof ServerSubLevel head && !head.isRemoved()) return true;
      for (UUID partId : partIds) {
         if (container.getSubLevel(partId) instanceof ServerSubLevel part && !part.isRemoved()) return true;
      }
      return false;
   }

   static void discardWithoutEffects(ServerLevel level, UUID headId, List<UUID> partIds) {
      releaseNow(headId, partIds, level);
      SMOKE_EMITTED.remove(headId);
   }

   private static boolean releaseNow(UUID headId, List<UUID> partIds, ServerLevel level) {
      PlayerlessRagdollSession session = SESSIONS.remove(headId);
      if (session != null) {
         session.release();
         return allGone(level, headId, partIds);
      }
      return releaseStoredRagdoll(level, headId, partIds);
   }

   private static boolean releaseStoredRagdoll(ServerLevel level, UUID headId, List<UUID> partIds) {
      SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(headId);
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return false;

      boolean attemptedRemoval = false;
      if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
         RagdollRegistry.tryRestoreOnLoad(level, serverSubLevel);
         RagdollExpireHelper.expire(physicsSystem, level, serverSubLevel, "corpse looted after reload");
         attemptedRemoval = true;
      }

      SubLevelContainer container = SubLevelContainer.getContainer(level);
      for (UUID partId : partIds) {
         if (partId.equals(headId)) continue;
         SubLevel partSubLevel = container.getSubLevel(partId);
         if (partSubLevel instanceof ServerSubLevel serverPart && !serverPart.isRemoved()) {
            RagdollDeferredSync.queueRemoval(partId, level);
            attemptedRemoval = true;
         }
      }
      return attemptedRemoval && allGone(level, headId, partIds);
   }

   private static boolean allGone(ServerLevel level, UUID headId, List<UUID> partIds) {
      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (container.getSubLevel(headId) instanceof ServerSubLevel head && !head.isRemoved()) return false;
      for (UUID partId : partIds) {
         if (container.getSubLevel(partId) instanceof ServerSubLevel part && !part.isRemoved()) return false;
      }
      return true;
   }

   private static void emitSmoke(ServerLevel level, UUID headId) {
      Vec3 position = smokePosition(level, headId);
      if (position != null) {
         level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, position.x, position.y, position.z, 20, 0.45, 0.25, 0.45, 0.04);
         level.playSound(null, position.x, position.y, position.z, SoundEvents.BREEZE_JUMP, SoundSource.BLOCKS, 0.8F, 1.15F);
      }
   }

   @Nullable
   private static Vec3 smokePosition(ServerLevel level, UUID headId) {
      SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(headId);
      if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) return null;

      if (serverSubLevel.getPlot() == null) {
         org.joml.Vector3dc position = serverSubLevel.logicalPose().position();
         return new Vec3(position.x(), position.y(), position.z());
      }
      return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(serverSubLevel.getPlot().getCenterBlock()));
   }
}
