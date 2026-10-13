package ru.frend.ai;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CraftingHelper {

    public static ItemStack craftOne(ServerPlayerEntity bot, MinecraftServer server, ServerWorld world, Item desiredResult) {
        List<CraftingRecipe> recipes = new ArrayList<>();
        for (var entry : server.getRecipeManager().values()) {
            if (entry.value() instanceof CraftingRecipe cr) recipes.add(cr);
        }
        for (CraftingRecipe recipe : recipes) {
            if (recipe.getResult(world.getRegistryManager()).getItem() != desiredResult) continue;
            CraftingInventory grid = new CraftingInventory(null, 3, 3);
            if (!placeIngredients(recipe, grid, bot)) continue;
            if (!recipe.matches(grid, world)) continue;

            // remove ingredients
            for (int i = 0; i < grid.size(); i++) {
                ItemStack s = grid.getStack(i);
                if (s.isEmpty()) continue;
                removeOne(bot, s.getItem());
            }
            ItemStack result = recipe.craft(grid, world.getRegistryManager());
            bot.getInventory().insertStack(result);
            return result;
        }
        return ItemStack.EMPTY;
    }

    private static boolean placeIngredients(CraftingRecipe recipe, CraftingInventory grid, ServerPlayerEntity bot) {
        // Shaped/shapeless: try recipe.getIngredients list; place each ingredient in its own slot if possible.
        List<Ingredient> ingredients = recipe.getIngredients();
        int slots = grid.size();
        int g = 0;
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) {
                g++;
                if (g >= slots) break;
                continue;
            }
            boolean placed = false;
            for (ItemStack cand : ing.getMatchingStacks()) {
                if (countInInventory(bot, cand.getItem()) > 0) {
                    grid.setStack(g, new ItemStack(cand.getItem(), 1));
                    placed = true;
                    break;
                }
            }
            if (!placed) return false;
            g++;
            if (g >= slots) break;
        }
        return true;
    }

    private static int countInInventory(ServerPlayerEntity bot, Item item) {
        int n = 0;
        for (int i = 0; i < bot.getInventory().size(); i++) {
            ItemStack s = bot.getInventory().getStack(i);
            if (s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private static void removeOne(ServerPlayerEntity bot, Item item) {
        for (int i = 0; i < bot.getInventory().size(); i++) {
            ItemStack s = bot.getInventory().getStack(i);
            if (s.getItem() == item && !s.isEmpty()) {
                s.decrement(1);
                if (s.isEmpty()) bot.getInventory().setStack(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    public static void craftRepeat(ServerPlayerEntity bot, MinecraftServer server, ServerWorld world, Item desiredResult, int times) {
        for (int i = 0; i < times; i++) {
            ItemStack r = craftOne(bot, server, world, desiredResult);
            if (r.isEmpty()) break;
        }
    }
}
