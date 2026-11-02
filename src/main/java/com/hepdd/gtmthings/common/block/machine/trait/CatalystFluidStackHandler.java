package com.hepdd.gtmthings.common.block.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ported for GTCEu 8.0.0: machine injected via attachTrait(), not constructor.
 * The 7.5.2 FluidStackHashStrategy no longer exists; tanks are checked directly
 * (same any-match semantics as the original deduplicated map).
 */
public class CatalystFluidStackHandler extends NotifiableFluidTank {

    public CatalystFluidStackHandler(int slots, int capacity, IO io, IO capabilityIO) {
        super(slots, capacity, io, capabilityIO);
    }

    public CatalystFluidStackHandler(int slots, int capacity, IO io) {
        this(slots, capacity, io, io);
    }

    @Override
    @Nullable
    public List<FluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<FluidIngredient> left,
                                                   boolean simulate) {
        if (recipe == null) return left;

        for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            if (content.chance() <= 0) continue;
            var ingredient = (FluidIngredient) content.content();
            for (var tank : getStorages()) {
                FluidStack fs = tank.getFluid();
                if (!fs.isEmpty() && ingredient.test(fs)) return left;
            }
        }
        return super.handleRecipeInner(io, recipe, left, simulate);
    }
}
