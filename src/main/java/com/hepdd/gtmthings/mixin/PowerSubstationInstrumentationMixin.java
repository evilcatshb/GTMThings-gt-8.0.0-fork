package com.hepdd.gtmthings.mixin;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine.PowerStationEnergyBank;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runtime-only diagnostics for the GTCEu 8.0.0 Power Substation reload/formation bug. */
@Mixin(MultiblockControllerMachine.class)
public abstract class PowerSubstationInstrumentationMixin {

    @Inject(method = "formStructure", at = @At("HEAD"), remap = false)
    private void diag$controllerHead(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine s) {
            log("CONTROLLER.formStructure HEAD name={} pos={} formed={} parts={} pattern={} stored={}",
                    name, s.getBlockPos(), s.isFormed(), s.getParts().size(), s.getPatternState(name).getState(), s.getStored());
        }
    }

    @Inject(method = "formStructure", at = @At("TAIL"), remap = false)
    private void diag$controllerTail(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine s) {
            log("CONTROLLER.formStructure TAIL name={} pos={} formed={} parts={} pattern={} stored={} capacity={}",
                    name, s.getBlockPos(), s.isFormed(), s.getParts().size(), s.getPatternState(name).getState(), s.getStored(), s.getCapacity());
        }
    }

    @Unique
    private static void log(String format, Object... args) {
        com.gregtechceu.gtceu.GTCEu.LOGGER.info("[GTMThings PowerSubstationDiag] " + format, args);
    }
}

@Mixin(WorkableMultiblockMachine.class)
abstract class PowerSubstationWorkableInstrumentationMixin {

    @Inject(method = "formStructure", at = @At("HEAD"), remap = false)
    private void diag$workableHead(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine s) {
            com.gregtechceu.gtceu.GTCEu.LOGGER.info(
                    "[GTMThings PowerSubstationDiag] WORKABLE.formStructure HEAD name={} pos={} formed={} parts={} pattern={} stored={}",
                    name, s.getBlockPos(), s.isFormed(), s.getParts().size(), s.getPatternState(name).getState(), s.getStored());
        }
    }

    @Inject(method = "formStructure", at = @At("TAIL"), remap = false)
    private void diag$workableTail(String name, CallbackInfo ci) {
        if ((Object) this instanceof PowerSubstationMachine s) {
            com.gregtechceu.gtceu.GTCEu.LOGGER.info(
                    "[GTMThings PowerSubstationDiag] WORKABLE.formStructure TAIL name={} pos={} formed={} parts={} pattern={} stored={}",
                    name, s.getBlockPos(), s.isFormed(), s.getParts().size(), s.getPatternState(name).getState(), s.getStored());
        }
    }
}

@Mixin(PowerSubstationMachine.class)
abstract class PowerSubstationMachineInstrumentationMixin {

    @Shadow
    private EnergyContainerList inputHatches;
    @Shadow
    private EnergyContainerList outputHatches;
    @Shadow
    private PowerStationEnergyBank energyBank;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void diag$constructorTail(com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo info, CallbackInfo ci) {
        PowerSubstationMachine s = (PowerSubstationMachine) (Object) this;
        log("SUBSTATION constructor pos={} stored={} capacity={} inputs={} outputs={}",
                s.getBlockPos(), energyBank.getStored(), energyBank.getCapacity(), inputHatches, outputHatches);
    }

    @Inject(method = "formStructure", at = @At("HEAD"), remap = false)
    private void diag$substationFormHead(String name, CallbackInfo ci) {
        PowerSubstationMachine s = (PowerSubstationMachine) (Object) this;
        log("SUBSTATION.formStructure HEAD name={} pos={} formed={} parts={} pattern={} stored={} capacity={} inputs={} outputs={}",
                name, s.getBlockPos(), s.isFormed(), s.getParts().size(), s.getPatternState(name).getState(),
                energyBank.getStored(), energyBank.getCapacity(), inputHatches, outputHatches);
    }

    @Inject(method = "formStructure", at = @At("TAIL"), remap = false)
    private void diag$substationFormTail(String name, CallbackInfo ci) {
        PowerSubstationMachine s = (PowerSubstationMachine) (Object) this;
        log("SUBSTATION.formStructure TAIL name={} pos={} formed={} parts={} pattern={} stored={} capacity={} inputs={} outputs={}",
                name, s.getBlockPos(), s.isFormed(), s.getParts().size(), s.getPatternState(name).getState(),
                energyBank.getStored(), energyBank.getCapacity(), inputHatches, outputHatches);
    }

    @Unique
    private static void log(String format, Object... args) {
        com.gregtechceu.gtceu.GTCEu.LOGGER.info("[GTMThings PowerSubstationDiag] " + format, args);
    }
}

@Mixin(PowerStationEnergyBank.class)
abstract class PowerStationEnergyBankInstrumentationMixin {

    @Inject(method = "deserializeNBT", at = @At("TAIL"), remap = false)
    private void diag$deserialize(CompoundTag tag, CallbackInfo ci) {
        PowerStationEnergyBank bank = (PowerStationEnergyBank) (Object) this;
        com.gregtechceu.gtceu.GTCEu.LOGGER.info(
                "[GTMThings PowerSubstationDiag] ENERGYBANK deserializeNBT size={} stored={} capacity={} tag={}",
                tag.getInt("Size"), bank.getStored(), bank.getCapacity(), tag);
    }

    @Inject(method = "serializeNBT", at = @At("HEAD"), remap = false)
    private void diag$serialize(CallbackInfoReturnable<CompoundTag> cir) {
        PowerStationEnergyBank bank = (PowerStationEnergyBank) (Object) this;
        com.gregtechceu.gtceu.GTCEu.LOGGER.info(
                "[GTMThings PowerSubstationDiag] ENERGYBANK serializeNBT stored={} capacity={}",
                bank.getStored(), bank.getCapacity());
    }

    @Inject(method = "rebuild", at = @At("HEAD"), remap = false)
    private void diag$rebuildHead(java.util.List<?> batteries, CallbackInfo ci) {
        PowerStationEnergyBank bank = (PowerStationEnergyBank) (Object) this;
        com.gregtechceu.gtceu.GTCEu.LOGGER.info(
                "[GTMThings PowerSubstationDiag] ENERGYBANK rebuild HEAD batteries={} oldStored={} oldCapacity={}",
                batteries.size(), bank.getStored(), bank.getCapacity());
    }

    @Inject(method = "rebuild", at = @At("TAIL"), remap = false)
    private void diag$rebuildTail(java.util.List<?> batteries, CallbackInfo ci) {
        PowerStationEnergyBank bank = (PowerStationEnergyBank) (Object) this;
        com.gregtechceu.gtceu.GTCEu.LOGGER.info(
                "[GTMThings PowerSubstationDiag] ENERGYBANK rebuild TAIL batteries={} newStored={} newCapacity={}",
                batteries.size(), bank.getStored(), bank.getCapacity());
    }
}
