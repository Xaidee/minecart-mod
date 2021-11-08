package com.alc.moreminecarts.tile_entities;

import com.alc.moreminecarts.MMReferences;
import com.alc.moreminecarts.containers.MinecartUnLoaderContainer;
import com.alc.moreminecarts.entities.ChunkLoaderCartEntity;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.entity.item.minecart.ContainerMinecartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

public class MinecartUnloaderTile extends AbstractCommonLoader implements ITickableTileEntity {

    public MinecartUnloaderTile() {
        super(MMReferences.minecart_unloader_te);
        last_redstone_output = !redstone_output;
    }

    @Override
    public boolean getIsUnloader() {
        return true;
    }

    public Container createMenu(int i, PlayerInventory inventory, PlayerEntity player) {
        this.changed_flag = true;
        return new MinecartUnLoaderContainer(i, level, worldPosition, inventory, player);
    }

    @Override
    protected Container createMenu(int p_213906_1_, PlayerInventory p_213906_2_) {
        return null;
    }

    public void tick() {

        if (!level.isClientSide) {

            if (!isOnCooldown()) {
                List<AbstractMinecartEntity> minecarts = getLoadableMinecartsInRange();
                float criteria_total = 0;
                for (AbstractMinecartEntity minecart : minecarts) {

                    LazyOptional<IFluidHandler> tankCapability = minecart.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
                    LazyOptional<IEnergyStorage> energyCapability = minecart.getCapability(CapabilityEnergy.ENERGY);
                    if (tankCapability.isPresent()) {
                        IFluidHandler fluid_handler = tankCapability.orElse(null);
                        criteria_total += doFluidUnloads(fluid_handler);
                    }
                    else if (energyCapability.isPresent()) {
                        IEnergyStorage energy_storage = energyCapability.orElse(null);
                        criteria_total += doElectricUnloads(energy_storage);
                    }
                    else if (minecart instanceof ContainerMinecartEntity && !(minecart instanceof ChunkLoaderCartEntity)) {
                        criteria_total += doMinecartUnloads((ContainerMinecartEntity) minecart);
                    }

                }

                if (minecarts.size() == 0) criteria_total = 0;
                else criteria_total /= minecarts.size();

                if (comparator_output != ComparatorOutputType.cart_fullness)
                    criteria_total = (float) Math.floor(criteria_total);

                int new_comparator_output_value = (int) (criteria_total * 15);
                if (new_comparator_output_value != comparator_output_value || last_redstone_output != redstone_output) {
                    comparator_output_value = new_comparator_output_value;
                    last_redstone_output = redstone_output;
                    level.updateNeighbourForOutputSignal(getBlockPos(), this.getBlockState().getBlock());
                    level.updateNeighborsAt(getBlockPos(), this.getBlockState().getBlock());
                }

                if (changed_flag) {
                    this.setChanged();
                    changed_flag = false;
                }

            } else {
                decCooldown();
            }

        }
    }

    public float doFluidUnloads(IFluidHandler minecart_handler) {
        boolean changed = false;
        boolean all_empty = true;

        IFluidHandler our_fluid_handler = fluid_handler.orElse(null);
        FluidStack our_fluid_stack = our_fluid_handler.getFluidInTank(0);

        float fluid_content_proportion = 0;
        for (int i = 0; i < minecart_handler.getTanks(); i++) {

            if (minecart_handler.getTankCapacity(i) > 0)
                fluid_content_proportion += (float)minecart_handler.getFluidInTank(i).getAmount() / minecart_handler.getTankCapacity(i);

            if (our_fluid_stack.getAmount() == FLUID_CAPACITY) continue;

            boolean did_load = false;
            FluidStack take_stack = minecart_handler.getFluidInTank(i);

            if (take_stack.isEmpty() || (leave_one_in_stack && take_stack.getAmount() == 1)) continue;
            all_empty = false;

            if (our_fluid_handler.isFluidValid(i, take_stack)) {

                if (our_fluid_stack.isEmpty()) {
                    FluidStack new_stack = take_stack.copy();
                    int transfer_amount = Math.min(1000, new_stack.getAmount());
                    new_stack.setAmount(transfer_amount);
                    our_fluid_handler.fill(new_stack, IFluidHandler.FluidAction.EXECUTE);
                    take_stack.shrink(transfer_amount);
                    did_load = true;
                }
                else if (our_fluid_stack.isFluidEqual(take_stack)) {
                    int true_count = take_stack.getAmount() - (leave_one_in_stack? 1 : 0);
                    int to_fill = our_fluid_handler.getTankCapacity(i) - our_fluid_stack.getAmount();
                    int transfer = Math.min(1000, Math.min(true_count, to_fill));

                    our_fluid_stack.grow(transfer);
                    minecart_handler.drain(transfer, IFluidHandler.FluidAction.EXECUTE);
                    did_load = transfer > 0;
                }
            }

            if (did_load) {
                changed = true;
                break;
            }
        }
        if (minecart_handler.getTanks() > 0) fluid_content_proportion /= minecart_handler.getTanks();

        if (changed) {
            resetCooldown();
            changed_flag = true;
        }

        if (comparator_output == ComparatorOutputType.done_loading) return changed? 0.0f : 1.0f;
        else if (comparator_output == ComparatorOutputType.cart_full) return all_empty? 1.0f : 0.0f;
        else return fluid_content_proportion;

    }

    public float doElectricUnloads(IEnergyStorage minecart_handler) {
        boolean changed = false;

        IEnergyStorage our_handler = energy_handler.orElse(null);

        if (minecart_handler.canReceive()) {

            int true_count = minecart_handler.getEnergyStored() - (leave_one_in_stack? 1 : 0);
            int to_fill = our_handler.getMaxEnergyStored() - our_handler.getEnergyStored();
            int transfer = Math.min(1000, Math.min(true_count, to_fill));

            our_handler.receiveEnergy(transfer, false);
            minecart_handler.extractEnergy(transfer, false);
            changed = transfer > 0;
        }

        if (changed) {
            resetCooldown();
            changed_flag = true;
        }

        if (comparator_output == ComparatorOutputType.done_loading) return changed? 0.0f : 1.0f;
        if (comparator_output == ComparatorOutputType.cart_full) return
                (minecart_handler.getEnergyStored() <= (leave_one_in_stack? 1 : 0))? 1.0f : 0.0f;
        else {
            return (float)minecart_handler.getEnergyStored() / minecart_handler.getMaxEnergyStored();
        }
    }

    public float doMinecartUnloads(ContainerMinecartEntity minecart) {
        boolean changed = false;
        boolean all_empty = true;

        for (int i = 0; i < minecart.getContainerSize(); i++) {

            ItemStack stack = minecart.getItem(i);

            if (stack.isEmpty() || (leave_one_in_stack && stack.getCount() == 1)) continue;
            all_empty = false;

            for (int j = 0; j < this.getContainerSize(); j++) {
                ItemStack add_to_stack = this.getItem(j);

                boolean did_load = false;

                if (add_to_stack.isEmpty()) {
                    int true_count = stack.getCount() - (leave_one_in_stack? 1 : 0);
                    ItemStack new_stack = stack.copy();
                    int transfer_amount = Math.min(8, true_count);
                    new_stack.setCount(transfer_amount);
                    this.setItem(j, new_stack);
                    stack.shrink(transfer_amount);
                    did_load = true;
                }
                else if (canMergeItems(add_to_stack, stack)) {
                    int true_count = stack.getCount() - (leave_one_in_stack? 1 : 0);
                    int to_fill = add_to_stack.getMaxStackSize() - add_to_stack.getCount();
                    int transfer = Math.min(8, Math.min(true_count, to_fill));
                    stack.shrink(transfer);
                    add_to_stack.grow(transfer);
                    did_load = transfer > 0;
                }

                if (did_load) {
                    changed = true;
                    break;
                }

            }
        }

        if (changed) {
            resetCooldown();
            changed_flag = true;
        }

        if (comparator_output == ComparatorOutputType.done_loading) return changed? 0.0f : 1.0f;
        else if (comparator_output == ComparatorOutputType.cart_full) return all_empty? 1.0f : 0.0f;
        else if (minecart instanceof ChunkLoaderCartEntity && comparator_output == ComparatorOutputType.cart_fullness) {
            return ((ChunkLoaderCartEntity)minecart).getComparatorSignal() / 15.0f;
        }
        else {
            return Container.getRedstoneSignalFromContainer(minecart) / 15.0f;
        }
    }

    @Override
    protected ITextComponent getDefaultName() {
        return new StringTextComponent("Minecart Unloader");
    }
}
