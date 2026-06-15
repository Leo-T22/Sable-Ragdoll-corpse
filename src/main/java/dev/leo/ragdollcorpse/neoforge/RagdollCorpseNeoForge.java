package dev.leo.ragdollcorpse.neoforge;

import dev.leo.ragdollcorpse.RagdollCorpse;
import dev.leo.ragdollcorpse.RagdollCorpseConfig;
import dev.leo.ragdollcorpse.corpse.CorpseCommands;
import dev.leo.ragdollcorpse.corpse.CorpseCompassTracker;
import dev.leo.ragdollcorpse.corpse.CorpseDeathHandler;
import dev.leo.ragdollcorpse.corpse.CorpseDropHandler;
import dev.leo.ragdollcorpse.corpse.CorpseInteractHandler;
import dev.leo.ragdollcorpse.corpse.CorpseNetworking;
import dev.leo.ragdollcorpse.corpse.CorpseRagdollSessions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(RagdollCorpse.MOD_ID)
public final class RagdollCorpseNeoForge {
   public RagdollCorpseNeoForge(IEventBus modBus, ModContainer container) {
      container.registerConfig(ModConfig.Type.COMMON, RagdollCorpseConfig.SPEC);
      modBus.addListener(CorpseNetworking::register);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CorpseDeathHandler::onPlayerDeath);
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CorpseDropHandler::onLivingDropsHighCapture);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CorpseDropHandler::onLivingDrops);
      NeoForge.EVENT_BUS.addListener(CorpseInteractHandler::onRagdollInteract);
      NeoForge.EVENT_BUS.addListener(CorpseRagdollSessions::onLevelTick);
      NeoForge.EVENT_BUS.addListener(CorpseCompassTracker::onServerTick);
      NeoForge.EVENT_BUS.addListener(CorpseCompassTracker::onPlayerLogin);
      NeoForge.EVENT_BUS.addListener(CorpseCompassTracker::onPlayerLogout);
      NeoForge.EVENT_BUS.addListener(CorpseCommands::register);
      NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
         CorpseRagdollSessions.reset();
         CorpseCompassTracker.reset();
      });
   }
}
