package com.raoulvdberge.refinedstorage.screen.grid.filtering;

import com.raoulvdberge.refinedstorage.screen.grid.stack.IGridStack;

import java.util.function.Predicate;

public class CraftableGridFilter implements Predicate<IGridStack> {
    private boolean craftable;

    public CraftableGridFilter(boolean craftable) {
        this.craftable = craftable;
    }

    @Override
    public boolean test(IGridStack stack) {
        if (craftable) {
            return stack.isCraftable();
        } else {
            return !stack.isCraftable() && stack.getOtherId() == null;
        }
    }
}
