package com.raoulvdberge.refinedstorage.gui.widget.sidebutton;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

public class SideButtonGridSearchBoxMode extends SideButton {
    public SideButtonGridSearchBoxMode(GuiGrid gui) {
        super(gui);
    }

    @Override
    public String getTooltip() {
        return I18n.format("sidebutton.refinedstorage:grid.search_box_mode") + "\n" + TextFormatting.GRAY + I18n.format("sidebutton.refinedstorage:grid.search_box_mode." + ((GuiGrid) gui).getGrid().getSearchBoxMode());
    }

    @Override
    protected void drawButtonIcon(int x, int y) {
        int mode = ((GuiGrid) gui).getGrid().getSearchBoxMode();

        gui.blit(x, y, mode == IGrid.SEARCH_BOX_MODE_NORMAL_AUTOSELECTED || mode == IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED ? 16 : 0, 96, 16, 16);
    }

    @Override
    public void onPress() {
        int mode = ((GuiGrid) gui).getGrid().getSearchBoxMode();

        if (mode == IGrid.SEARCH_BOX_MODE_NORMAL) {
            mode = IGrid.SEARCH_BOX_MODE_NORMAL_AUTOSELECTED;
        } else if (mode == IGrid.SEARCH_BOX_MODE_NORMAL_AUTOSELECTED) {
            //TODO if (IntegrationJEI.isLoaded()) {
            //    mode = IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED;
            //} else {
                mode = IGrid.SEARCH_BOX_MODE_NORMAL;
            //}
        } else if (mode == IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED) {
            mode = IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED;
        } else if (mode == IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED) {
            mode = IGrid.SEARCH_BOX_MODE_NORMAL;
        }

        ((GuiGrid) gui).getGrid().onSearchBoxModeChanged(mode);

        ((GuiGrid) gui).getSearchField().setMode(mode);
    }
}