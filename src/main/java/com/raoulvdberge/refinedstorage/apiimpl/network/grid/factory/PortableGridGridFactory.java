package com.raoulvdberge.refinedstorage.apiimpl.network.grid.factory;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.grid.GridFactoryType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridFactory;
import com.raoulvdberge.refinedstorage.tile.grid.portable.PortableGrid;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class PortableGridGridFactory implements IGridFactory {
    public static final ResourceLocation ID = new ResourceLocation(RS.ID, "portable_grid_item");

    @Nullable
    @Override
    public IGrid createFromStack(PlayerEntity player, ItemStack stack) {
        PortableGrid portableGrid = new PortableGrid(player, stack);

        portableGrid.onOpen();

        return portableGrid;
    }

    @Nullable
    @Override
    public IGrid createFromBlock(PlayerEntity player, BlockPos pos) {
        return null;
    }

    @Nullable
    @Override
    public TileEntity getRelevantTile(World world, BlockPos pos) {
        return null;
    }

    @Override
    public GridFactoryType getType() {
        return GridFactoryType.STACK;
    }
}
