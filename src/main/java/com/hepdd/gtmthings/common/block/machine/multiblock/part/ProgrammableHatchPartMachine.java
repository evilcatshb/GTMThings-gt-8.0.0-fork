package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;

import com.hepdd.gtmthings.data.CustomItems;

public class ProgrammableHatchPartMachine extends DualHatchPartMachine {

    public ProgrammableHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
        super(info, tier, io);
        getInventory().setFilter(itemStack -> !itemStack.is(CustomItems.VIRTUAL_ITEM_PROVIDER.get()));
        getCircuitSlot().setEnabled(io == IO.IN);
    }
}
