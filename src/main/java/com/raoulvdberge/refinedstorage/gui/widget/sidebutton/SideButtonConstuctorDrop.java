package com.raoulvdberge.refinedstorage.gui.widget.sidebutton;

import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.tile.TileConstructor;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

public class SideButtonConstuctorDrop extends SideButton {
    public SideButtonConstuctorDrop(GuiBase gui) {
        super(gui);
    }

    @Override
    protected void drawButtonIcon(int x, int y) {
        gui.blit(x, y, 64 + (TileConstructor.DROP.getValue() ? 16 : 0), 16, 16, 16);
    }

    @Override
    public String getTooltip() {
        return I18n.format("sidebutton.refinedstorage:constructor.drop") + "\n" + TextFormatting.GRAY + I18n.format(TileConstructor.DROP.getValue() ? "gui.yes" : "gui.no");
    }

    @Override
    public void onPress() {
        TileDataManager.setParameter(TileConstructor.DROP, !TileConstructor.DROP.getValue());
    }
}