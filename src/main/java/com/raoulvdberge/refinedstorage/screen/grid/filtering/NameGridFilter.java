package com.raoulvdberge.refinedstorage.screen.grid.filtering;

import com.raoulvdberge.refinedstorage.screen.grid.stack.IGridStack;

import java.util.function.Predicate;

public class NameGridFilter implements Predicate<IGridStack> {
    private String name;

    public NameGridFilter(String name) {
        this.name = name.toLowerCase();
    }

    @Override
    public boolean test(IGridStack stack) {
        return stack.getName().toLowerCase().contains(name);
    }
}
