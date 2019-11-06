package com.raoulvdberge.refinedstorage.apiimpl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawer;
import com.raoulvdberge.refinedstorage.screen.grid.CraftingPreviewScreen;
import net.minecraft.client.gui.FontRenderer;

public class CraftingPreviewElementDrawers extends ElementDrawers {
    private CraftingPreviewScreen screen;
    private IElementDrawer<Integer> overlayDrawer = (x, y, colour) -> {
        GlStateManager.color4f(1, 1, 1, 1);
        GlStateManager.disableLighting();

        screen.fill(x, y, x + 73, y + 29, colour);
    };

    public CraftingPreviewElementDrawers(CraftingPreviewScreen screen, FontRenderer fontRenderer) {
        super(screen, fontRenderer);

        this.screen = screen;
    }

    @Override
    public IElementDrawer<Integer> getOverlayDrawer() {
        return overlayDrawer;
    }
}
