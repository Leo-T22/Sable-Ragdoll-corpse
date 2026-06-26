package dev.leo.ragdollcorpse.corpse;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;

final class CorpseAccessoriesCompat {
   private CorpseAccessoriesCompat() {
   }

   static void moveToSlot(Player player, ItemStack stack, String slotId, int index, boolean cosmetic) {
      if (slotId.isBlank() || index < 0 || stack.isEmpty()) return;

      AccessoriesCapability capability = AccessoriesCapability.get(player);
      if (capability == null) return;

      AccessoriesContainer container = capability.getContainers().get(slotId);
      if (container == null || index >= container.getSize()) return;

      Container targetContainer = cosmetic ? container.getCosmeticAccessories() : container.getAccessories();
      ItemStack target = targetContainer.getItem(index);
      if (target.isEmpty()) {
         targetContainer.setItem(index, stack.copy());
         stack.setCount(0);
         container.markChanged();
         container.update();
         return;
      }

      if (!ItemStack.isSameItemSameComponents(target, stack) || target.getCount() >= target.getMaxStackSize()) return;

      int moved = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
      target.grow(moved);
      stack.shrink(moved);
      targetContainer.setItem(index, target);
      container.markChanged();
      container.update();
   }
}
