package dev.leo.ragdollcorpse.corpse;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CorpseMenu extends AbstractContainerMenu {
   private static final int CORPSE_ROWS = 6;
   private static final int COLS = 9;
   private static final int CORPSE_SLOTS = CORPSE_ROWS * COLS;

   private final SimpleContainer corpseContainer;
   private final UUID headId;
   private final ServerLevel level;

   public CorpseMenu(int id, Inventory playerInv, SimpleContainer corpseContainer, UUID headId, ServerLevel level) {
      super(MenuType.GENERIC_9x6, id);
      this.corpseContainer = corpseContainer;
      this.headId = headId;
      this.level = level;

      for (int row = 0; row < CORPSE_ROWS; row++) {
         for (int col = 0; col < COLS; col++) {
            int slotIndex = col + row * COLS;
            addSlot(new Slot(corpseContainer, slotIndex, 8 + col * 18, 18 + row * 18) {
               @Override
               public boolean mayPlace(ItemStack stack) {
                  return false;
               }
            });
         }
      }

      int i = (CORPSE_ROWS - 4) * 18;
      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < COLS; col++) {
            addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 103 + i + row * 18));
         }
      }

      for (int col = 0; col < COLS; col++) {
         addSlot(new Slot(playerInv, col, 8 + col * 18, 161 + i));
      }
   }

   @Override
   public ItemStack quickMoveStack(Player player, int slotIndex) {
      Slot slot = slots.get(slotIndex);
      if (!slot.hasItem()) return ItemStack.EMPTY;
      ItemStack stack = slot.getItem();
      ItemStack original = stack.copy();

      if (slotIndex < CORPSE_SLOTS) {
         if (!moveItemStackTo(stack, CORPSE_SLOTS + 27, CORPSE_SLOTS + 36, false)
               && !moveItemStackTo(stack, CORPSE_SLOTS, CORPSE_SLOTS + 27, false)) {
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }

      if (stack.isEmpty()) {
         slot.set(ItemStack.EMPTY);
      } else {
         slot.setChanged();
      }

      if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
      slot.onTake(player, stack);
      return original;
   }

    public UUID getHeadId() {
       return headId;
    }

    @Override
    public boolean stillValid(Player player) {
       return true;
    }

   @Override
   public void removed(Player player) {
      super.removed(player);
      if (isCorpseEmpty()) {
         CorpseSavedData.get(level).markForRelease(headId);
      }
   }

   private boolean isCorpseEmpty() {
      for (int i = 0; i < corpseContainer.getContainerSize(); i++) {
         if (!corpseContainer.getItem(i).isEmpty()) return false;
      }
      return true;
   }
}
