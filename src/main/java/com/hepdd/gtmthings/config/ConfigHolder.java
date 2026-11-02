package com.hepdd.gtmthings.config;

import com.hepdd.gtmthings.GTMThings;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = GTMThings.MOD_ID)
public class ConfigHolder {

    public static com.hepdd.gtmthings.config.ConfigHolder INSTANCE;
    private static final Object LOCK = new Object();

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(com.hepdd.gtmthings.config.ConfigHolder.class, ConfigFormats.yaml()).getConfigInstance();
            }
        }
    }

    @Configurable
    @Configurable.Comment({ "如果启用，则需要使用无线能源绑定工具绑定电池箱或者变电站来提高无线能量传输上限。" })
    public boolean isWirelessRateEnable = true;

    @Configurable
    @Configurable.Comment({
            "If enabled, SimpleTieredMachine's circuit slot only accepts configured Virtual Item Provider cards",
            "(via right-click, or piping one in, gated by an attached Programmable Cover), and physical Programmed",
            "Circuits are blocked from that slot entirely.",
            "If disabled (default), the circuit slot behaves exactly like normal GregTech 8.0.0: physical",
            "Programmed Circuits work as usual and Virtual Item Provider cards are inert on these machines."
    })
    public boolean enableProgrammableCircuit = false;
}
