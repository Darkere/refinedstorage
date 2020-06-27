package com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;

import java.util.*;

class Crafting extends Craft {
    private SetMultimap<ItemStack,Integer> reusableItems= MultimapBuilder.hashKeys().hashSetValues().build();

    Crafting(ICraftingPattern pattern, boolean root, NonNullList<ItemStack> recipe) {
        super(pattern, root);
    }

    Crafting(INetwork network, CompoundNBT tag) throws CraftingTaskReadException {
        super(network, tag);
    }

    boolean introduceReusableItem(ItemStack item, int ingredientNumber){
        // true if it's the same ingredient in a different slot.
        boolean usedBefore = reusableItems.containsKey(item) && (!reusableItems.get(item).contains(ingredientNumber));
        reusableItems.put(item,ingredientNumber);
        return usedBefore;
    }

    boolean isReusableItem(ItemStack item){
        for (ItemStack itemStack : reusableItems.keySet()) {
            if(API.instance().getComparer().isEqual(item,itemStack,0)){
                return true;
            }
        }
        return false;
    }
    Collection<Integer> getReusableIngredients(){
        return reusableItems.values();
    }
}
