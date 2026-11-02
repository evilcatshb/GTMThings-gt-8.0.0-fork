package com.hepdd.gtmthings.integration.jade.provider;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.jade.provider.CapabilityBlockProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.api.capability.IBindable;
import com.hepdd.gtmthings.utils.TeamUtil;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.UUID;

import static com.hepdd.gtmthings.utils.TeamUtil.hasOwner;

public class WirelessEnergyHatchProvider extends CapabilityBlockProvider<IBindable> {

    public WirelessEnergyHatchProvider() {
        super(new ResourceLocation(GTMThings.MOD_ID, "wireless_energy_hatch_provider"));
    }

    @Override
    protected @Nullable IBindable getCapability(Level level, BlockPos pos, @Nullable Direction side) {
        var metaMachine = MetaMachine.getMachine(level, pos);
        if (metaMachine != null) {
            if (metaMachine instanceof IBindable bindable && bindable.display()) {
                return bindable;
            } else {
                var covers = metaMachine.getCoverContainer().getCovers();
                for (var cover : covers) {
                    if (cover instanceof IBindable bindable && bindable.display()) {
                        return bindable;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void write(CompoundTag data, IBindable capability) {
        if (capability.getUUID() != null) {
            data.putBoolean("isBindable", true);
            data.putUUID("uuid", capability.getUUID());
            data.putBoolean("cover", capability.cover());

        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        var capData = data.getCompound(uid.toString());
        writeServerCapability(capData, "null", blockAccessor.getLevel(), blockAccessor.getPosition(), null);
        for (Direction value : com.gregtechceu.gtceu.utils.GTUtil.DIRECTIONS) {
            writeServerCapability(capData, value.getName(), blockAccessor.getLevel(), blockAccessor.getPosition(), value);
        }
        data.put(uid.toString(), capData);
    }

    private void writeServerCapability(CompoundTag capData, String key, Level level, BlockPos pos, @Nullable Direction side) {
        IBindable capability = getCapability(level, pos, side);
        if (capability == null || !allowDisplaying(capability) || capability.getUUID() == null) return;
        var tag = new CompoundTag();
        write(tag, capability);
        // This is executed by Jade's server-data path. Resolve the UUID while the
        // authoritative FTB Teams manager is available, then send the display name
        // to the client. The client must not be responsible for resolving a team UUID.
        tag.putString("displayName", TeamUtil.getWirelessNetworkName(level, capability.getUUID()).getString());
        capData.put(key, tag);
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block, BlockEntity blockEntity, IPluginConfig config) {
        if (!capData.getBoolean("isBindable")) return;
        boolean cover = capData.getBoolean("cover");
        if (!capData.hasUUID("uuid")) {
            if (cover) {
                tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_cover.tooltip.1"));
            } else {
                tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.1"));
            }
        } else {
            UUID uuid = capData.getUUID("uuid");
            String displayName = capData.getString("displayName");
            if (!displayName.isEmpty()) {
                if (cover) {
                    tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_cover.tooltip.2", Component.literal(displayName)));
                } else {
                    tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.2", Component.literal(displayName)));
                }
            } else if (hasOwner(block.getLevel(), uuid)) {
                if (cover) {
                    tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_cover.tooltip.2", TeamUtil.getWirelessNetworkName(block.getLevel(), uuid)));
                } else {
                    tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.2", TeamUtil.getWirelessNetworkName(block.getLevel(), uuid)));
                }
            } else {
                if (cover) {
                    tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_cover.tooltip.3", uuid));
                } else {
                    tooltip.add(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.3", uuid));
                }
            }
        }
    }
}
