package com.hepdd.gtmthings.mixin;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;

import com.hepdd.gtmthings.api.machine.trait.ProgrammableCircuitTrait;
import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.CustomItems;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported from GTMThings 7.x for GTCEu 8.0.0.
 *
 * <p>
 * 1. Redirects the {@code new ProgrammableCircuitSlotTrait()} inside the constructor to
 * install {@link ProgrammableCircuitTrait} instead. The {@code @At(NEW)} target uses just the
 * class descriptor without {@code <init>()V} — Mixin 0.8.5's {@code BeforeNew} selector
 * requires only the type descriptor for matching the {@code NEW} opcode.
 *
 * <p>
 * 2. Filters the import bus at constructor TAIL so configured Virtual Item Provider cards
 * cannot enter the normal item-input bus when the feature is enabled.
 *
 * <p>
 * When {@link ConfigHolder#enableProgrammableCircuit} is {@code false} (the default), both
 * injections are functionally no-ops and GTCEu 8.0.0 vanilla behavior is fully preserved.
 */
@Mixin(SimpleTieredMachine.class)
public abstract class SimpleTieredMachineMixin extends WorkableTieredMachine {

    // Never invoked - exists so the class compiles against WorkableTieredMachine members.
    private SimpleTieredMachineMixin(BlockEntityCreationInfo info, int tier, boolean energyEmitter,
                                     RecipeLogic recipeLogic, Int2IntFunction tankScalingFunction) {
        super(info, tier, energyEmitter, recipeLogic, tankScalingFunction);
    }

    /**
     * Intercepts the {@code new ProgrammableCircuitSlotTrait()} call and returns a
     * {@link ProgrammableCircuitTrait} subclass instance instead. The target is just the class
     * descriptor without {@code <init>()V} — Mixin 0.8.5 matches {@code NEW} opcodes by type only.
     */
    @Redirect(
              method = "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;ILcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;Lit/unimi/dsi/fastutil/ints/Int2IntFunction;)V",
              at = @At(value = "NEW",
                       target = "com/gregtechceu/gtceu/common/machine/trait/ProgrammableCircuitSlotTrait"),
              remap = false)
    private ProgrammableCircuitSlotTrait gtmthings$replaceCircuitTrait() {
        return new ProgrammableCircuitTrait();
    }

    @Inject(
            method = "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;ILcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;Lit/unimi/dsi/fastutil/ints/Int2IntFunction;)V",
            at = @At("TAIL"),
            remap = false)
    private void gtmthings$filterImportItems(BlockEntityCreationInfo info, int tier, RecipeLogic recipeLogic,
                                             Int2IntFunction tankScalingFunction, CallbackInfo ci) {
        this.importItems.setFilter(i -> !ConfigHolder.INSTANCE.enableProgrammableCircuit ||
                !i.is(CustomItems.VIRTUAL_ITEM_PROVIDER.get()) || !i.hasTag());
    }
}
