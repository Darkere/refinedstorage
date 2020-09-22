package com.refinedmods.refinedstorage.item.blockitem;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.NetworkType;
import com.refinedmods.refinedstorage.block.ControllerBlock;
import net.minecraft.block.Block;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.RegistryObject;

public class ControllerBlockItem extends EnergyBlockItem {
    private final DyeColor color;
    private final RegistryObject<? extends Block> blockForTranslation;

    public ControllerBlockItem(ControllerBlock block, DyeColor color, RegistryObject<? extends Block> blockForTranslation) {
        super(block, new Item.Properties().group(RS.MAIN_GROUP).maxStackSize(1), block.getType() == NetworkType.CREATIVE, () -> RS.SERVER_CONFIG.getController().getCapacity());
        this.color = color;
        this.blockForTranslation = blockForTranslation;
    }

    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        IFormattableTextComponent text = new StringTextComponent("");
        if (color != DyeColor.LIGHT_BLUE) {
            text.append(new TranslationTextComponent("color.minecraft." + color.getTranslationKey()));
            text.append(new StringTextComponent(" "));
        }

        text.append(new TranslationTextComponent(blockForTranslation.get().getTranslationKey()));
        return text;
    }
}
