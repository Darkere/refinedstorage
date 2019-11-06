package com.raoulvdberge.refinedstorage.screen.widget.sidebutton;

import com.raoulvdberge.refinedstorage.screen.BaseScreen;
import com.raoulvdberge.refinedstorage.tile.CrafterTile;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

public class CrafterModeSideButton extends SideButton {
    public CrafterModeSideButton(BaseScreen screen) {
        super(screen);
    }

    @Override
    public String getTooltip() {
        return I18n.format("sidebutton.refinedstorage.crafter_mode") + "\n" + TextFormatting.GRAY + I18n.format("sidebutton.refinedstorage.crafter_mode." + CrafterTile.MODE.getValue());
    }

    @Override
    protected void renderButtonIcon(int x, int y) {
        screen.blit(x, y, CrafterTile.MODE.getValue() * 16, 0, 16, 16);
    }

    @Override
    public void onPress() {
        TileDataManager.setParameter(CrafterTile.MODE, CrafterTile.MODE.getValue() + 1);
    }
}
