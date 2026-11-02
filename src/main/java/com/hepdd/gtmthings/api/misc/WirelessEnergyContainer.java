package com.hepdd.gtmthings.api.misc;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import com.hepdd.gtmthings.utils.BigIntegerUtils;
import com.hepdd.gtmthings.utils.TeamUtil;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.UUID;
import java.util.WeakHashMap;

@Getter
public class WirelessEnergyContainer {

    /**
     * Live transfer observations collected since the last server-side snapshot.
     * This is producer state and must not be consumed by a GUI.
     */
    public static final WeakHashMap<MetaMachine, ITransferData> TRANSFER_DATA = new WeakHashMap<>();

    /**
     * Published observation window shared by every wireless monitor/terminal.
     * GUIs only read this snapshot; they never clear or mutate the live map.
     */
    public static final WeakHashMap<MetaMachine, ITransferData> TRANSFER_SNAPSHOT = new WeakHashMap<>();

    /** Last server tick on which each producer reported a transfer. */
    public static final WeakHashMap<MetaMachine, Long> TRANSFER_LAST_TICK = new WeakHashMap<>();

    public static MinecraftServer server;

    public static WirelessEnergyContainer getOrCreateContainer(UUID playerUUID) {
        return getOrCreateContainerByWirelessUUID(TeamUtil.getWirelessNetworkUUID(playerUUID));
    }

    /** Resolve a canonical wireless UUID without applying team resolution a second time. */
    public static WirelessEnergyContainer getOrCreateContainerByWirelessUUID(UUID wirelessNetworkUUID) {
        if (wirelessNetworkUUID == null || server == null) return null;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld == null ? null : getOrCreateContainer(overworld, wirelessNetworkUUID);
    }

    /**
     * Server-level-aware lookup. The wireless SavedData is always owned by the server
     * overworld, regardless of the dimension containing the requesting machine.
     */
    public static WirelessEnergyContainer getOrCreateContainer(ServerLevel level, UUID wirelessNetworkUUID) {
        if (level == null || wirelessNetworkUUID == null) return null;
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return null;
        WirelessEnergySavaedData data = WirelessEnergySavaedData.getOrCreate(overworld);
        return data.containerMap.computeIfAbsent(wirelessNetworkUUID, WirelessEnergyContainer::new);
    }

    private BigInteger storage;

    private long rate;

    private GlobalPos bindPos;

    private final UUID uuid;

    private final EnergyStat energyStat;

    public WirelessEnergyContainer(UUID uuid, BigInteger storage, long rate, GlobalPos bindPos) {
        this.storage = storage;
        this.rate = rate;
        this.bindPos = bindPos;
        this.uuid = uuid;
        this.energyStat = new EnergyStat(0);
    }

    private WirelessEnergyContainer(UUID uuid) {
        this.uuid = uuid;
        this.storage = BigInteger.ZERO;
        this.rate = Long.MAX_VALUE;
        int currentTick = server == null ? 0 : server.getTickCount();
        this.energyStat = new EnergyStat(currentTick);
    }

    public long addEnergy(long energy, @Nullable MetaMachine machine) {
        long change = energy;
        if (ConfigHolder.INSTANCE.isWirelessRateEnable) change = Math.min(rate, energy);
        if (change <= 0) return 0;
        storage = storage.add(BigInteger.valueOf(change));
        WirelessEnergySavaedData.INSTANCE.setDirty(true);
        if (machine != null) {
            energyStat.update(BigInteger.valueOf(change), server.getTickCount());
        }
        if (machine != null) {
            publishTransfer(machine, new BasicTransferData(uuid, change, machine));
        }
        return change;
    }

    public long removeEnergy(long energy, @Nullable MetaMachine machine) {
        long change = Math.min(BigIntegerUtils.getLongValue(storage), energy);
        if (ConfigHolder.INSTANCE.isWirelessRateEnable) change = Math.min(BigIntegerUtils.getLongValue(storage), Math.min(rate, energy));
        if (change <= 0) return 0;
        storage = storage.subtract(BigInteger.valueOf(change));
        WirelessEnergySavaedData.INSTANCE.setDirty(true);
        if (machine != null) {
            energyStat.update(BigInteger.valueOf(change).negate(), server.getTickCount());
        }
        if (machine != null) {
            publishTransfer(machine, new BasicTransferData(uuid, -change, machine));
        }
        return change;
    }

    public void setStorage(BigInteger energy) {
        storage = energy;
        WirelessEnergySavaedData.INSTANCE.setDirty(true);
    }

    public void setRate(long rate) {
        this.rate = rate;
        WirelessEnergySavaedData.INSTANCE.setDirty(true);
    }

    public void setBindPos(GlobalPos bindPos) {
        this.bindPos = bindPos;
        WirelessEnergySavaedData.INSTANCE.setDirty(true);
    }

    public BigInteger getCapacity() {
        return null;
    }

    /**
     * Single producer entry point. Every server-side wireless transfer updates both
     * the live producer map and the authoritative published snapshot immediately.
     * GUI consumers never clear either map.
     */
    private static void publishTransfer(MetaMachine machine, ITransferData data) {
        if (server == null) return;
        long tick = server.getTickCount();
        synchronized (TRANSFER_DATA) {
            TRANSFER_DATA.put(machine, data);
            TRANSFER_LAST_TICK.put(machine, tick);
        }
        synchronized (TRANSFER_SNAPSHOT) {
            TRANSFER_SNAPSHOT.put(machine, data);
        }
    }

    /**
     * Removes only expired producer entries. This is maintenance performed by the
     * publisher, never by either GUI consumer. A producer remains visible for one
     * second after its most recent transfer, matching the realtime observation
     * window while preventing stale machines from remaining forever.
     */
    public static void expireTransferStatistics(long currentTick) {
        synchronized (TRANSFER_DATA) {
            var it = TRANSFER_LAST_TICK.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (currentTick - entry.getValue() >= 20) {
                    MetaMachine machine = entry.getKey();
                    it.remove();
                    TRANSFER_DATA.remove(machine);
                    synchronized (TRANSFER_SNAPSHOT) {
                        TRANSFER_SNAPSHOT.remove(machine);
                    }
                }
            }
        }
    }
}
