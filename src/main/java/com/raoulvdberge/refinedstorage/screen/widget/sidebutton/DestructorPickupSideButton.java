package com.raoulvdberge.refinedstorage.screen.widget.sidebutton;

import com.raoulvdberge.refinedstorage.screen.BaseScreen;
import com.raoulvdberge.refinedstorage.tile.DestructorTile;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

public class DestructorPickupSideButton extends SideButton {
    public DestructorPickupSideButton(BaseScreen screen) {
        super(screen);
    }

    @Override
    protected void renderButtonIcon(int x, int y) {
        screen.blit(x, y, 64 + (!DestructorTile.PICKUP.getValue() ? 16 : 0), 0, 16, 16);
    }

    @Override
    public String getTooltip() {
        return I18n.format("sidebutton.refinedstorage.destructor.pickup") + "\n" + TextFormatting.GRAY + I18n.format(DestructorTile.PICKUP.getValue() ? "gui.yes" : "gui.no");
    }

    @Override
    public void onPress() {
        TileDataManager.setParameter(DestructorTile.PICKUP, !DestructorTile.PICKUP.getValue());
    }
}
