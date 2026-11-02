package com.hepdd.gtmthings.api.machine.trait;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;

import net.minecraft.world.item.ItemStack;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.common.cover.ProgrammableCover;
import com.hepdd.gtmthings.common.item.VirtualItemProviderBehavior;
import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.CustomItems;
import org.jetbrains.annotations.NotNull;

/**
 * GTMThings' 8.0.0 replacement for the vanilla integrated-circuit slot on
 * {@link com.gregtechceu.gtceu.api.machine.SimpleTieredMachine}, gated behind
 * {@link ConfigHolder#enableProgrammableCircuit} (disabled by default).
 *
 * <p>
 * When the config option is disabled, this trait is a pure passthrough to vanilla
 * {@link ProgrammableCircuitSlotTrait} behavior - physical Programmed Circuits work exactly
 * like on any other GTCEu 8.0.0 machine, and Virtual Item Provider cards are inert here.
 *
 * <p>
 * When enabled, it reproduces GTMThings 7.x's {@code ProgrammableCircuitHandler}: the slot
 * blocks physical circuits entirely and only accepts a configured Virtual Item Provider card
 * (gated by a {@link ProgrammableCover} being attached), resolving the card into its stored
 * virtual item on insertion. Cards can be piped in by automation or dragged into the slot in
 * the GUI, not just placed via {@link VirtualItemProviderBehavior#useOn}.
 *
 * <p>
 * Instances of this class are installed by
 * {@link com.hepdd.gtmthings.mixin.ProgrammableCircuitSlotTraitMixin} intercepting
 * {@link ProgrammableCircuitSlotTrait#createStorage()} via the factory-hook our custom GTCEu fork
 * exposes. {@link com.hepdd.gtmthings.mixin.SimpleTieredMachineMixin} additionally filters the
 * import bus so configured cards cannot enter the normal item-input slots.
 */
public class ProgrammableCircuitTrait extends ProgrammableCircuitSlotTrait {

    /**
     * Called by {@link com.hepdd.gtmthings.mixin.ProgrammableCircuitSlotTraitMixin} when this
     * instance needs its backing storage set up. Exposed as {@code public} so the mixin (which
     * lives in a different package) can reach it without reflection.
     */
    public CustomItemStackHandler createVirtualStorage() {
        return new VirtualItemStorage(this);
    }

    /**
     * Directly sets the slot's contents to the given (already-resolved) virtual item, bypassing
     * {@link VirtualItemStorage#isItemValid}, which only gates {@code insertItem}, not
     * {@code setStackInSlot}. Used by {@link VirtualItemProviderBehavior#useOn} for right-click.
     */
    public void setVirtualItem(ItemStack virtualItem) {
        storage.setStackInSlot(0, virtualItem);
    }

    public ItemStack getVirtualItem() {
        return storage.getStackInSlot(0);
    }

    @Override
    public void attachLeftConfigurators(Flow flow, ModularPanel<?> panel, PanelSyncManager syncManager) {
        if (!ConfigHolder.INSTANCE.enableProgrammableCircuit) {
            // Feature disabled: keep the normal int-circuit picker button.
            super.attachLeftConfigurators(flow, panel, syncManager);
            return;
        }
        // Intentionally no-op when enabled: the vanilla int-circuit picker doesn't apply to a
        // virtual-item-only slot. Population happens by right-clicking the machine with a
        // configured Virtual Item Provider card, or piping/dragging one into this slot.
    }

    /**
     * Backing item handler for the slot. Delegates straight to vanilla behavior (physical-circuit-only)
     * whenever the feature is turned off, so toggling the config option cannot leave a machine stuck
     * in a half-configured state.
     */
    public static class VirtualItemStorage extends CustomItemStackHandler {

        private final ProgrammableCircuitTrait trait;

        public VirtualItemStorage(ProgrammableCircuitTrait trait) {
            super(1);
            this.trait = trait;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (!ConfigHolder.INSTANCE.enableProgrammableCircuit) {
                return IntCircuitBehaviour.isIntegratedCircuit(stack);
            }
            return stack.is(CustomItems.VIRTUAL_ITEM_PROVIDER.get()) && coverAllows();
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!ConfigHolder.INSTANCE.enableProgrammableCircuit) {
                return IntCircuitBehaviour.isIntegratedCircuit(stack) ? super.insertItem(slot, stack, simulate) :
                        stack;
            }
            if (!stack.is(CustomItems.VIRTUAL_ITEM_PROVIDER.get()) || !coverAllows()) return stack;
            if (!simulate) setStackInSlot(slot, VirtualItemProviderBehavior.getVirtualItem(stack));
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!ConfigHolder.INSTANCE.enableProgrammableCircuit) {
                return super.extractItem(slot, amount, simulate);
            }
            // Matches GTMThings 7.x: the resolved virtual item can only be cleared by overwriting it
            // (right-click/insert a new card), not pulled back out by automation.
            return simulate ? super.extractItem(slot, amount, true) : ItemStack.EMPTY;
        }

        private boolean coverAllows() {
            for (CoverBehavior cover : trait.getMachine().getCoverContainer().getCovers()) {
                if (cover instanceof ProgrammableCover) return true;
            }
            return false;
        }
    }
}
