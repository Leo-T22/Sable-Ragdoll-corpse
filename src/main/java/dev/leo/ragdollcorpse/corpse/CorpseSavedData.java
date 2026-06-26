package dev.leo.ragdollcorpse.corpse;

import dev.leo.ragdollcorpse.RagdollCorpseConfig;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class CorpseSavedData extends SavedData {
   public static final int SLOTS = 54;
   private static final String FILE_ID = "ragdoll_corpse";
   private static final Factory<CorpseSavedData> FACTORY = new Factory<>(CorpseSavedData::new, CorpseSavedData::load);

   private final Map<UUID, SimpleContainer> containers = new HashMap<>();
   private final Map<UUID, RestoreTarget[]> restoreTargets = new HashMap<>();
   private final Map<UUID, String> ownerNames = new HashMap<>();
   private final Map<UUID, RagdollEquipmentSnapshot> equipmentSnapshots = new HashMap<>();
   private final Map<UUID, Long> createdAtTicks = new HashMap<>();
   private final Map<UUID, List<UUID>> ragdollPartIds = new HashMap<>();
   private final Map<UUID, Long> releaseAtTicks = new HashMap<>();
   private final Map<UUID, UUID> ownerIds = new HashMap<>();
   private final Map<UUID, BlockPos> lastKnownPos = new HashMap<>();
   @Nullable
   private ServerLevel levelRef;

   public static CorpseSavedData get(ServerLevel level) {
      CorpseSavedData data = level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
      data.levelRef = level;
      return data;
   }

   public static CorpseSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      CorpseSavedData data = new CorpseSavedData();
      ListTag corpseList = tag.getList("Corpses", Tag.TAG_COMPOUND);
      for (int i = 0; i < corpseList.size(); i++) {
         CompoundTag corpseTag = corpseList.getCompound(i);
         if (!corpseTag.hasUUID("HeadId")) continue;
         UUID headId = corpseTag.getUUID("HeadId");
         if (corpseTag.contains("OwnerName")) {
            data.ownerNames.put(headId, corpseTag.getString("OwnerName"));
         }
         if (corpseTag.contains("EquipmentSnapshot", Tag.TAG_COMPOUND)) {
            data.equipmentSnapshots.put(headId, loadSnapshot(corpseTag.getCompound("EquipmentSnapshot"), registries));
         }
         data.createdAtTicks.put(headId, corpseTag.getLong("CreatedAtTick"));
         data.ragdollPartIds.put(headId, loadPartIds(corpseTag, headId));
         if (corpseTag.contains("ReleaseAtTick")) {
            data.releaseAtTicks.put(headId, corpseTag.getLong("ReleaseAtTick"));
         }
         if (corpseTag.hasUUID("OwnerId")) {
            data.ownerIds.put(headId, corpseTag.getUUID("OwnerId"));
         }
         if (corpseTag.contains("PosX")) {
            data.lastKnownPos.put(headId, new BlockPos(corpseTag.getInt("PosX"), corpseTag.getInt("PosY"), corpseTag.getInt("PosZ")));
         }
         SimpleContainer container = new SimpleContainer(SLOTS);
         RestoreTarget[] targets = emptyRestoreTargets();
         ListTag itemList = corpseTag.getList("Items", Tag.TAG_COMPOUND);
         for (int j = 0; j < itemList.size(); j++) {
            CompoundTag itemTag = itemList.getCompound(j);
            int slot = itemTag.getInt("Slot");
            if (slot >= 0 && slot < SLOTS && itemTag.contains("Item")) {
               ItemStack.parse(registries, itemTag.get("Item")).ifPresent(stack -> container.setItem(slot, stack));
               targets[slot] = loadRestoreTarget(itemTag);
            }
         }
         data.attachListeners(container, headId);
         data.containers.put(headId, container);
         data.restoreTargets.put(headId, targets);
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      ListTag corpseList = new ListTag();
      containers.forEach((headId, container) -> {
         CompoundTag corpseTag = new CompoundTag();
         corpseTag.putUUID("HeadId", headId);
         String ownerName = ownerNames.get(headId);
         if (ownerName != null) corpseTag.putString("OwnerName", ownerName);
         RagdollEquipmentSnapshot snapshot = equipmentSnapshots.get(headId);
         if (snapshot != null) corpseTag.put("EquipmentSnapshot", saveSnapshot(snapshot, registries));
         corpseTag.putLong("CreatedAtTick", createdAtTicks.getOrDefault(headId, 0L));
         Long releaseAt = releaseAtTicks.get(headId);
         if (releaseAt != null) corpseTag.putLong("ReleaseAtTick", releaseAt);
         UUID owner = ownerIds.get(headId);
         if (owner != null) corpseTag.putUUID("OwnerId", owner);
         BlockPos pos = lastKnownPos.get(headId);
         if (pos != null) {
            corpseTag.putInt("PosX", pos.getX());
            corpseTag.putInt("PosY", pos.getY());
            corpseTag.putInt("PosZ", pos.getZ());
         }
         savePartIds(corpseTag, ragdollPartIds.getOrDefault(headId, List.of(headId)));
         ListTag itemList = new ListTag();
         for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
               CompoundTag itemTag = new CompoundTag();
               itemTag.putInt("Slot", slot);
               saveRestoreTarget(itemTag, getRestoreTarget(headId, slot));
               itemTag.put("Item", stack.save(registries));
               itemList.add(itemTag);
            }
         }
         corpseTag.put("Items", itemList);
         corpseList.add(corpseTag);
      });
      tag.put("Corpses", corpseList);
      return tag;
   }

   public void store(UUID headId, List<ItemStack> items, List<RestoreTarget> itemRestoreTargets, String ownerName, UUID ownerId, BlockPos deathPos, RagdollEquipmentSnapshot snapshot) {
      SimpleContainer container = new SimpleContainer(SLOTS);
      RestoreTarget[] targets = emptyRestoreTargets();
      for (int i = 0; i < Math.min(items.size(), SLOTS); i++) {
         container.setItem(i, items.get(i).copy());
         if (i < itemRestoreTargets.size()) targets[i] = itemRestoreTargets.get(i);
      }
      attachListeners(container, headId);
      containers.put(headId, container);
      restoreTargets.put(headId, targets);
      ownerNames.put(headId, ownerName);
      ownerIds.put(headId, ownerId);
      lastKnownPos.put(headId, deathPos);
      equipmentSnapshots.put(headId, snapshot == null ? RagdollEquipmentSnapshot.empty() : snapshot);
      createdAtTicks.put(headId, levelRef == null ? 0L : levelRef.getGameTime());
      ragdollPartIds.put(headId, List.copyOf(RagdollAssemblyHelper.linkedParts(headId)));
      syncVisualsToAvailableItems(headId, container);
      setDirty();
   }

   public void tickLoadedCorpses(ServerLevel level) {
      var subLevelContainer = SubLevelContainer.getContainer(level);
      long gameTime = level.getGameTime();
      long corpseLifetime = RagdollCorpseConfig.corpseLifetimeTicks();
      List<UUID> toPurge = null;
      for (UUID headId : containers.keySet()) {
         Long releaseAt = releaseAtTicks.get(headId);
         if (releaseAt != null && gameTime >= releaseAt) {
            List<UUID> partIds = ragdollPartIds.getOrDefault(headId, List.of(headId));
            if (!CorpseRagdollSessions.hasKnownBody(level, headId, partIds) || CorpseRagdollSessions.tryRelease(level, headId, partIds)) {
               if (toPurge == null) toPurge = new ArrayList<>();
               toPurge.add(headId);
            }
            continue;
         }

         if (corpseLifetime > 0L && gameTime - createdAtTicks.getOrDefault(headId, gameTime) >= corpseLifetime) {
            List<UUID> partIds = ragdollPartIds.getOrDefault(headId, List.of(headId));
            if (!CorpseRagdollSessions.hasKnownBody(level, headId, partIds) || CorpseRagdollSessions.tryRelease(level, headId, partIds)) {
               if (toPurge == null) toPurge = new ArrayList<>();
               toPurge.add(headId);
            } else {
               releaseAtTicks.put(headId, gameTime);
               setDirty();
            }
            continue;
         }

         SubLevel subLevel = subLevelContainer.getSubLevel(headId);
         if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            RagdollRegistry.tryRestoreOnLoad(level, serverSubLevel);
            RagdollAPI.setCorpse(level, headId, true);
            BlockPos pos = corpseWorldPos(level, serverSubLevel);
            if (pos != null && !pos.equals(lastKnownPos.get(headId))) {
               lastKnownPos.put(headId, pos);
               setDirty();
            }
         }
      }
      if (toPurge != null) {
         toPurge.forEach(this::purge);
      }
   }

   public void collectLatestCorpses(Map<UUID, CorpseTarget> best) {
      if (levelRef == null) return;
      for (Map.Entry<UUID, UUID> entry : ownerIds.entrySet()) {
         UUID headId = entry.getKey();
         UUID ownerId = entry.getValue();
         if (releaseAtTicks.containsKey(headId)) continue;
         BlockPos pos = lastKnownPos.get(headId);
         if (pos == null) continue;
         long tick = createdAtTicks.getOrDefault(headId, 0L);
         CorpseTarget current = best.get(ownerId);
         if (current == null || tick >= current.createdTick()) {
            best.put(ownerId, new CorpseTarget(tick, GlobalPos.of(levelRef.dimension(), pos)));
         }
      }
   }

   @Nullable
   private static BlockPos corpseWorldPos(ServerLevel level, ServerSubLevel subLevel) {
      if (subLevel.getPlot() == null) {
         org.joml.Vector3dc p = subLevel.logicalPose().position();
         return BlockPos.containing(p.x(), p.y(), p.z());
      }
      Vec3 world = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(subLevel.getPlot().getCenterBlock()));
      return world == null ? null : BlockPos.containing(world);
   }

   public record CorpseTarget(long createdTick, GlobalPos pos) {
   }

   public record CorpseInfo(UUID headId, String ownerName, @Nullable BlockPos pos, long createdTick, long ageTicks, int itemStacks, boolean releasing, boolean bodyPresent) {
   }

   public void markForRelease(UUID headId) {
      if (containers.containsKey(headId) && releaseAtTicks.putIfAbsent(headId, gameTimeNow() + RagdollCorpseConfig.emptyCorpseDespawnTicks()) == null) {
         setDirty();
      }
   }

   public void releaseEmptyCorpseNow(UUID headId, ServerLevel level) {
      SimpleContainer container = containers.get(headId);
      if (container == null || !isEmpty(container)) return;

      releaseCorpseNow(headId, level);
   }

   public boolean releaseCorpseNow(UUID headId, ServerLevel level) {
      if (!containers.containsKey(headId)) return false;

      List<UUID> partIds = ragdollPartIds.getOrDefault(headId, List.of(headId));
      if (!CorpseRagdollSessions.hasKnownBody(level, headId, partIds)) {
         purge(headId);
         return true;
      }

      if (CorpseRagdollSessions.tryRelease(level, headId, partIds)) {
         purge(headId);
         return true;
      }

      releaseAtTicks.put(headId, level.getGameTime());
      setDirty();
      return false;
   }

   public List<ItemStack> takeAllItems(UUID headId) {
      SimpleContainer container = containers.get(headId);
      if (container == null) return List.of();

      List<ItemStack> items = gatherItems(container);
      container.clearContent();
      setDirty();
      return items;
   }

   public void purgeCorpse(UUID headId) {
      purge(headId);
   }

   public int releaseEmptyCorpsesNow(ServerLevel level) {
      List<UUID> emptyCorpses = new ArrayList<>();
      for (Map.Entry<UUID, SimpleContainer> entry : containers.entrySet()) {
         if (isEmpty(entry.getValue())) emptyCorpses.add(entry.getKey());
      }

      int released = 0;
      for (UUID headId : emptyCorpses) {
         List<UUID> partIds = ragdollPartIds.getOrDefault(headId, List.of(headId));
         if (!CorpseRagdollSessions.hasKnownBody(level, headId, partIds)) {
            purge(headId);
            released++;
            continue;
         }
         if (releaseAtTicks.containsKey(headId)) continue;
         releaseCorpseNow(headId, level);
         released++;
      }
      return released;
   }

   public List<CorpseInfo> listCorpses(ServerLevel level) {
      List<CorpseInfo> result = new ArrayList<>();
      containers.forEach((headId, container) -> {
         long createdTick = createdAtTicks.getOrDefault(headId, 0L);
         List<UUID> partIds = ragdollPartIds.getOrDefault(headId, List.of(headId));
         result.add(new CorpseInfo(
            headId,
            ownerNames.getOrDefault(headId, "Unknown"),
            lastKnownPos.get(headId),
            createdTick,
            Math.max(0L, level.getGameTime() - createdTick),
            countItems(container),
            releaseAtTicks.containsKey(headId),
            CorpseRagdollSessions.hasKnownBody(level, headId, partIds)
         ));
      });
      return result;
   }

   private static int countItems(net.minecraft.world.Container container) {
      int count = 0;
      for (int i = 0; i < container.getContainerSize(); i++) {
         if (!container.getItem(i).isEmpty()) count++;
      }
      return count;
   }

   private long gameTimeNow() {
      return levelRef == null ? 0L : levelRef.getGameTime();
   }

   private void purge(UUID headId) {
      containers.remove(headId);
      restoreTargets.remove(headId);
      ownerNames.remove(headId);
      ownerIds.remove(headId);
      lastKnownPos.remove(headId);
      equipmentSnapshots.remove(headId);
      createdAtTicks.remove(headId);
      ragdollPartIds.remove(headId);
      releaseAtTicks.remove(headId);
      setDirty();
   }

   private void attachListeners(SimpleContainer container, UUID headId) {
      container.addListener(c -> {
         setDirty();
         syncVisualsToAvailableItems(headId, c);
      });
   }

   private void syncVisualsToAvailableItems(UUID headId, net.minecraft.world.Container container) {
      if (levelRef != null) {
         List<ItemStack> current = gatherItems(container);
         RagdollEquipmentSnapshot snapshot = equipmentSnapshots.getOrDefault(headId, RagdollEquipmentSnapshot.empty());
         RagdollEquipmentSnapshot filtered = snapshot.filteredByAvailableItems(current);
         equipmentSnapshots.put(headId, filtered);
         RagdollAPI.applyEquipmentSnapshot(levelRef, headId, filtered);
      }
   }

   private static List<ItemStack> gatherItems(net.minecraft.world.Container container) {
      List<ItemStack> items = new ArrayList<>();
      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack s = container.getItem(i);
         if (!s.isEmpty()) items.add(s.copy());
      }
      return items;
   }

   private static boolean isEmpty(net.minecraft.world.Container container) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         if (!container.getItem(i).isEmpty()) return false;
      }
      return true;
   }

   public String getOwnerName(UUID headId) {
      return ownerNames.getOrDefault(headId, "Unknown");
   }

   @Nullable
   public SimpleContainer getContainer(UUID headId) {
      return containers.get(headId);
   }

   public RestoreTarget getRestoreTarget(UUID headId, int containerSlot) {
      RestoreTarget[] targets = restoreTargets.get(headId);
      if (targets == null || containerSlot < 0 || containerSlot >= targets.length) return RestoreTarget.none();
      RestoreTarget target = targets[containerSlot];
      return target == null ? RestoreTarget.none() : target;
   }

   private static RestoreTarget[] emptyRestoreTargets() {
      RestoreTarget[] targets = new RestoreTarget[SLOTS];
      java.util.Arrays.fill(targets, RestoreTarget.none());
      return targets;
   }

   private static RestoreTarget loadRestoreTarget(CompoundTag itemTag) {
      if (itemTag.contains("RestoreKind")) {
         RestoreKind kind = RestoreKind.bySerializedName(itemTag.getString("RestoreKind"));
         if (kind == RestoreKind.PLAYER_INVENTORY) {
            return RestoreTarget.playerInventory(itemTag.getInt("RestoreIndex"));
         }
         if ((kind == RestoreKind.CURIOS || kind == RestoreKind.ACCESSORIES) && itemTag.contains("RestoreSlotId")) {
            return new RestoreTarget(kind, itemTag.getString("RestoreSlotId"), itemTag.getInt("RestoreIndex"), itemTag.getBoolean("RestoreCosmetic"));
         }
      }
      if (itemTag.contains("OriginalSlot")) return RestoreTarget.playerInventory(itemTag.getInt("OriginalSlot"));
      return RestoreTarget.none();
   }

   private static void saveRestoreTarget(CompoundTag itemTag, RestoreTarget target) {
      if (target.kind() == RestoreKind.NONE) return;

      itemTag.putString("RestoreKind", target.kind().serializedName());
      itemTag.putInt("RestoreIndex", target.index());
      if (!target.slotId().isBlank()) itemTag.putString("RestoreSlotId", target.slotId());
      if (target.cosmetic()) itemTag.putBoolean("RestoreCosmetic", true);
      if (target.kind() == RestoreKind.PLAYER_INVENTORY) itemTag.putInt("OriginalSlot", target.index());
   }

   public enum RestoreKind {
      NONE("none"),
      PLAYER_INVENTORY("player_inventory"),
      CURIOS("curios"),
      ACCESSORIES("accessories");

      private final String serializedName;

      RestoreKind(String serializedName) {
         this.serializedName = serializedName;
      }

      public String serializedName() {
         return serializedName;
      }

      static RestoreKind bySerializedName(String name) {
         for (RestoreKind kind : values()) {
            if (kind.serializedName.equals(name)) return kind;
         }
         return NONE;
      }
   }

   public record RestoreTarget(RestoreKind kind, String slotId, int index, boolean cosmetic) {
      public static RestoreTarget none() {
         return new RestoreTarget(RestoreKind.NONE, "", -1, false);
      }

      public static RestoreTarget playerInventory(int slot) {
         return new RestoreTarget(RestoreKind.PLAYER_INVENTORY, "", slot, false);
      }
   }

   private static void savePartIds(CompoundTag corpseTag, List<UUID> partIds) {
      ListTag list = new ListTag();
      for (UUID partId : partIds) {
         CompoundTag partTag = new CompoundTag();
         partTag.putUUID("Id", partId);
         list.add(partTag);
      }
      corpseTag.put("PartIds", list);
   }

   private static List<UUID> loadPartIds(CompoundTag corpseTag, UUID headId) {
      if (!corpseTag.contains("PartIds", Tag.TAG_LIST)) return List.of(headId);

      ListTag list = corpseTag.getList("PartIds", Tag.TAG_COMPOUND);
      List<UUID> partIds = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
         CompoundTag partTag = list.getCompound(i);
         if (partTag.hasUUID("Id")) partIds.add(partTag.getUUID("Id"));
      }
      return partIds.isEmpty() ? List.of(headId) : List.copyOf(partIds);
   }

   private static CompoundTag saveSnapshot(RagdollEquipmentSnapshot snapshot, net.minecraft.core.HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();

      ListTag vanilla = new ListTag();
      snapshot.vanillaItems().forEach((slot, stack) -> {
         if (!stack.isEmpty()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("Slot", slot.getName());
            itemTag.put("Item", stack.save(registries));
            vanilla.add(itemTag);
         }
      });
      tag.put("Vanilla", vanilla);
      tag.put("Curios", saveSlotMap(snapshot.curioItems(), registries));
      tag.put("CuriosCosmetics", saveSlotMap(snapshot.curioCosmeticItems(), registries));
      tag.put("CuriosRenderOptions", saveBooleanSlotMap(snapshot.curioRenderOptions()));
      tag.put("Accessories", saveSlotMap(snapshot.accessoriesItems(), registries));
      tag.put("AccessoriesCosmetics", saveSlotMap(snapshot.accessoriesCosmeticItems(), registries));
      tag.put("AccessoriesRenderOptions", saveBooleanSlotMap(snapshot.accessoriesRenderOptions()));
      return tag;
   }

   private static RagdollEquipmentSnapshot loadSnapshot(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      EnumMap<EquipmentSlot, ItemStack> vanilla = new EnumMap<>(EquipmentSlot.class);
      ListTag vanillaList = tag.getList("Vanilla", Tag.TAG_COMPOUND);
      for (int i = 0; i < vanillaList.size(); i++) {
         CompoundTag itemTag = vanillaList.getCompound(i);
         EquipmentSlot slot = EquipmentSlot.byName(itemTag.getString("Slot"));
         if (slot != null && itemTag.contains("Item")) {
            ItemStack.parse(registries, itemTag.get("Item")).ifPresent(stack -> vanilla.put(slot, stack));
         }
      }

      Map<String, List<ItemStack>> curios = loadSlotMap(tag.getList("Curios", Tag.TAG_COMPOUND), registries);
      Map<String, List<ItemStack>> curioCosmetics = loadSlotMap(tag.getList("CuriosCosmetics", Tag.TAG_COMPOUND), registries);
      Map<String, List<Boolean>> curioRenderOptions = loadBooleanSlotMap(tag.getList("CuriosRenderOptions", Tag.TAG_COMPOUND));
      Map<String, List<ItemStack>> accessories = loadSlotMap(tag.getList("Accessories", Tag.TAG_COMPOUND), registries);
      Map<String, List<ItemStack>> accessoryCosmetics = loadSlotMap(tag.getList("AccessoriesCosmetics", Tag.TAG_COMPOUND), registries);
      Map<String, List<Boolean>> accessoryRenderOptions = loadBooleanSlotMap(tag.getList("AccessoriesRenderOptions", Tag.TAG_COMPOUND));
      return new RagdollEquipmentSnapshot(vanilla, curios, curioCosmetics, curioRenderOptions, accessories, accessoryCosmetics, accessoryRenderOptions);
   }

   private static ListTag saveSlotMap(Map<String, List<ItemStack>> slotMap, net.minecraft.core.HolderLookup.Provider registries) {
      ListTag list = new ListTag();
      slotMap.forEach((slotId, stacks) -> {
         CompoundTag slotTag = new CompoundTag();
         slotTag.putString("SlotId", slotId);
         ListTag stackList = new ListTag();
         for (ItemStack stack : stacks) {
            CompoundTag stackTag = new CompoundTag();
            if (!stack.isEmpty()) stackTag.put("Item", stack.save(registries));
            stackList.add(stackTag);
         }
         slotTag.put("Stacks", stackList);
         list.add(slotTag);
      });
      return list;
   }

   private static Map<String, List<ItemStack>> loadSlotMap(ListTag list, net.minecraft.core.HolderLookup.Provider registries) {
      Map<String, List<ItemStack>> slotMap = new LinkedHashMap<>();
      for (int i = 0; i < list.size(); i++) {
         CompoundTag slotTag = list.getCompound(i);
         String slotId = slotTag.getString("SlotId");
         ListTag stackList = slotTag.getList("Stacks", Tag.TAG_COMPOUND);
         List<ItemStack> stacks = new ArrayList<>(stackList.size());
         for (int j = 0; j < stackList.size(); j++) {
            CompoundTag stackTag = stackList.getCompound(j);
            Tag itemTag = stackTag.get("Item");
            stacks.add(itemTag == null ? ItemStack.EMPTY : ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY));
         }
         if (!slotId.isBlank()) slotMap.put(slotId, stacks);
      }
      return slotMap;
   }

   private static ListTag saveBooleanSlotMap(Map<String, List<Boolean>> slotMap) {
      ListTag list = new ListTag();
      slotMap.forEach((slotId, options) -> {
         CompoundTag slotTag = new CompoundTag();
         slotTag.putString("SlotId", slotId);
         ListTag optionList = new ListTag();
         for (Boolean option : options) {
            CompoundTag optionTag = new CompoundTag();
            optionTag.putBoolean("Render", Boolean.TRUE.equals(option));
            optionList.add(optionTag);
         }
         slotTag.put("Options", optionList);
         list.add(slotTag);
      });
      return list;
   }

   private static Map<String, List<Boolean>> loadBooleanSlotMap(ListTag list) {
      Map<String, List<Boolean>> slotMap = new LinkedHashMap<>();
      for (int i = 0; i < list.size(); i++) {
         CompoundTag slotTag = list.getCompound(i);
         String slotId = slotTag.getString("SlotId");
         ListTag optionList = slotTag.getList("Options", Tag.TAG_COMPOUND);
         List<Boolean> options = new ArrayList<>(optionList.size());
         for (int j = 0; j < optionList.size(); j++) {
            options.add(optionList.getCompound(j).getBoolean("Render"));
         }
         if (!slotId.isBlank()) slotMap.put(slotId, List.copyOf(options));
      }
      return slotMap;
   }
}
