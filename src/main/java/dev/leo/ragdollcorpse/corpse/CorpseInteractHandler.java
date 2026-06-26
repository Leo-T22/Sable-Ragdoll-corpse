package dev.leo.ragdollcorpse.corpse;

import dev.leo.sableplayerragdoll.api.RagdollInteractEvent;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class CorpseInteractHandler {
   private CorpseInteractHandler() {
   }

   public static void onRagdollInteract(RagdollInteractEvent event) {
      UUID rootId = event.rootId();
      if (!rootId.equals(event.partId())) return;

      CorpseSavedData data = CorpseSavedData.get(event.level());
      SimpleContainer container = data.getContainer(rootId);
      if (container == null) return;

      ServerPlayer player = event.player();

      if (player.isShiftKeyDown()) {
         lootAll(player.getInventory(), data, rootId, container);
         if (isEmpty(container)) data.markForRelease(rootId);
         event.setCanceled(true);
         return;
      }

      if (isEmpty(container)) {
         data.releaseEmptyCorpseNow(rootId, event.level());
         event.setCanceled(true);
         return;
      }

      String ownerName = data.getOwnerName(rootId);
      player.openMenu(new SimpleMenuProvider(
         (id, playerInv, p) -> new CorpseMenu(id, playerInv, container, rootId, event.level()),
         Component.translatable("container.ragdoll_corpse.corpse", ownerName)
      ));
      event.setCanceled(true);
   }

   private static void lootAll(Inventory inv, CorpseSavedData data, UUID headId, SimpleContainer container) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack stack = container.getItem(i);
         if (stack.isEmpty()) continue;
         ItemStack copy = stack.copy();
         moveToRestoreTarget(inv, copy, data.getRestoreTarget(headId, i));
         if (!copy.isEmpty()) inv.add(copy);
         container.setItem(i, copy.isEmpty() ? ItemStack.EMPTY : copy);
      }
      inv.setChanged();
   }

   private static void moveToRestoreTarget(Inventory inv, ItemStack stack, CorpseSavedData.RestoreTarget target) {
      if (target.kind() == CorpseSavedData.RestoreKind.PLAYER_INVENTORY) {
         moveToPlayerInventorySlot(inv, stack, target.index());
      } else if (target.kind() == CorpseSavedData.RestoreKind.CURIOS && ModList.get().isLoaded("curios")) {
         CorpseCuriosCompat.moveToSlot(inv.player, stack, target.slotId(), target.index(), target.cosmetic());
      } else if (target.kind() == CorpseSavedData.RestoreKind.ACCESSORIES && ModList.get().isLoaded("accessories")) {
         CorpseAccessoriesCompat.moveToSlot(inv.player, stack, target.slotId(), target.index(), target.cosmetic());
      }
   }

   private static void moveToPlayerInventorySlot(Inventory inv, ItemStack stack, int slot) {
      if (slot < 0 || slot >= inv.getContainerSize() || stack.isEmpty()) return;

      ItemStack target = inv.getItem(slot);
      if (target.isEmpty()) {
         inv.setItem(slot, stack.copy());
         stack.setCount(0);
         return;
      }

      if (!ItemStack.isSameItemSameComponents(target, stack) || target.getCount() >= target.getMaxStackSize()) return;

      int moved = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
      target.grow(moved);
      stack.shrink(moved);
   }

   private static boolean isEmpty(SimpleContainer container) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         if (!container.getItem(i).isEmpty()) return false;
      }
      return true;
   }
}
