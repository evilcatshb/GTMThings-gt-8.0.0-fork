package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
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
import net.minecraft.world.item.Items;

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
public class WirelessEnergyHatchPartMachine extends TieredIOPartMachine
                                            implements IWirelessEnergyContainerHolder {

    @Nullable
    @Getter
    @Setter
    private WirelessEnergyContainer WirelessEnergyContainerCache;

    /** Canonical persisted wireless network identity (FTB team UUID, or player UUID without a team). */
    @SaveField
    @Nullable
    private UUID wirelessTeamUUID;

    @SaveField
    public final NotifiableEnergyContainer energyContainer;
    @Getter
    protected int amperage;
    private TickableSubscription updEnergySubs;

    public WirelessEnergyHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, int amperage,
                                          Object... args) {
        super(info, tier, io);
        this.amperage = amperage;
        this.energyContainer = attachTrait(createEnergyContainer());
    }

    protected NotifiableEnergyContainer createEnergyContainer() {
        NotifiableEnergyContainer container;
        if (io == IO.OUT) {
            container = NotifiableEnergyContainer.emitterContainer(
                    GTValues.V[tier] * 64L * amperage, GTValues.V[tier], amperage);
            container.setSideOutputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
            container.setCapabilityValidator(s -> s == null || s == getFrontFacing());
        } else {
            container = NotifiableEnergyContainer.receiverContainer(
                    GTValues.V[tier] * 16L * amperage, GTValues.V[tier], amperage);
            container.setSideInputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
            container.setCapabilityValidator(s -> s == null || s == getFrontFacing());
        }
        return container;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && wirelessTeamUUID == null && getOwnerUUID() != null) {
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(getOwnerUUID());
            markAsChanged();
        }
        updateEnergySubscription();
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
        updateEnergySubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (updEnergySubs != null) {
            updEnergySubs.unsubscribe();
            updEnergySubs = null;
        }
    }

    private void updateEnergySubscription() {
        if (this.getUUID() != null) {
            updEnergySubs = subscribeServerTick(updEnergySubs, this::updateEnergy);
        } else if (updEnergySubs != null) {
            updEnergySubs.unsubscribe();
            updEnergySubs = null;
        }
    }

    private void updateEnergy() {
        if (this.getUUID() == null) return;
        if (io == IO.IN) {
            useEnergy();
        } else {
            addEnergy();
        }
    }

    private void useEnergy() {
        var currentStored = energyContainer.getEnergyStored();
        var maxStored = energyContainer.getEnergyCapacity();
        var changeStored = Math.min(maxStored - currentStored,
                energyContainer.getInputVoltage() * energyContainer.getInputAmperage());
        if (changeStored <= 0) return;
        WirelessEnergyContainer container = getWirelessEnergyContainer();
        if (container == null) return;
        changeStored = container.removeEnergy(changeStored, this);
        if (changeStored > 0) energyContainer.setEnergyStored(currentStored + changeStored);
    }

    private void addEnergy() {
        var currentStored = energyContainer.getEnergyStored();
        if (currentStored <= 0) return;
        var changeStored = Math.min(energyContainer.getOutputVoltage() * energyContainer.getOutputAmperage(),
                currentStored);
        WirelessEnergyContainer container = getWirelessEnergyContainer();
        if (container == null) return;
        changeStored = container.addEnergy(changeStored, this);
        if (changeStored > 0) energyContainer.setEnergyStored(currentStored - changeStored);
    }

    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        var superResult = super.onUse(context);
        if (superResult != InteractionResult.PASS) return superResult;
        if (isRemote()) return InteractionResult.PASS;
        Player player = context.getPlayer();
        ItemStack is = player.getItemInHand(context.getHand());
        if (is.isEmpty()) return InteractionResult.PASS;
        if (is.is(GTItems.TOOL_DATA_STICK.get())) {
            setOwnerUUID(player.getUUID());
            wirelessTeamUUID = com.hepdd.gtmthings.utils.TeamUtil.getWirelessNetworkUUID(player.getUUID());
            setWirelessEnergyContainerCache(null);
            markAsChanged();
            player.sendSystemMessage(Component.translatable(
                    "gtmthings.machine.wireless_energy_hatch.tooltip.bind", GetName(player)));
            updateEnergySubscription();
            return InteractionResult.sidedSuccess(isRemote());
        } else if (is.is(Items.STICK)) {
            if (io == IO.OUT) energyContainer.setEnergyStored(GTValues.V[tier] * 64L * amperage);
            return InteractionResult.sidedSuccess(isRemote());
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
            player.sendSystemMessage(
                    Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.unbind"));
            updateEnergySubscription();
            return true;
        }
        return false;
    }

    @Override
    public void onMachineDestroyed() {
        // unbind owner cache when machine is removed
        setWirelessEnergyContainerCache(null);
        super.onMachineDestroyed();
    }

    @Override
    public @Nullable UUID getUUID() {
        return wirelessTeamUUID;
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }
}
