package dev.leo.ragdollcorpse.client;

import java.util.Optional;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public final class CorpseCompassClient {
   @Nullable
   private static volatile GlobalPos target;

   private CorpseCompassClient() {
   }

   public static void set(Optional<GlobalPos> newTarget) {
      target = newTarget.orElse(null);
   }

   public static void register() {
      ItemProperties.register(
         Items.RECOVERY_COMPASS,
         ResourceLocation.withDefaultNamespace("angle"),
         new CompassItemPropertyFunction((level, stack, entity) -> {
            GlobalPos corpse = target;
            if (corpse != null) {
               return corpse;
            }
            return entity instanceof Player player ? player.getLastDeathLocation().orElse(null) : null;
         })
      );
   }
}
