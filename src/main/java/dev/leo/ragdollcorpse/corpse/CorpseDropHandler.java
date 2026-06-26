package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.RagdollCorpseConfig;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentScope;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
      List<CorpseSavedData.RestoreTarget> restoreTargets = new ArrayList<>();
      List<SlotStackSnapshot> optionalSlotSnapshots = captureOptionalSlotSnapshots(snapshot);
      List<ItemEntity> overflow = new ArrayList<>();

      for (ItemEntity entity : event.getDrops()) {
         ItemStack stack = entity.getItem();
         if (!stack.isEmpty() && toStore.size() < CorpseSavedData.SLOTS) {
            toStore.add(stack.copy());
            restoreTargets.add(takeRestoreTarget(player.getUUID(), optionalSlotSnapshots, stack));
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
         .store(headId, toStore, restoreTargets, player.getGameProfile().getName(), player.getUUID(), player.blockPosition(), snapshot);

      event.getDrops().clear();
      event.getDrops().addAll(overflow);
   }

   private static CorpseSavedData.RestoreTarget takeRestoreTarget(UUID playerId, List<SlotStackSnapshot> optionalSlotSnapshots, ItemStack stack) {
      CorpseSavedData.RestoreTarget vanillaTarget = CorpseDeathHandler.takeOriginalSlot(playerId, stack);
      if (vanillaTarget.kind() != CorpseSavedData.RestoreKind.NONE) return vanillaTarget;

      for (int i = 0; i < optionalSlotSnapshots.size(); i++) {
         SlotStackSnapshot snapshot = optionalSlotSnapshots.get(i);
         if (snapshot.matches(stack)) {
            optionalSlotSnapshots.remove(i);
            return snapshot.target();
         }
      }
      return CorpseSavedData.RestoreTarget.none();
   }

   private static List<SlotStackSnapshot> captureOptionalSlotSnapshots(RagdollEquipmentSnapshot snapshot) {
      List<SlotStackSnapshot> snapshots = new ArrayList<>();
      addSlotStacks(snapshots, CorpseSavedData.RestoreKind.CURIOS, snapshot.curioItems(), false);
      addSlotStacks(snapshots, CorpseSavedData.RestoreKind.CURIOS, snapshot.curioCosmeticItems(), true);
      addSlotStacks(snapshots, CorpseSavedData.RestoreKind.ACCESSORIES, snapshot.accessoriesItems(), false);
      addSlotStacks(snapshots, CorpseSavedData.RestoreKind.ACCESSORIES, snapshot.accessoriesCosmeticItems(), true);
      return snapshots;
   }

   private static void addSlotStacks(List<SlotStackSnapshot> snapshots, CorpseSavedData.RestoreKind kind, Map<String, List<ItemStack>> slotMap) {
      addSlotStacks(snapshots, kind, slotMap, false);
   }

   private static void addSlotStacks(List<SlotStackSnapshot> snapshots, CorpseSavedData.RestoreKind kind, Map<String, List<ItemStack>> slotMap, boolean cosmetic) {
      slotMap.forEach((slotId, stacks) -> {
         for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (!stack.isEmpty()) snapshots.add(new SlotStackSnapshot(new CorpseSavedData.RestoreTarget(kind, slotId, index, cosmetic), stack.copy()));
         }
      });
   }

   private record SlotStackSnapshot(CorpseSavedData.RestoreTarget target, ItemStack stack) {
      boolean matches(ItemStack other) {
         return ItemStack.isSameItemSameComponents(stack, other);
      }
   }
}
