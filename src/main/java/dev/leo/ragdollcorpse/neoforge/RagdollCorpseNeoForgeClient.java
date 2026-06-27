package dev.leo.ragdollcorpse.neoforge;

import dev.leo.ragdollcorpse.RagdollCorpse;
import dev.leo.ragdollcorpse.RagdollCorpseConfig;
import dev.leo.ragdollcorpse.client.CorpseCompassClient;
import dev.leo.ragdollcorpse.corpse.CorpseReleasePacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = RagdollCorpse.MOD_ID, dist = {Dist.CLIENT})
public final class RagdollCorpseNeoForgeClient {
   public RagdollCorpseNeoForgeClient(ModContainer container, IEventBus modBus) {
      container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (modContainer, parent) -> new ConfigurationScreen(modContainer, parent));
      modBus.addListener(RagdollCorpseNeoForgeClient::onClientSetup);
      NeoForge.EVENT_BUS.addListener(RagdollCorpseNeoForgeClient::onScreenInit);
   }

   private static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(CorpseCompassClient::register);
   }

   private static void onScreenInit(ScreenEvent.Init.Post event) {
      if (!(event.getScreen() instanceof ContainerScreen screen)) return;
      if (!(screen.getTitle().getContents() instanceof TranslatableContents contents)) return;
      if (!"container.ragdoll_corpse.corpse".equals(contents.getKey())) return;
      if (!RagdollCorpseConfig.enableDespawnButton()) return;

      Button releaseButton = Button.builder(
            Component.translatable("container.ragdoll_corpse.release"),
            btn -> PacketDistributor.sendToServer(new CorpseReleasePacket()))
         .pos(screen.getGuiLeft() + 176 - 54, screen.getGuiTop() + 4)
         .size(50, 14)
         .build();
      event.addListener(releaseButton);
   }
}
