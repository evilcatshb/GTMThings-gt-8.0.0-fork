package com.hepdd.gtmthings.common.block.machine.multiblock.part.computation;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.hepdd.gtmthings.api.capability.IGTMTJadeIF;
import com.hepdd.gtmthings.common.block.machine.trait.WirelessNotifiableComputationContainer;
import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class WirelessOpticalComputationHatchMachine extends MultiblockPartMachine
                                                    implements IDataStickInteractable, IGTMTJadeIF {

    private final boolean transmitter;

    @SaveField
    private BlockPos transmitterPos;
    @SaveField
    private BlockPos receiverPos;
    protected WirelessNotifiableComputationContainer computationContainer;

    public WirelessOpticalComputationHatchMachine(BlockEntityCreationInfo info, boolean transmitter) {
        super(info);
        this.transmitter = transmitter;
        this.computationContainer = attachTrait(createComputationContainer(transmitter));
    }

    protected WirelessNotifiableComputationContainer createComputationContainer(Object... args) {
        IO io = IO.IN;
        if (args.length > 1 && args[args.length - 2] instanceof IO newIo) {
            io = newIo;
        }
        if (args.length > 0 && args[args.length - 1] instanceof Boolean tx) {
            return new WirelessNotifiableComputationContainer(io, tx);
        }
        throw new IllegalArgumentException("Missing transmitter flag");
    }

    @Override
    public boolean canShared(com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller, String substructureName) {
        return false;
    }

    @Override
    public boolean isbinded() {
        return (this.transmitterPos != null || this.receiverPos != null);
    }

    @Override
    public String getBindPos() {
        if (this.isTransmitter() && this.receiverPos != null) {
            return this.receiverPos.toShortString();
        } else if (!this.isTransmitter() && this.transmitterPos != null) {
            return this.transmitterPos.toShortString();
        }
        return "";
    }

    private static final String KEY_TRANSMITTER = "wireless_computation_transmitter";
    private static final String KEY_RECEIVER = "wireless_computation_receiver";

    private void setTransmitterPos(BlockPos pos) {
        if (transmitterPos != null) {
            var level = getLevel();
            if (level != null && MetaMachine.getMachine(level, transmitterPos) instanceof WirelessOpticalComputationHatchMachine machine) {
                machine.receiverPos = null;
            }
        }
        transmitterPos = pos;
    }

    private void setReceiverPos(BlockPos pos) {
        if (receiverPos != null) {
            var level = getLevel();
            if (level != null && MetaMachine.getMachine(level, receiverPos) instanceof WirelessOpticalComputationHatchMachine machine) {
                machine.transmitterPos = null;
            }
        }
        receiverPos = pos;
    }

    private static CompoundTag createPos(BlockPos pos) {
        CompoundTag posTag = new CompoundTag();
        posTag.putInt("x", pos.getX());
        posTag.putInt("y", pos.getY());
        posTag.putInt("z", pos.getZ());
        return posTag;
    }

    private static BlockPos getPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        if (isRemote()) return InteractionResult.SUCCESS;
        CompoundTag tag = dataStick.getOrCreateTag();
        BlockPos currentPos = getBlockPos();
        if (isTransmitter()) {
            tag.put(KEY_TRANSMITTER, createPos(currentPos));
            player.sendSystemMessage(Component.translatable(
                    "gtmthings.machine.wireless_computation_transmitter_hatch.tobind"));
        } else {
            tag.put(KEY_RECEIVER, createPos(currentPos));
            player.sendSystemMessage(Component.translatable(
                    "gtmthings.machine.wireless_computation_receiver_hatch.tobind"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        if (isRemote()) return InteractionResult.sidedSuccess(true);
        CompoundTag tag = dataStick.getTag();
        if (tag == null) return InteractionResult.PASS;
        if (isTransmitter() && tag.contains(KEY_RECEIVER, 10)) {
            BlockPos otherPos = getPos(tag.getCompound(KEY_RECEIVER));
            if (bindWith(otherPos, player)) return InteractionResult.SUCCESS;
        } else if (!isTransmitter() && tag.contains(KEY_TRANSMITTER, 10)) {
            BlockPos otherPos = getPos(tag.getCompound(KEY_TRANSMITTER));
            if (bindWith(otherPos, player)) return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private boolean bindWith(BlockPos otherPos, Player player) {
        Level level = getLevel();
        if (level == null || otherPos.equals(this.getBlockPos())) return false;
        MetaMachine otherMachine = MetaMachine.getMachine(level, otherPos);
        if (otherMachine instanceof WirelessOpticalComputationHatchMachine otherWoch) {
            if (this.isTransmitter() == otherWoch.isTransmitter()) return false;
            if (isTransmitter()) {
                this.setReceiverPos(otherPos);
                otherWoch.setTransmitterPos(this.getBlockPos());
            } else {
                this.setTransmitterPos(otherPos);
                otherWoch.setReceiverPos(this.getBlockPos());
            }
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_computation_hatch.binded"));
            return true;
        }
        return false;
    }
}
