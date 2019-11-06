package com.raoulvdberge.refinedstorage.item;

import com.raoulvdberge.refinedstorage.RS;
import net.minecraft.item.Item;

public class CoreItem extends Item {
    public enum Type {
        CONSTRUCTION,
        DESTRUCTION
    }

    public CoreItem(Type type) {
        super(new Item.Properties().group(RS.MAIN_GROUP));

        this.setRegistryName(RS.ID, type == Type.CONSTRUCTION ? "construction_core" : "destruction_core");
    }
}
