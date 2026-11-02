package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.item.component.forge.IComponentCapability;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.FluidSlot;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.hepdd.gtmthings.api.misc.CreativeFluidHandlerItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeFluidStats implements IItemComponent, IComponentCapability, IAddInformation, IItemUIHolder {

    private ItemStack itemStack;
    protected final CustomFluidTank creativeTank;

    public CreativeFluidStats() {
        this.creativeTank = new CustomFluidTank(1000);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                                TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("gtmthings.creative_tooltip"));
        if (stack.hasTag() && stack.getTag().contains("Fluid")) {
            FluidUtil.getFluidContained(stack).ifPresent(tank -> tooltipComponents.add(
                    Component.translatable("item.gtmthings.creative_fluid_cell.tooltip1", tank.getDisplayName())));
            if (getAccurate(stack)) {
                tooltipComponents.add(
                        Component.translatable("item.gtmthings.creative_fluid_cell.tooltip3", getCapacity(stack)));
            }
        } else {
            tooltipComponents.add(Component.translatable("item.gtmthings.creative_fluid_cell.tooltip2"));
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(ItemStack itemStack, @NotNull Capability<T> cap) {
        if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) {
            FluidStack fluidStack = getStored(itemStack);
            int capacity = getAccurate(itemStack) ? getCapacity(itemStack) : Integer.MAX_VALUE;
            if (!fluidStack.isEmpty()) {
                return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(cap,
                        LazyOptional.of(() -> new CreativeFluidHandlerItemStack(itemStack, capacity, fluidStack)));
            }
        }
        return LazyOptional.empty();
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager,
                                   UISettings settings) {
        this.itemStack = data.getUsedItemStack();
        this.creativeTank.setFluid(readStoredFluid());
        this.creativeTank.setOnContentsChanged(this::onTankChanged);

        // phantom fluid slot: syncs to the shared creativeTank
        syncManager.syncValue("creative_fluid_slot",
                new FluidSlotSyncHandler(creativeTank).phantom(true).controlsAmount(false));

        BooleanSyncValue accurate = new BooleanSyncValue(
                () -> getAccurate(this.itemStack),
                v -> setAccurate(v)).allowC2S();
        syncManager.syncValue("accurate", accurate);

        IntSyncValue capacity = new IntSyncValue(
                () -> getCapacity(this.itemStack),
                v -> setCapacity(v)).allowC2S();
        syncManager.syncValue("capacity", capacity);

        var panel = ModularPanel.defaultPanel("creative_fluid_cell", 176, 130);

        panel.child(Flow.col()
                .margin(7, 7)
                .childPadding(4)
                .child(new TextWidget<>(Text.lang("gtceu.creative.tank.fluid")))
                .child(Flow.row()
                        .coverChildrenHeight()
                        .child(new FluidSlot().syncHandler("creative_fluid_slot"))
                        .child(new ToggleButton()
                                .stateBackground(GTGuiTextures.BUTTON_BLACKLIST)
                                .syncHandler("accurate"))
                        .child(new TextFieldWidget()
                                .setNumbers(1, Integer.MAX_VALUE)
                                .setMaxLength(9)
                                .syncHandler("capacity")))
                .child(SlotGroupWidget.playerInventory(true)));

        return panel;
    }

    // ── NBT helpers ──────────────────────────────────────────────────────────

    private FluidStack readStoredFluid() {
        CompoundTag tag = this.itemStack.getTag();
        if (tag == null || !tag.contains("Fluid")) return FluidStack.EMPTY;
        return FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
    }

    private boolean getAccurate(ItemStack fluidCell) {
        CompoundTag tagCompound = fluidCell.getTag();
        return tagCompound != null && tagCompound.contains("Accurate") && tagCompound.getBoolean("Accurate");
    }

    private void setAccurate(boolean isEnable) {
        if (!this.itemStack.hasTag()) {
            this.itemStack.setTag(new CompoundTag());
        }
        this.itemStack.getTag().putBoolean("Accurate", isEnable);
        if (!isEnable && this.itemStack.getTag().contains("Fluid")) {
            // keep fluid, just switch mode
        }
        if (this.itemStack.getTag().contains("Fluid")) {
            FluidStack fs = readStoredFluid();
            if (!fs.isEmpty()) {
                this.creativeTank.setFluid(fs);
            }
        }
    }

    private FluidStack getStored(ItemStack fluidCell) {
        CompoundTag tagCompound = fluidCell.getTag();
        return tagCompound != null && tagCompound.contains("Fluid") ?
                FluidStack.loadFluidStackFromNBT(tagCompound.getCompound("Fluid")) : FluidStack.EMPTY;
    }

    private int getCapacity(ItemStack fluidCell) {
        CompoundTag tagCompound = fluidCell.getTag();
        return tagCompound != null && tagCompound.contains("Capacity") ? tagCompound.getInt("Capacity") : 1000;
    }

    private void setCapacity(int capacity) {
        if (!this.itemStack.hasTag()) {
            this.itemStack.setTag(new CompoundTag());
        }
        this.itemStack.getTag().putInt("Capacity", capacity);
    }

    /**
     * Called by the tank's contents-changed runnable wiring: the phantom slot
     * wrote a fluid into the tank; persist it to the item NBT.
     */
    protected void onTankChanged() {
        var fluid = this.creativeTank.getFluid();
        if (fluid.isEmpty()) {
            if (this.itemStack.hasTag()) this.itemStack.getTag().remove("Fluid");
        } else {
            FluidStack stored = fluid.copy();
            stored.setAmount(1000);
            if (!this.itemStack.hasTag()) {
                this.itemStack.setTag(new CompoundTag());
            }
            CompoundTag fluidTag = new CompoundTag();
            stored.writeToNBT(fluidTag);
            this.itemStack.getTag().put("Fluid", fluidTag);
        }
    }
}
