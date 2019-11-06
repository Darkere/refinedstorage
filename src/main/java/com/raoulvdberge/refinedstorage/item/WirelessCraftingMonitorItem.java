package com.raoulvdberge.refinedstorage.item;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.item.INetworkItem;
import com.raoulvdberge.refinedstorage.api.network.item.INetworkItemManager;
import com.raoulvdberge.refinedstorage.apiimpl.network.item.WirelessCraftingMonitorNetworkItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

public class WirelessCraftingMonitorItem extends NetworkItem {
    public static final String NBT_TAB_SELECTED = "TabSelected";
    public static final String NBT_TAB_PAGE = "TabPage";

    public enum Type {
        NORMAL,
        CREATIVE
    }

    private final Type type;

    public WirelessCraftingMonitorItem(Type type) {
        super(new Item.Properties().group(RS.MAIN_GROUP).maxStackSize(1), type == Type.CREATIVE, () -> RS.SERVER_CONFIG.getWirelessCraftingMonitor().getCapacity());

        this.setRegistryName(RS.ID, (type == Type.CREATIVE ? "creative_" : "") + "wireless_crafting_monitor");

        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Nonnull
    @Override
    public INetworkItem provide(INetworkItemManager handler, PlayerEntity player, ItemStack stack) {
        int invIndex = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
            if (player.inventory.getStackInSlot(i) == stack) {
                invIndex = i;
                break;
            }
        }

        return new WirelessCraftingMonitorNetworkItem(handler, player, stack, invIndex);
    }

    public static Optional<UUID> getTabSelected(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().hasUniqueId(NBT_TAB_SELECTED)) {
            return Optional.of(stack.getTag().getUniqueId(NBT_TAB_SELECTED));
        }

        return Optional.empty();
    }

    public static void setTabSelected(ItemStack stack, Optional<UUID> tabSelected) {
        if (!stack.hasTag()) {
            stack.setTag(new CompoundNBT());
        }

        if (tabSelected.isPresent()) {
            stack.getTag().putUniqueId(NBT_TAB_SELECTED, tabSelected.get());
        } else {
            stack.getTag().remove(NBT_TAB_SELECTED + "Least");
            stack.getTag().remove(NBT_TAB_SELECTED + "Most");
        }
    }

    public static int getTabPage(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(NBT_TAB_PAGE)) {
            return stack.getTag().getInt(NBT_TAB_PAGE);
        }

        return 0;
    }

    public static void setTabPage(ItemStack stack, int tabPage) {
        if (!stack.hasTag()) {
            stack.setTag(new CompoundNBT());
        }

        stack.getTag().putInt(NBT_TAB_PAGE, tabPage);
    }
}
