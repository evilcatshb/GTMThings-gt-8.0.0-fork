package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandlerModifiable;

import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.PhantomItemSlotSyncHandler;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import com.hepdd.gtmthings.api.machine.trait.ProgrammableCircuitTrait;
import com.hepdd.gtmthings.common.cover.ProgrammableCover;
import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.CustomItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static brachy.modularui.api.drawable.Text.lang;

@ParametersAreNonnullByDefault
public final class VirtualItemProviderBehavior implements IAddInformation, IItemUIHolder {

    public static final VirtualItemProviderBehavior INSTANCE = new VirtualItemProviderBehavior();

    private VirtualItemProviderBehavior() {}

    private static ItemStack setVirtualItem(ItemStack stack, ItemStack virtualItem) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove("t");
        if (virtualItem.isEmpty()) {
            tag.remove("m");
            tag.remove("n");
        } else {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(virtualItem.getItem());
            tag.putString("m", id.getNamespace());
            tag.putString("n", id.getPath());
            CompoundTag itemTag = virtualItem.getTag();
            if (itemTag != null) tag.put("t", itemTag);
        }
        return stack;
    }

    public static ItemStack getVirtualItem(ItemStack item) {
        CompoundTag tag = item.getOrCreateTag();
        String mod = tag.getString("m");
        if (mod.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = BuiltInRegistries.ITEM.get(new ResourceLocation(mod, tag.getString("n"))).getDefaultInstance();
        if (tag.contains("t")) stack.setTag(tag.getCompound("t"));
        return stack;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemstack, @Nullable Level world, @NotNull List<Component> list,
                                @NotNull TooltipFlag flag) {
        if (itemstack.hasTag()) {
            list.add(Component.translatable("gui.ae2.Items").append(": ")
                    .append(getVirtualItem(itemstack).getDisplayName()));
        }
    }

    //////////////////////////////////////
    // ****** MACHINE INTERACTION ******//
    //////////////////////////////////////

    /**
     * Ported from GTMThings 7.x's SimpleTieredMachineMixin#createCircuitItemHandler, rewritten for GTCEu 8.0.0.
     * <p>
     * Gated behind {@link ConfigHolder#enableProgrammableCircuit} - when disabled, this is a no-op and the
     * card behaves like any other item on right-click. When enabled: right-clicking a machine with a
     * configured card (and a {@link ProgrammableCover} attached) resolves the card's stored virtual item and
     * pushes it directly into the machine's {@link ProgrammableCircuitTrait}, consuming one card from the
     * player's hand on success. This mirrors GTCEu's own {@code IntCircuitBehaviour.useOn} pattern for setting
     * a circuit by right-click, and exists alongside (not instead of) piping/dragging a card directly into the
     * slot, which {@link ProgrammableCircuitTrait}'s storage handles on its own.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!ConfigHolder.INSTANCE.enableProgrammableCircuit) return IItemUIHolder.super.useOn(context);
        if (!context.isSecondaryUseActive()) return IItemUIHolder.super.useOn(context);

        Level level = context.getLevel();
        MetaMachine machine = MetaMachine.getMachine(level, context.getClickedPos());
        if (machine == null) return IItemUIHolder.super.useOn(context);

        boolean allowed = false;
        for (CoverBehavior cover : machine.getCoverContainer().getCovers()) {
            if (cover instanceof ProgrammableCover) {
                allowed = true;
                break;
            }
        }
        if (!allowed) return IItemUIHolder.super.useOn(context);

        var circuitTrait = machine.getTraitOptional(ProgrammableCircuitTrait.class);
        if (circuitTrait.isEmpty()) return IItemUIHolder.super.useOn(context);

        ItemStack stack = context.getItemInHand();
        circuitTrait.get().setVirtualItem(getVirtualItem(stack));
        if (!level.isClientSide) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager,
                                   UISettings settings) {
        Player player = data.getPlayer();
        ItemStack held = data.getUsedItemStack();

        // single-slot virtual item buffer, read-only display + insert-to-set
        var handler = new ItemHandler(held);
        syncManager.syncValue("virtual_item",
                new PhantomItemSlotSyncHandler(new ModularSlot(handler, 0)
                        .ignoreMaxStackSize(true).accessibility(true, false)));

        return ModularPanel.defaultPanel("virtual_item_provider", 150, 120)
                .child(Flow.col()
                        .margin(7, 7)
                        .childPadding(4)
                        .child(new TextWidget<>(lang("item.gtmthings.virtual_item_provider.gui.title")))
                        .child(new ItemSlot()
                                .slot(new ModularSlot(handler, 0).slotGroup("virtual"))
                                .background(GTGuiTextures.SLOT))
                        .child(SlotGroupWidget.playerInventory(true)));
    }

    private record ItemHandler(ItemStack holder) implements IItemHandlerModifiable {

        @Override
        public void setStackInSlot(int i, @NotNull ItemStack arg) {
            setVirtualItem(holder, arg.copyWithCount(1));
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int i) {
            return getVirtualItem(holder);
        }

        @Override
        public @NotNull ItemStack insertItem(int i, @NotNull ItemStack arg, boolean bl) {
            if (arg.is(CustomItems.VIRTUAL_ITEM_PROVIDER.get())) return arg;
            setVirtualItem(holder, arg.copyWithCount(1));
            return arg.copyWithCount(arg.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int i, int j, boolean bl) {
            if (holder.getOrCreateTag().getBoolean("marked")) return ItemStack.EMPTY;
            ItemStack current = getStackInSlot(0);
            setVirtualItem(holder, ItemStack.EMPTY);
            return current;
        }

        @Override
        public int getSlotLimit(int i) {
            return 1;
        }

        @Override
        public boolean isItemValid(int i, @NotNull ItemStack arg) {
            return !arg.is(CustomItems.VIRTUAL_ITEM_PROVIDER.get());
        }
    }
}
