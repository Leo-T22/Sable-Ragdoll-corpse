package dev.leo.ragdollcorpse.corpse;

import dev.leo.sableplayerragdoll.api.RagdollInteractEvent;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

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
         lootAll(player.getInventory(), container);
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

   private static void lootAll(Inventory inv, SimpleContainer container) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack stack = container.getItem(i);
         if (stack.isEmpty()) continue;
         ItemStack copy = stack.copy();
         inv.add(copy);
         container.setItem(i, copy.isEmpty() ? ItemStack.EMPTY : copy);
      }
   }

   private static boolean isEmpty(SimpleContainer container) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         if (!container.getItem(i).isEmpty()) return false;
      }
      return true;
   }
}
