package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.RagdollCorpseConfig;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentScope;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class CorpseDropHandler {
   private CorpseDropHandler() {
   }

   public static void onLivingDropsHighCapture(LivingDropsEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      UUID headId = CorpseDeathHandler.peekPendingCorpse(player.getUUID());
      if (headId == null) return;
      RagdollEquipmentSnapshot snapshot = RagdollAPI.captureEquipment(player, RagdollEquipmentScope.OPTIONAL_MODS);
      CorpseDeathHandler.mergePendingEquipment(player.getUUID(), snapshot);
      RagdollAPI.applyEquipmentSnapshot(player.serverLevel(), headId, snapshot);
   }

   public static void onLivingDrops(LivingDropsEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      UUID headId = CorpseDeathHandler.takePendingCorpse(player.getUUID());
      if (headId == null) return;
      RagdollEquipmentSnapshot snapshot = CorpseDeathHandler.takePendingEquipment(player.getUUID());

      List<ItemStack> toStore = new ArrayList<>();
      List<ItemEntity> overflow = new ArrayList<>();

      for (ItemEntity entity : event.getDrops()) {
         ItemStack stack = entity.getItem();
         if (!stack.isEmpty() && toStore.size() < CorpseSavedData.SLOTS) {
            toStore.add(stack.copy());
         } else {
            overflow.add(entity);
         }
      }

      if (toStore.isEmpty() && !RagdollCorpseConfig.spawnEmptyInventoryCorpses()) {
         CorpseRagdollSessions.discardWithoutEffects(player.serverLevel(), headId, List.copyOf(RagdollAssemblyHelper.linkedParts(headId)));
         event.getDrops().clear();
         event.getDrops().addAll(overflow);
         return;
      }

      CorpseSavedData.get(player.serverLevel())
         .store(headId, toStore, player.getGameProfile().getName(), player.getUUID(), player.blockPosition(), snapshot);

      event.getDrops().clear();
      event.getDrops().addAll(overflow);
   }
}
