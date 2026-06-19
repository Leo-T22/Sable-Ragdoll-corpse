package dev.leo.ragdollcorpse;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RagdollCorpseConfig {
   public static final ModConfigSpec SPEC;
   private static final ModConfigSpec.BooleanValue SPAWN_EMPTY_INVENTORY_CORPSES;
   private static final ModConfigSpec.BooleanValue ENABLE_DESPAWN_BUTTON;
   private static final ModConfigSpec.DoubleValue CORPSE_LIFETIME_MINUTES;
   private static final ModConfigSpec.DoubleValue EMPTY_CORPSE_DESPAWN_SECONDS;

   static {
      ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
      builder.push("corpses");
      SPAWN_EMPTY_INVENTORY_CORPSES = builder
         .translation("ragdoll_corpse.configuration.spawn_empty_inventory_corpses")
         .comment("Whether players with no stored death drops should still leave a ragdoll corpse.")
         .define("spawnEmptyInventoryCorpses", true);
      ENABLE_DESPAWN_BUTTON = builder
         .translation("ragdoll_corpse.configuration.enable_despawn_button")
         .comment("Whether corpses can be deleted by pressing a despawn button.")
         .define("enableDespawnButton", true);
      CORPSE_LIFETIME_MINUTES = builder
         .translation("ragdoll_corpse.configuration.corpse_lifetime_minutes")
         .comment("How long normal corpses remain before despawning, in minutes. Set to 0 to keep them forever.")
         .defineInRange("corpseLifetimeMinutes", 0.0D, 0.0D, 10080.0D);
      EMPTY_CORPSE_DESPAWN_SECONDS = builder
         .translation("ragdoll_corpse.configuration.empty_corpse_despawn_seconds")
         .comment("How long an emptied corpse waits before despawning, in seconds.")
         .defineInRange("emptyCorpseDespawnSeconds", 1.5D, 0.0D, 600.0D);
      builder.pop();
      SPEC = builder.build();
   }

   private RagdollCorpseConfig() {
   }

   public static boolean spawnEmptyInventoryCorpses() {
      return SPAWN_EMPTY_INVENTORY_CORPSES.get();
   }

   public static boolean enableDespawnButton() {
      return ENABLE_DESPAWN_BUTTON.get();
   }

   public static int emptyCorpseDespawnTicks() {
      return Math.max(0, (int) Math.round(EMPTY_CORPSE_DESPAWN_SECONDS.get() * 20.0D));
   }

   public static long corpseLifetimeTicks() {
      return Math.max(0L, Math.round(CORPSE_LIFETIME_MINUTES.get() * 60.0D * 20.0D));
   }
}