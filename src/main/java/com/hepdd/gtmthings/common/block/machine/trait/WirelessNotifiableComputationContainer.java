package com.hepdd.gtmthings.common.block.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WirelessNotifiableComputationContainer extends NotifiableComputationContainer {

    private int currentOutputCwu = 0, lastOutputCwu = 0;

    public WirelessNotifiableComputationContainer(IO handlerIO, boolean transmitter) {
        super(handlerIO, transmitter);
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        var latestTimeStamp = getMachine().getOffsetTimer();
        if (lastTimeStamp < latestTimeStamp) {
            lastOutputCwu = currentOutputCwu;
            currentOutputCwu = 0;
            lastTimeStamp = latestTimeStamp;
        }

        seen.add(this);
        if (handlerIO == IO.IN) {
            if (isTransmitter()) {
                var machine = getMachine();
                if (machine instanceof IOpticalComputationProvider provider) {
                    return provider.requestCWUt(cwut, simulate, seen);
                } else if (machine instanceof MultiblockPartMachine part) {
                    if (!part.isFormed()) {
                        return 0;
                    }
                    for (MultiblockControllerMachine controller : part.getControllers()) {
                        if (controller instanceof IOpticalComputationProvider provider) {
                            return provider.requestCWUt(cwut, simulate, seen);
                        }
                        for (MachineTrait trait : controller.getAllTraits()) {
                            if (trait instanceof IOpticalComputationProvider provider) {
                                return provider.requestCWUt(cwut, simulate, seen);
                            }
                        }
                    }
                    GTCEu.LOGGER.error("WirelessNotifiableComputationContainer could not request CWU/t from controller!");
                    return 0;
                } else {
                    GTCEu.LOGGER.error("WirelessNotifiableComputationContainer could not request CWU/t from machine!");
                    return 0;
                }
            } else {
                IOpticalComputationProvider provider = getOpticalNetProvider();
                if (provider == null) return 0;
                return provider.requestCWUt(cwut, simulate, seen);
            }
        } else {
            lastOutputCwu = lastOutputCwu - cwut;
            return Math.min(lastOutputCwu, cwut);
        }
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (handlerIO == IO.IN) {
            if (isTransmitter()) {
                var machine = getMachine();
                if (machine instanceof IOpticalComputationProvider provider) {
                    return provider.getMaxCWUt(seen);
                } else if (machine instanceof MultiblockPartMachine part) {
                    if (!part.isFormed()) {
                        return 0;
                    }
                    for (MultiblockControllerMachine controller : part.getControllers()) {
                        if (!controller.isFormed()) continue;
                        if (controller instanceof IOpticalComputationProvider provider) {
                            return provider.getMaxCWUt(seen);
                        }
                        for (MachineTrait trait : controller.getAllTraits()) {
                            if (trait instanceof IOpticalComputationProvider provider) {
                                return provider.getMaxCWUt(seen);
                            }
                        }
                    }
                    GTCEu.LOGGER.error("WirelessNotifiableComputationContainer could not get max CWU/t from controller!");
                    return 0;
                } else {
                    GTCEu.LOGGER.error("WirelessNotifiableComputationContainer could not get max CWU/t from machine!");
                    return 0;
                }
            } else {
                IOpticalComputationProvider provider = getOpticalNetProvider();
                if (provider == null) return 0;
                return provider.getMaxCWUt(seen);
            }
        } else {
            return lastOutputCwu;
        }
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (handlerIO == IO.IN) {
            if (isTransmitter()) {
                var machine = getMachine();
                if (machine instanceof IOpticalComputationProvider provider) {
                    return provider.canBridge(seen);
                } else if (machine instanceof MultiblockPartMachine part) {
                    if (!part.isFormed()) {
                        return false;
                    }
                    for (MultiblockControllerMachine controller : part.getControllers()) {
                        if (!controller.isFormed()) continue;
                        if (controller instanceof IOpticalComputationProvider provider) {
                            return provider.canBridge(seen);
                        }
                        for (MachineTrait trait : controller.getAllTraits()) {
                            if (trait instanceof IOpticalComputationProvider provider) {
                                return provider.canBridge(seen);
                            }
                        }
                    }
                    GTCEu.LOGGER.error("WirelessNotifiableComputationContainer could not test bridge status!");
                    return false;
                } else {
                    GTCEu.LOGGER.error("WirelessNotifiableComputationContainer could not test bridge status!");
                    return false;
                }
            } else {
                IOpticalComputationProvider provider = getOpticalNetProvider();
                if (provider == null) return true;
                return provider.canBridge(seen);
            }
        } else {
            return false;
        }
    }

    @Override
    public List<Integer> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<Integer> left, boolean simulate) {
        IOpticalComputationProvider provider = getOpticalNetProvider();
        if (provider == null) return left;

        int sum = left.stream().mapToInt(Integer::intValue).sum();
        if (io == IO.IN) {
            int availableCWUt = requestCWUt(Integer.MAX_VALUE, true);
            if (availableCWUt >= sum) {
                if (recipe != null && recipe.data.getBoolean("duration_is_total_cwu")) {
                    int drawn = provider.requestCWUt(availableCWUt, simulate);
                    if (!simulate) {
                        var machine = getMachine();
                        if (machine instanceof IRecipeLogicMachine rlm) {
                            rlm.getRecipeLogic().setProgressDelta(drawn - 1);
                        } else if (machine instanceof MultiblockPartMachine multiPart) {
                            for (MultiblockControllerMachine controller : multiPart.getControllers()) {
                                if (controller instanceof IRecipeLogicMachine rlm) {
                                    rlm.getRecipeLogic().setProgressDelta(drawn - 1);
                                }
                            }
                        }
                    }
                    sum -= drawn;
                } else {
                    sum -= provider.requestCWUt(sum, simulate);
                }
            }
        } else if (io == IO.OUT) {
            int canInput = this.getMaxCWUt() - this.lastOutputCwu;
            if (!simulate) {
                this.currentOutputCwu = Math.min(canInput, sum);
            }
            sum = sum - canInput;
        }
        return sum <= 0 ? Collections.emptyList() : Collections.singletonList(sum);
    }

    @Nullable
    private IOpticalComputationProvider getOpticalNetProvider() {
        var machine = getMachine();
        if (machine instanceof WirelessOpticalComputationHatchMachine woc && woc.getTransmitterPos() != null) {
            var transmitterMachine = MetaMachine.getMachine(machine.getLevel(), woc.getTransmitterPos());
            if (transmitterMachine instanceof WirelessOpticalComputationHatchMachine transmitter) {
                return transmitter.getComputationContainer();
            }
        }
        return null;
    }
}
