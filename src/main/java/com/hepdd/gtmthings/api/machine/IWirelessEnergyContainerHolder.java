package com.hepdd.gtmthings.api.machine;

import net.minecraft.world.entity.player.Player;

import com.hepdd.gtmthings.api.capability.IBindable;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;

import javax.annotation.Nullable;

public interface IWirelessEnergyContainerHolder extends IBindable {

    void setWirelessEnergyContainerCache(WirelessEnergyContainer container);

    WirelessEnergyContainer getWirelessEnergyContainerCache();

    default boolean autoBindOnPlacement() {
        return false;
    }

    default void bindToPlacingPlayer(Player player) {}

    /**
     * Resolves wireless SavedData only on the logical server. Client GUIs must consume
     * synchronized display data instead of touching server SavedData.
     */
    @Nullable
    default WirelessEnergyContainer getWirelessEnergyContainer() {
        if (getWirelessEnergyContainerCache() != null) return getWirelessEnergyContainerCache();
        if (getUUID() == null) return null;
        WirelessEnergyContainer container = WirelessEnergyContainer.getOrCreateContainerByWirelessUUID(getUUID());
        if (container != null) setWirelessEnergyContainerCache(container);
        return container;
    }
}
