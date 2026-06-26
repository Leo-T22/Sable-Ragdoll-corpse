package dev.leo.ragdollcorpse.corpse;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosCapability;

final class CorpseCuriosCompat {
   private CorpseCuriosCompat() {
   }

   static void moveToSlot(Player player, ItemStack stack, String slotId, int index, boolean cosmetic) {
      if (slotId.isBlank() || index < 0 || stack.isEmpty()) return;

      var handler = player.getCapability(CuriosCapability.INVENTORY);
      if (handler == null) return;

      handler.getStacksHandler(slotId).ifPresent(stacksHandler -> {
         var targetHandler = cosmetic ? stacksHandler.getCosmeticStacks() : stacksHandler.getStacks();
         if (index >= targetHandler.getSlots()) return;

         ItemStack target = targetHandler.getStackInSlot(index);
         if (target.isEmpty()) {
            targetHandler.setStackInSlot(index, stack.copy());
            stack.setCount(0);
            stacksHandler.update();
            return;
         }

         if (!ItemStack.isSameItemSameComponents(target, stack) || target.getCount() >= target.getMaxStackSize()) return;

         int moved = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
         target.grow(moved);
         stack.shrink(moved);
         targetHandler.setStackInSlot(index, target);
         stacksHandler.update();
      });
   }
}
