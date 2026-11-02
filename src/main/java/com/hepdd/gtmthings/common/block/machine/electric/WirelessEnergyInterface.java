package com.hepdd.gtmthings.common.block.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.hepdd.gtmthings.api.machine.IWirelessEnergyContainerHolder;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.hepdd.gtmthings.utils.TeamUtil.GetName;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WirelessEnergyInterface extends TieredIOPartMachine implements IWirelessEnergyContainerHolder {

    @Getter
    @Setter
    @Nullable
    private WirelessEnergyContainer WirelessEnergyContainerCache;

    /** Canonical persisted wireless network identity (FTB team UUID, or player UUID without a team). */
    @SaveField
    @Nullable
    private UUID wirelessTeamUUID;

    @SaveField
    public final NotifiableEnergyContainer energyContainer;

    public WirelessEnergyInterface(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX, IO.IN);
        this.energyContainer = attachTrait(new InterfaceEnergyContainer(this));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && wirelessTeamUUID == null && getOwnerUUID() != null) {
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(getOwnerUUID());
            markAsChanged();
        }
    }

    @Override
    public boolean autoBindOnPlacement() {
        return true;
    }

    @Override
    public void bindToPlacingPlayer(Player player) {
        if (isRemote() || player == null) return;
        if (wirelessTeamUUID == null) {
            setOwnerUUID(player.getUUID());
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(player.getUUID());
            setWirelessEnergyContainerCache(null);
            markAsChanged();
        }
    }

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        if (isRemote()) return InteractionResult.PASS;
        Player player = context.getPlayer();
        ItemStack is = context.getItemInHand();
        if (is.isEmpty()) return InteractionResult.PASS;
        if (is.is(GTItems.TOOL_DATA_STICK.get())) {
            setOwnerUUID(player.getUUID());
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(player.getUUID());
            setWirelessEnergyContainerCache(null);
            markAsChanged();
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.bind", GetName(player)));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onLeftClick(Player player, InteractionHand hand, @Nullable Direction face) {
        if (isRemote()) return false;
        ItemStack is = player.getItemInHand(hand);
        if (is.isEmpty()) return false;
        if (is.is(GTItems.TOOL_DATA_STICK.get())) {
            setOwnerUUID(null);
            wirelessTeamUUID = null;
            setWirelessEnergyContainerCache(null);
            markAsChanged();
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.unbind"));
            return true;
        }
        return false;
    }

    @Override
    public @Nullable UUID getUUID() {
        return wirelessTeamUUID;
    }

    /**
     * The wireless interface is a network endpoint, not a conventional buffered
     * GT energy machine. GTCEu's 8.0.0 EnergyNet calls acceptEnergyFromNetwork
     * with (voltage, amperage), so the endpoint must convert that packet directly
     * into wireless EU. The pre-8.0 GTMThings implementation used this same
     * dedicated container; replacing it with a normal NotifiableEnergyContainer
     * introduced a broken intermediate-buffer path for EnergyNet transfers.
     */
    private static final class InterfaceEnergyContainer extends NotifiableEnergyContainer {

        private final WirelessEnergyInterface energyInterface;

        private InterfaceEnergyContainer(WirelessEnergyInterface machine) {
            super(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0L, 0L);
            this.energyInterface = machine;
        }

        @Override
        public long getEnergyCapacity() {
            WirelessEnergyContainer container = energyInterface.getWirelessEnergyContainer();
            if (container == null || container.getCapacity() == null) return Long.MAX_VALUE;
            return com.hepdd.gtmthings.utils.BigIntegerUtils.getLongValue(container.getCapacity());
        }

        @Override
        public long getEnergyStored() {
            WirelessEnergyContainer container = energyInterface.getWirelessEnergyContainer();
            if (container == null) return 0L;
            return com.hepdd.gtmthings.utils.BigIntegerUtils.getLongValue(container.getStorage());
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            if (voltage <= 0L || amperage <= 0L || !inputsEnergy(side)) return 0L;

            WirelessEnergyContainer container = energyInterface.getWirelessEnergyContainer();
            if (container == null) return 0L;

            long energy;
            try {
                energy = Math.multiplyExact(voltage, amperage);
            } catch (ArithmeticException ignored) {
                energy = Long.MAX_VALUE;
            }

            long added = container.addEnergy(energy, energyInterface);
            if (added <= 0L) return 0L;

            // EnergyNet consumes amperage * voltage from the source, so only
            // report complete amperes that were actually inserted.
            return Math.min(amperage, added / voltage);
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return side == energyInterface.getFrontFacing() && energyInterface.isWorkingEnabled();
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return false;
        }

        @Override
        public void checkOutputSubscription() {}

        @Override
        public long changeEnergy(long energyToAdd) {
            return 0L;
        }
    }

    //////////////////////////////////////
    // ********** Misc **********//
    //////////////////////////////////////

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }
}
