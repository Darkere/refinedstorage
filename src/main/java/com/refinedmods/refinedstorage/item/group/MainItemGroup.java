package com.refinedmods.refinedstorage.item.group;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.RSBlocks;
import com.refinedmods.refinedstorage.util.BlockUtils;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

public class MainItemGroup extends ItemGroup {
    public MainItemGroup() {
        super(RS.ID);
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(RSBlocks.CREATIVE_CONTROLLER.get(BlockUtils.DEFAULT_COLOR).get());
    }
}
