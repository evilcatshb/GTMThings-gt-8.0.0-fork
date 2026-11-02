package com.hepdd.gtmthings.forge;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.api.machine.IWirelessEnergyContainerHolder;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.item.WirelessEnergyBindingToolBehavior;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;

@Mod.EventBusSubscriber(modid = GTMThings.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEventListener {

    /**
     * Binds newly placed GTMThings wireless machines after Forge has completed
     * the block-placement lifecycle. This deliberately uses Forge's placement
     * event instead of a mixin into GTCEu/vanilla placement methods, avoiding
     * mapping-sensitive injection targets while preserving immediate binding.
     */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;

        MetaMachine machine = MetaMachine.getMachine(level, event.getPos());
        if (machine instanceof IWirelessEnergyContainerHolder holder && holder.autoBindOnPlacement()) {
            holder.bindToPlacingPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerTickEvent(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WirelessEnergyContainer.expireTransferStatistics(event.getServer().getTickCount());
            if (event.getServer().getTickCount() % 20 == 0) {
                boolean refreshBinding = event.getServer().getTickCount() % 200 == 0;
                WirelessEnergySavaedData data = WirelessEnergySavaedData.INSTANCE;
                if (data == null) return;
                for (WirelessEnergyContainer container : data.containerMap.values()) {
                    if (refreshBinding) {
                        GlobalPos pos = container.getBindPos();
                        if (pos != null) {
                            long rate = WirelessEnergyBindingToolBehavior.getRate(
                                    event.getServer().getLevel(pos.dimension()), pos.pos());
                            container.setRate(rate);
                        }
                        // no bindPos = leave rate alone
                    }
                    container.getEnergyStat().tick();
                }
            }
        }
    }

    @SubscribeEvent
    public static void serverSetup(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerLevel serverLevel = level.getServer().getLevel(Level.OVERWORLD);
            if (serverLevel == null) return;
            WirelessEnergySavaedData.INSTANCE = WirelessEnergySavaedData.getOrCreate(serverLevel);
            WirelessEnergyContainer.server = event.getLevel().getServer();
        }
    }

    // fixes 110
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        WirelessEnergyContainer.server = null;
        WirelessEnergySavaedData.INSTANCE = null;
    }
}
