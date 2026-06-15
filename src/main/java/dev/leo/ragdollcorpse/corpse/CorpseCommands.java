package dev.leo.ragdollcorpse.corpse;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CorpseCommands {
   private CorpseCommands() {
   }

   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(root());
   }

   private static LiteralArgumentBuilder<CommandSourceStack> root() {
      return Commands.literal("ragdollcorpse")
         .requires(source -> source.hasPermission(2))
         .then(Commands.literal("list")
            .executes(CorpseCommands::list))
         .then(Commands.literal("clean")
            .executes(CorpseCommands::clean))
         .then(Commands.literal("recover-missing")
            .executes(CorpseCommands::recoverMissing))
         .then(Commands.literal("recover")
            .then(Commands.argument("corpse", StringArgumentType.word())
               .executes(CorpseCommands::recover)));
   }

   private static int list(CommandContext<CommandSourceStack> context) {
      List<IndexedCorpse> corpses = listIndexedCorpses(context.getSource().getServer());

      for (int i = 0; i < corpses.size(); i++) {
         int index = i + 1;
         IndexedCorpse corpse = corpses.get(i);
         context.getSource().sendSuccess(() -> Component.literal(formatCorpse(index, corpse.dimension(), corpse.info())), false);
      }

      int total = corpses.size();
      context.getSource().sendSuccess(() -> Component.literal("Listed " + total + " corpse(s)."), false);
      return total;
   }

   private static int clean(CommandContext<CommandSourceStack> context) {
      MinecraftServer server = context.getSource().getServer();
      int affected = 0;

      for (ServerLevel level : server.getAllLevels()) {
         affected += CorpseSavedData.get(level).releaseEmptyCorpsesNow(level);
      }

      int total = affected;
      context.getSource().sendSuccess(() -> Component.literal("Cleaned " + total + " empty corpse(s)."), true);
      return affected;
   }

   private static int recoverMissing(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = context.getSource().getPlayerOrException();
      } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
         context.getSource().sendFailure(Component.literal("Recover missing must be run by an in-game player."));
         return 0;
      }

      int corpses = 0;
      int stacks = 0;
      for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
         CorpseSavedData data = CorpseSavedData.get(level);
         for (CorpseSavedData.CorpseInfo corpse : data.listCorpses(level)) {
            if (corpse.bodyPresent()) continue;

            stacks += giveItems(player, data.takeAllItems(corpse.headId()));
            data.purgeCorpse(corpse.headId());
            corpses++;
         }
      }

      int totalCorpses = corpses;
      int totalStacks = stacks;
      context.getSource().sendSuccess(
         () -> Component.literal("Recovered " + totalStacks + " stack(s) from " + totalCorpses + " missing-body corpse(s)."),
         true
      );
      return corpses;
   }

   private static int recover(CommandContext<CommandSourceStack> context) {
      UUID headId = resolveCorpseId(context.getSource(), StringArgumentType.getString(context, "corpse"));
      if (headId == null) return 0;

      ServerPlayer player;
      try {
         player = context.getSource().getPlayerOrException();
      } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
         context.getSource().sendFailure(Component.literal("Recover must be run by an in-game player."));
         return 0;
      }

      for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
         CorpseSavedData data = CorpseSavedData.get(level);
         if (data.getContainer(headId) == null) continue;

         List<ItemStack> items = data.takeAllItems(headId);
         int stacks = giveItems(player, items);
         data.releaseCorpseNow(headId, level);
         context.getSource().sendSuccess(() -> Component.literal("Recovered " + stacks + " stack(s) from corpse " + headId + "."), true);
         return stacks;
      }

      context.getSource().sendFailure(Component.literal("No corpse found for corpse " + headId + "."));
      return 0;
   }

   private static UUID resolveCorpseId(CommandSourceStack source, String value) {
      Integer index = parsePositiveIndex(value);
      if (index != null) {
         List<IndexedCorpse> corpses = listIndexedCorpses(source.getServer());
         if (index <= corpses.size()) return corpses.get(index - 1).info().headId();

         source.sendFailure(Component.literal("No corpse at index " + index + ". Run /ragdollcorpse list for current indexes."));
         return null;
      }

      try {
         return UUID.fromString(value);
      } catch (IllegalArgumentException e) {
         source.sendFailure(Component.literal("Invalid corpse id or index: " + value));
         return null;
      }
   }

   private static Integer parsePositiveIndex(String value) {
      try {
         int index = Integer.parseInt(value);
         return index > 0 ? index : null;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private static int giveItems(ServerPlayer player, List<ItemStack> items) {
      int stacks = 0;
      for (ItemStack stack : items) {
         if (stack.isEmpty()) continue;
         ItemStack copy = stack.copy();
         player.getInventory().add(copy);
         if (!copy.isEmpty()) {
            player.drop(copy, false);
         }
         stacks++;
      }
      return stacks;
   }

   private static List<IndexedCorpse> listIndexedCorpses(MinecraftServer server) {
      List<IndexedCorpse> corpses = new ArrayList<>();
      for (ServerLevel level : server.getAllLevels()) {
         String dimension = level.dimension().location().toString();
         for (CorpseSavedData.CorpseInfo corpse : CorpseSavedData.get(level).listCorpses(level)) {
            corpses.add(new IndexedCorpse(level, dimension, corpse));
         }
      }
      corpses.sort(Comparator
         .comparing(IndexedCorpse::dimension)
         .thenComparing(c -> c.info().ownerName())
         .thenComparingLong(c -> c.info().createdTick())
         .thenComparing(c -> c.info().headId()));
      return corpses;
   }

   private static String formatCorpse(int index, String dimension, CorpseSavedData.CorpseInfo corpse) {
      BlockPos pos = corpse.pos();
      String posText = pos == null ? "unknown pos" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
      String releasing = corpse.releasing() ? ", releasing" : "";
      String body = corpse.bodyPresent() ? "" : ", missing body";
      return "#" + index
         + " | " + corpse.ownerName()
         + " | dim=" + dimension
         + " | pos=" + posText
         + " | deathTick=" + corpse.createdTick()
         + " | age=" + formatAge(corpse.ageTicks())
         + " | stacks=" + corpse.itemStacks()
         + releasing
         + body;
   }

   private static String formatAge(long ticks) {
      long seconds = ticks / 20L;
      long minutes = seconds / 60L;
      seconds %= 60L;
      return minutes + "m" + seconds + "s";
   }

   private record IndexedCorpse(ServerLevel level, String dimension, CorpseSavedData.CorpseInfo info) {
   }
}
