package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.task;

import com.google.common.collect.Maps;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementList;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.*;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.ErrorCraftingMonitorElement;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.FluidCraftingMonitorElement;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.ItemCraftingMonitorElement;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.FluidCraftingPreviewElement;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.ItemCraftingPreviewElement;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.FluidStorageDisk;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory.FluidStorageDiskFactory;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory.ItemStorageDiskFactory;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CraftingTask implements ICraftingTask {
    private static final String NBT_REQUESTED = "Requested";
    private static final String NBT_QUANTITY = "Quantity";
    private static final String NBT_PATTERN = "Pattern";
    private static final String NBT_TICKS = "Ticks";
    private static final String NBT_ID = "Id";
    private static final String NBT_EXECUTION_STARTED = "ExecutionStarted";
    private static final String NBT_INTERNAL_STORAGE = "InternalStorage";
    private static final String NBT_INTERNAL_FLUID_STORAGE = "InternalFluidStorage";
    private static final String NBT_TO_EXTRACT_INITIAL = "ToExtractInitial";
    private static final String NBT_TO_EXTRACT_INITIAL_FLUIDS = "ToExtractInitialFluids";
    private static final String NBT_CRAFTS = "Crafts";
    private static final String NBT_MISSING = "Missing";
    private static final String NBT_MISSING_FLUIDS = "MissingFluids";
    private static final String NBT_TOTAL_STEPS = "TotalSteps";

    private static final String NBT_PATTERN_STACK = "Stack";
    private static final String NBT_PATTERN_CONTAINER_POS = "ContainerPos";

    private static final int DEFAULT_EXTRACT_FLAGS = IComparer.COMPARE_NBT;

    private static final Logger LOGGER = LogManager.getLogger(CraftingTask.class);

    private INetwork network;
    private ICraftingRequestInfo requested;
    private int quantity;
    private ICraftingPattern pattern;
    private UUID id = UUID.randomUUID();
    private int ticks;
    private long calculationStarted = -1;
    private long executionStarted = -1;
    private int totalSteps;
    private Set<ICraftingPattern> patternsUsed = new HashSet<>();

    private IStorageDisk<ItemStack> internalStorage;
    private IStorageDisk<FluidStack> internalFluidStorage;

    private IStackList<ItemStack> toExtractInitial = API.instance().createItemStackList();
    private IStackList<FluidStack> toExtractInitialFluids = API.instance().createFluidStackList();

    private List<Craft> crafts = new ArrayList<>();
    private List<Craft> toRemove = new ArrayList<>();
    private Map<ICraftingPatternContainer, Integer> counter = Maps.newHashMap();

    private IStackList<ItemStack> missing = API.instance().createItemStackList();
    private IStackList<FluidStack> missingFluids = API.instance().createFluidStackList();

    private IStackList<ItemStack> toTake = API.instance().createItemStackList();
    private IStackList<FluidStack> toTakeFluids = API.instance().createFluidStackList();

    private IStackList<ItemStack> toCraft = API.instance().createItemStackList();
    private IStackList<FluidStack> toCraftFluids = API.instance().createFluidStackList();

    public CraftingTask(INetwork network, ICraftingRequestInfo requested, int quantity, ICraftingPattern pattern) {
        this.network = network;
        this.requested = requested;
        this.quantity = quantity;
        this.pattern = pattern;

        this.internalStorage = new ItemStorageDisk(null, -1);
        this.internalFluidStorage = new FluidStorageDisk(null, -1);
    }

    public CraftingTask(INetwork network, CompoundNBT tag) throws CraftingTaskReadException {
        this.network = network;

        this.requested = API.instance().createCraftingRequestInfo(tag.getCompound(NBT_REQUESTED));
        this.quantity = tag.getInt(NBT_QUANTITY);
        this.pattern = readPatternFromNbt(tag.getCompound(NBT_PATTERN), network.getWorld());
        this.ticks = tag.getInt(NBT_TICKS);
        this.id = tag.getUniqueId(NBT_ID);
        this.executionStarted = tag.getLong(NBT_EXECUTION_STARTED);

        if (tag.contains(NBT_TOTAL_STEPS)) {
            this.totalSteps = tag.getInt(NBT_TOTAL_STEPS);
        }

        ItemStorageDiskFactory factoryItem = new ItemStorageDiskFactory();
        FluidStorageDiskFactory factoryFluid = new FluidStorageDiskFactory();

        this.internalStorage = factoryItem.createFromNbt(null, tag.getCompound(NBT_INTERNAL_STORAGE));
        this.internalFluidStorage = factoryFluid.createFromNbt(null, tag.getCompound(NBT_INTERNAL_FLUID_STORAGE));

        this.toExtractInitial = readItemStackList(tag.getList(NBT_TO_EXTRACT_INITIAL, Constants.NBT.TAG_COMPOUND));
        this.toExtractInitialFluids = readFluidStackList(tag.getList(NBT_TO_EXTRACT_INITIAL_FLUIDS, Constants.NBT.TAG_COMPOUND));

        ListNBT craftList = tag.getList(NBT_CRAFTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < craftList.size(); ++i) {
            crafts.add(Craft.createCraftFromNBT(network, craftList.getCompound(i)));
        }


        this.missing = readItemStackList(tag.getList(NBT_MISSING, Constants.NBT.TAG_COMPOUND));
        this.missingFluids = readFluidStackList(tag.getList(NBT_MISSING_FLUIDS, Constants.NBT.TAG_COMPOUND));
    }

    @Override
    public CompoundNBT writeToNbt(CompoundNBT tag) {
        tag.put(NBT_REQUESTED, requested.writeToNbt());
        tag.putInt(NBT_QUANTITY, quantity);
        tag.put(NBT_PATTERN, writePatternToNbt(pattern));
        tag.putInt(NBT_TICKS, ticks);
        tag.putUniqueId(NBT_ID, id);
        tag.putLong(NBT_EXECUTION_STARTED, executionStarted);
        tag.put(NBT_INTERNAL_STORAGE, internalStorage.writeToNbt());
        tag.put(NBT_INTERNAL_FLUID_STORAGE, internalFluidStorage.writeToNbt());
        tag.put(NBT_TO_EXTRACT_INITIAL, writeItemStackList(toExtractInitial));
        tag.put(NBT_TO_EXTRACT_INITIAL_FLUIDS, writeFluidStackList(toExtractInitialFluids));
        tag.putInt(NBT_TOTAL_STEPS, totalSteps);

        ListNBT craftingList = new ListNBT();
        for (Craft craft : this.crafts) {
            craftingList.add(craft.writeToNbt());
        }
        tag.put(NBT_CRAFTS, craftingList);


        tag.put(NBT_MISSING, writeItemStackList(missing));
        tag.put(NBT_MISSING_FLUIDS, writeFluidStackList(missingFluids));

        return tag;
    }

    static ListNBT writeItemStackList(IStackList<ItemStack> stacks) {
        ListNBT list = new ListNBT();

        for (StackListEntry<ItemStack> entry : stacks.getStacks()) {
            list.add(StackUtils.serializeStackToNbt(entry.getStack()));
        }

        return list;
    }

    static IStackList<ItemStack> readItemStackList(ListNBT list) throws CraftingTaskReadException {
        IStackList<ItemStack> stacks = API.instance().createItemStackList();

        for (int i = 0; i < list.size(); ++i) {
            ItemStack stack = StackUtils.deserializeStackFromNbt(list.getCompound(i));

            if (stack.isEmpty()) {
                throw new CraftingTaskReadException("Empty stack!");
            }

            stacks.add(stack);
        }

        return stacks;
    }

    static ListNBT writeFluidStackList(IStackList<FluidStack> stacks) {
        ListNBT list = new ListNBT();

        for (StackListEntry<FluidStack> entry : stacks.getStacks()) {
            list.add(entry.getStack().writeToNBT(new CompoundNBT()));
        }

        return list;
    }

    static IStackList<FluidStack> readFluidStackList(ListNBT list) throws CraftingTaskReadException {
        IStackList<FluidStack> stacks = API.instance().createFluidStackList();

        for (int i = 0; i < list.size(); ++i) {
            FluidStack stack = FluidStack.loadFluidStackFromNBT(list.getCompound(i));

            if (stack.isEmpty()) {
                throw new CraftingTaskReadException("Empty stack!");
            }

            stacks.add(stack);
        }

        return stacks;
    }

    @Override
    @Nullable
    public ICraftingTaskError calculate() {
        if (calculationStarted != -1) {
            throw new IllegalStateException("Task already calculated!");
        }

        if (executionStarted != -1) {
            throw new IllegalStateException("Task already started!");
        }

        this.calculationStarted = System.currentTimeMillis();

        IStackList<ItemStack> results = API.instance().createItemStackList();
        IStackList<FluidStack> fluidResults = API.instance().createFluidStackList();

        IStackList<ItemStack> storage = network.getItemStorageCache().getList().copy();
        IStackList<FluidStack> fluidStorage = network.getFluidStorageCache().getList().copy();

        int qtyPerCraft = getQuantityPerCraft(requested.getItem(), requested.getFluid(), this.pattern);
        int qty = ((this.quantity - 1) / qtyPerCraft) + 1; //CeilDiv


        ICraftingTaskError result = calculateInternal(qty, storage, fluidStorage, results, fluidResults, this.pattern, true);

        if (result != null) {
            return result;
        }


        if (requested.getItem() != null) {
            this.toCraft.add(requested.getItem(), qty * qtyPerCraft);
        } else {
            this.toCraftFluids.add(requested.getFluid(), qty * qtyPerCraft);
        }

        return null;
    }

    static class PossibleInputs {
        private List<ItemStack> possibilities;
        private int pos;

        PossibleInputs(List<ItemStack> possibilities) {
            this.possibilities = possibilities;
        }

        ItemStack get() {
            return possibilities.get(pos);
        }

        // Return false if we're exhausted.
        boolean cycle() {
            if (pos + 1 >= possibilities.size()) {
                pos = 0;

                return false;
            }

            pos++;

            return true;
        }

        void sort(IStackList<ItemStack> mutatedStorage, IStackList<ItemStack> results) {
            possibilities.sort((a, b) -> {
                ItemStack ar = mutatedStorage.get(a);
                ItemStack br = mutatedStorage.get(b);

                return (br == null ? 0 : br.getCount()) - (ar == null ? 0 : ar.getCount());
            });

            possibilities.sort((a, b) -> {
                ItemStack ar = results.get(a);
                ItemStack br = results.get(b);

                return (br == null ? 0 : br.getCount()) - (ar == null ? 0 : ar.getCount());
            });
        }
    }

    static class PossibleFluidInputs {
        private List<FluidStack> possibilities;
        private int pos;

        PossibleFluidInputs(List<FluidStack> possibilities) {
            this.possibilities = possibilities;
        }

        FluidStack get() {
            return possibilities.get(pos);
        }

        // Return false if we're exhausted.
        boolean cycle() {
            if (pos + 1 >= possibilities.size()) {
                pos = 0;

                return false;
            }

            pos++;

            return true;
        }

        void sort(IStackList<FluidStack> mutatedStorage, IStackList<FluidStack> results) {
            possibilities.sort((a, b) -> {
                FluidStack ar = mutatedStorage.get(a);
                FluidStack br = mutatedStorage.get(b);

                return (br == null ? 0 : br.getAmount()) - (ar == null ? 0 : ar.getAmount());
            });

            possibilities.sort((a, b) -> {
                FluidStack ar = results.get(a);
                FluidStack br = results.get(b);

                return (br == null ? 0 : br.getAmount()) - (ar == null ? 0 : ar.getAmount());
            });
        }
    }

    @Nullable
    private ICraftingTaskError calculateInternal(
            int qty,
            IStackList<ItemStack> mutatedStorage,
            IStackList<FluidStack> mutatedFluidStorage,
            IStackList<ItemStack> results,
            IStackList<FluidStack> fluidResults,
            ICraftingPattern pattern,
            boolean root) {

        if (System.currentTimeMillis() - calculationStarted > RS.SERVER_CONFIG.getAutocrafting().getCalculationTimeoutMs()) {
            return new CraftingTaskError(CraftingTaskErrorType.TOO_COMPLEX);
        }

        if (!patternsUsed.add(pattern)) {
            return new CraftingTaskError(CraftingTaskErrorType.RECURSIVE, pattern);
        }

        IStackList<ItemStack> itemsToExtract = API.instance().createItemStackList();
        IStackList<FluidStack> fluidsToExtract = API.instance().createFluidStackList();

        NonNullList<ItemStack> took = NonNullList.create();

        for (NonNullList<ItemStack> inputs : pattern.getInputs()) {
            if (inputs.isEmpty()) {
                took.add(ItemStack.EMPTY);

                continue;
            }

            PossibleInputs possibleInputs = new PossibleInputs(new ArrayList<>(inputs));
            possibleInputs.sort(mutatedStorage, results);

            ItemStack possibleInput = possibleInputs.get();

            ItemStack fromSelf = results.get(possibleInput);
            ItemStack fromNetwork = mutatedStorage.get(possibleInput);

            took.add(possibleInput);

            int remaining = possibleInput.getCount() * qty;

            if (remaining < 0) { //int overflow
                return new CraftingTaskError(CraftingTaskErrorType.TOO_COMPLEX);
            }

            while (remaining > 0) {
                if (fromSelf != null) {
                    int toTake = Math.min(remaining, fromSelf.getCount());

                    itemsToExtract.add(possibleInput, toTake);

                    results.remove(fromSelf, toTake);

                    remaining -= toTake;

                    fromSelf = results.get(possibleInput);
                }
                if (fromNetwork != null && remaining > 0) {
                    int toTake = Math.min(remaining, fromNetwork.getCount());

                    this.toTake.add(possibleInput, toTake);

                    itemsToExtract.add(possibleInput, toTake);

                    mutatedStorage.remove(fromNetwork, toTake);

                    remaining -= toTake;

                    fromNetwork = mutatedStorage.get(possibleInput);

                    toExtractInitial.add(possibleInput, toTake);
                }
                if (remaining > 0) {
                    ICraftingPattern subPattern = network.getCraftingManager().getPattern(possibleInput);

                    if (subPattern != null) {
                        int qtyPerCraft = getQuantityPerCraft(possibleInput, null, subPattern);
                        int subQty = ((remaining - 1) / qtyPerCraft) + 1; //CeilDiv

                        ICraftingTaskError result = calculateInternal(subQty, mutatedStorage, mutatedFluidStorage, results, fluidResults, subPattern, false);

                        if (result != null) {
                            return result;
                        }

                        fromSelf = results.get(possibleInput);
                        if (fromSelf == null) {
                            throw new IllegalStateException("Recursive calculation didn't yield anything");
                        }

                        fromNetwork = mutatedStorage.get(possibleInput);
                        // fromSelf contains the amount crafted after the loop.
                        this.toCraft.add(fromSelf);


                    } else {
                        if (!possibleInputs.cycle()) {
                            // Give up.
                            possibleInput = possibleInputs.get(); // Revert back to 0.

                            this.missing.add(possibleInput, remaining);

                            itemsToExtract.add(possibleInput, remaining);

                            remaining = 0;
                        } else {
                            // Retry with new input...
                            possibleInput = possibleInputs.get();

                            fromSelf = results.get(possibleInput);
                            fromNetwork = mutatedStorage.get(possibleInput);
                        }
                    }
                }
            }
        }

        for (NonNullList<FluidStack> inputs : pattern.getFluidInputs()) {
            if (inputs.isEmpty()) {
                continue;
            }

            PossibleFluidInputs possibleInputs = new PossibleFluidInputs(new ArrayList<>(inputs));
            possibleInputs.sort(mutatedFluidStorage, fluidResults);

            FluidStack possibleInput = possibleInputs.get();

            FluidStack fromSelf = fluidResults.get(possibleInput, IComparer.COMPARE_NBT);
            FluidStack fromNetwork = mutatedFluidStorage.get(possibleInput, IComparer.COMPARE_NBT);

            int remaining = possibleInput.getAmount() * qty;

            if (remaining < 0) { //int overflow
                return new CraftingTaskError(CraftingTaskErrorType.TOO_COMPLEX);
            }

            while (remaining > 0) {
                if (fromSelf != null) {
                    int toTake = Math.min(remaining, fromSelf.getAmount());

                    fluidsToExtract.add(possibleInput, toTake);

                    fluidResults.remove(possibleInput, toTake);

                    remaining -= toTake;

                    fromSelf = fluidResults.get(possibleInput, IComparer.COMPARE_NBT);
                }
                if (fromNetwork != null && remaining > 0) {

                    int toTake = Math.min(remaining, fromNetwork.getAmount());

                    this.toTakeFluids.add(possibleInput, toTake);

                    fluidsToExtract.add(possibleInput, toTake);

                    mutatedFluidStorage.remove(fromNetwork, toTake);

                    remaining -= toTake;

                    fromNetwork = mutatedFluidStorage.get(possibleInput, IComparer.COMPARE_NBT);

                    toExtractInitialFluids.add(possibleInput);
                }
                if (remaining > 0) {
                    ICraftingPattern subPattern = network.getCraftingManager().getPattern(possibleInput);

                    if (subPattern != null) {
                        int qtyPerCraft = getQuantityPerCraft(null, possibleInput, subPattern);
                        int subQty = ((remaining - 1) / qtyPerCraft) + 1; //CeilDiv

                        ICraftingTaskError result = calculateInternal(subQty, mutatedStorage, mutatedFluidStorage, results, fluidResults, subPattern, false);

                        if (result != null) {
                            return result;
                        }

                        fromSelf = fluidResults.get(possibleInput, IComparer.COMPARE_NBT);
                        if (fromSelf == null) {
                            throw new IllegalStateException("Recursive fluid calculation didn't yield anything");
                        }

                        fromNetwork = mutatedFluidStorage.get(possibleInput, IComparer.COMPARE_NBT);

                        // fromSelf contains the amount crafted after the loop.
                        this.toCraftFluids.add(fromSelf);
                    } else {
                        this.missingFluids.add(possibleInput, remaining);

                        fluidsToExtract.add(possibleInput, remaining);

                        remaining = 0;
                    }
                }
            }
        }

        patternsUsed.remove(pattern);

        if (pattern.isProcessing()) {
            IStackList<ItemStack> itemsToReceive = API.instance().createItemStackList();
            IStackList<FluidStack> fluidsToReceive = API.instance().createFluidStackList();

            for (ItemStack output : pattern.getOutputs()) {
                results.add(output, output.getCount() * qty);

                itemsToReceive.add(output);
            }

            for (FluidStack output : pattern.getFluidOutputs()) {
                fluidResults.add(output, output.getAmount() * qty);

                fluidsToReceive.add(output);
            }

            crafts.add(new Processing(pattern, itemsToReceive, fluidsToReceive, itemsToExtract, fluidsToExtract, root));
        } else {
            if (!fluidsToExtract.isEmpty()) {
                throw new IllegalStateException("Cannot extract fluids in normal pattern!");
            }

            crafts.add(new Crafting(pattern, took, itemsToExtract, root));
            ItemStack output = pattern.getOutput(took);
            results.add(output, output.getCount() * qty);

            for (ItemStack byproduct : pattern.getByproducts(took)) {
                results.add(byproduct, byproduct.getCount() * qty);
            }
        }

        return null;
    }

    private void extractInitial() {
        if (!toExtractInitial.isEmpty()) {
            List<ItemStack> toRemove = new ArrayList<>();

            for (StackListEntry<ItemStack> toExtract : toExtractInitial.getStacks()) {
                ItemStack result = network.extractItem(toExtract.getStack(), toExtract.getStack().getCount(), Action.PERFORM);

                if (!result.isEmpty()) {
                    internalStorage.insert(toExtract.getStack(), result.getCount(), Action.PERFORM);

                    toRemove.add(result);
                }
            }

            for (ItemStack stack : toRemove) {
                toExtractInitial.remove(stack);
            }

            if (!toRemove.isEmpty()) {
                network.getCraftingManager().onTaskChanged();
            }
        }

        if (!toExtractInitialFluids.isEmpty()) {
            List<FluidStack> toRemove = new ArrayList<>();

            for (StackListEntry<FluidStack> toExtract : toExtractInitialFluids.getStacks()) {
                FluidStack result = network.extractFluid(toExtract.getStack(), toExtract.getStack().getAmount(), Action.PERFORM);

                if (!result.isEmpty()) {
                    internalFluidStorage.insert(toExtract.getStack(), result.getAmount(), Action.PERFORM);

                    toRemove.add(result);
                }
            }

            for (FluidStack stack : toRemove) {
                toExtractInitialFluids.remove(stack);
            }

            if (!toRemove.isEmpty()) {
                network.getCraftingManager().onTaskChanged();
            }
        }
    }

    private void updateCrafting(Crafting c) {

        ICraftingPatternContainer container = c.getPattern().getContainer();

        int interval = container.getUpdateInterval();

        if (interval < 0) {
            throw new IllegalStateException(container + " has an update interval of < 0");
        }

        if (interval == 0 || ticks % interval == 0) {
            if (counter.getOrDefault(container, 0) >= container.getMaximumSuccessfulCraftingUpdates()) {
                return;
            }
            IStackList<ItemStack> extractedItems = extractFromInternalItemStorage(c.getItemsToUse().getStacks(), this.internalStorage, Action.SIMULATE);
            if (extractedItems != null) {

                //this uses extractedItems instead of getToExtract because getToExtract will become more expensive in the future
                extractFromInternalItemStorage(extractedItems.getStacks(), this.internalStorage, Action.PERFORM);

                ItemStack output = c.getPattern().getOutput(c.getTook());

                if (!c.isRoot()) {
                    this.internalStorage.insert(output, output.getCount(), Action.PERFORM);
                } else {
                    ItemStack remainder = this.network.insertItem(output, output.getCount(), Action.PERFORM);

                    this.internalStorage.insert(remainder, remainder.getCount(), Action.PERFORM);
                }

                // Byproducts need to always be inserted in the internal storage for later reuse further in the task.
                // Regular outputs can be inserted into the network *IF* it's a root since it's *NOT* expected to be used later on.
                for (ItemStack byp : c.getPattern().getByproducts(c.getTook())) {
                    this.internalStorage.insert(byp, byp.getCount(), Action.PERFORM);
                }

                toRemove.add(c);

                network.getCraftingManager().onTaskChanged();

                counter.merge(container, 1, Integer::sum);
            }
        }
    }

    private void updateProcessing(Processing p) {

        ICraftingPatternContainer container = p.getPattern().getContainer();

        if (p.getState() == ProcessingState.PROCESSED) {
            toRemove.add(p);
            network.getCraftingManager().onTaskChanged();

            return;
        }

        if (p.getState() == ProcessingState.EXTRACTED_ALL) {
            return;
        }

        int interval = container.getUpdateInterval();

        if (interval < 0) {
            throw new IllegalStateException(container + " has an update interval of < 0");
        }

        if (interval == 0 || ticks % interval == 0) {
            if (counter.getOrDefault(container, 0) >= container.getMaximumSuccessfulCraftingUpdates()) {
                return;
            }

            ProcessingState originalState = p.getState();

            if (container.isLocked()) {
                p.setState(ProcessingState.LOCKED);
            } else if (!p.getItemsToUse().isEmpty() && container.getConnectedInventory() == null
                    || !p.getFluidsToPut().isEmpty() && container.getConnectedFluidInventory() == null) {
                p.setState(ProcessingState.MACHINE_NONE);
            } else {

                boolean hasAll = false;
                IStackList<ItemStack> extractedItems;
                IStackList<FluidStack> extractedFluids = null;

                extractedItems = extractFromInternalItemStorage(p.getItemsToUse().getStacks(), this.internalStorage, Action.SIMULATE);
                if (extractedItems != null) {
                    extractedFluids = extractFromInternalFluidStorage(p.getFluidsToPut().getStacks(), this.internalFluidStorage, Action.SIMULATE);
                    if (extractedFluids != null) {
                        hasAll = true;
                    }
                }

                boolean canInsert = false;
                if (hasAll) {
                    canInsert = insertIntoInventory(container.getConnectedInventory(), extractedItems.getStacks(), Action.SIMULATE);
                    if (canInsert) {
                        canInsert = insertIntoTank(container.getConnectedFluidInventory(), extractedFluids.getStacks(), Action.SIMULATE);
                    }
                }

                if (hasAll && !canInsert) {
                    p.setState(ProcessingState.MACHINE_DOES_NOT_ACCEPT);
                }

                if (hasAll && canInsert) {

                    extractFromInternalItemStorage(p.getItemsToUse().getStacks(), this.internalStorage, Action.PERFORM);
                    extractFromInternalFluidStorage(p.getFluidsToPut().getStacks(), this.internalFluidStorage, Action.PERFORM);

                    insertIntoInventory(container.getConnectedInventory(), extractedItems.getStacks(), Action.PERFORM);
                    insertIntoTank(container.getConnectedFluidInventory(), extractedFluids.getStacks(), Action.PERFORM);

                    p.setState(ProcessingState.EXTRACTED_ALL);

                    container.onUsedForProcessing();

                    counter.merge(container, 1, Integer::sum);
                }
            }

            if (originalState != p.getState()) {
                network.getCraftingManager().onTaskChanged();
            }
        }
    }

    private static IStackList<ItemStack> extractFromInternalItemStorage(Collection<StackListEntry<ItemStack>> stacks, IStorageDisk<ItemStack> storage, Action action) {
        IStackList<ItemStack> toReturn = API.instance().createItemStackList();
        for (StackListEntry<ItemStack> entry : stacks) {
            ItemStack result = storage.extract(entry.getStack(), entry.getStack().getCount(), DEFAULT_EXTRACT_FLAGS, action);

            if (result == ItemStack.EMPTY || result.getCount() != entry.getStack().getCount()) {
                if (action == Action.PERFORM) {
                    throw new IllegalStateException("The internal crafting inventory reported that " + entry.getStack() + " was available but we got " + result);
                }
                return null;
            }
            toReturn.add(result);
        }
        return toReturn;
    }

    private static IStackList<FluidStack> extractFromInternalFluidStorage(Collection<StackListEntry<FluidStack>> stacks, IStorageDisk<FluidStack> storage, Action action) {
        IStackList<FluidStack> toReturn = API.instance().createFluidStackList();
        for (StackListEntry<FluidStack> entry : stacks) {
            FluidStack result = storage.extract(entry.getStack(), entry.getStack().getAmount(), IComparer.COMPARE_NBT, action);
            if (result == FluidStack.EMPTY || result.getAmount() != entry.getStack().getAmount()) {
                if (action == Action.PERFORM) {
                    throw new IllegalStateException("The internal crafting inventory reported that " + entry.getStack() + " was available but we got " + result);
                }
                return null;
            }
            toReturn.add(result);
        }
        return toReturn;
    }


    private static boolean insertIntoInventory(@Nullable IItemHandler dest, Collection<StackListEntry<ItemStack>> toInsert, Action action) {
        if (dest == null) {
            return false;
        }
        if (toInsert.isEmpty()) {
            return true;
        }
        Deque<StackListEntry<ItemStack>> stacks = new ArrayDeque<>(toInsert);

        StackListEntry<ItemStack> currentEntry = stacks.poll();

        ItemStack current = currentEntry != null ? currentEntry.getStack() : null;

        List<Integer> availableSlots = IntStream.range(0, dest.getSlots()).boxed().collect(Collectors.toList());

        while (current != null && !availableSlots.isEmpty()) {
            ItemStack remainder = ItemStack.EMPTY;

            for (int i = 0; i < availableSlots.size(); ++i) {
                int slot = availableSlots.get(i);

                // .copy() is mandatory!
                remainder = dest.insertItem(slot, current.copy(), action == Action.SIMULATE);

                // If we inserted *something*
                if (remainder.isEmpty() || current.getCount() != remainder.getCount()) {
                    availableSlots.remove(i);
                    break;
                }
            }

            if (remainder.isEmpty()) { // If we inserted successfully, get a next stack.
                currentEntry = stacks.poll();

                current = currentEntry != null ? currentEntry.getStack() : null;
            } else if (current.getCount() == remainder.getCount()) { // If we didn't insert anything over ALL these slots, stop here.
                break;
            } else { // If we didn't insert all, continue with other slots and use our remainder.
                current = remainder;
            }
        }

        boolean success = current == null && stacks.isEmpty();
        if (!success && action == Action.PERFORM) {
            LOGGER.warn("Item Handler unexpectedly didn't accept " + (current != null ? current.getTranslationKey() : null) + ", the remainder has been voided!");
        }
        return success;
    }

    private static boolean insertIntoTank(IFluidHandler dest, Collection<StackListEntry<FluidStack>> toInsert, Action action) {
        for (StackListEntry<FluidStack> entry : toInsert) {
            int filled = dest.fill(entry.getStack(), action == Action.SIMULATE ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (filled != entry.getStack().getAmount()) {
                if (action == Action.PERFORM) {
                    LOGGER.warn("Fluid Handler unexpectedly didn't accept all of " + entry.getStack().getTranslationKey() + ", the remainder has been voided!");
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public int getCompletionPercentage() {
        if (totalSteps == 0) {
            return 0;
        }

        //TODO return 100 - (int) (((float) (crafting.size() + processing.size()) / (float) totalSteps) * 100F);
        return 0;
    }

    @Override
    public boolean update() {
        if (hasMissing()) {
            LOGGER.warn("Crafting task with missing items or fluids cannot execute, cancelling...");

            return true;
        }

        if (executionStarted == -1) {
            executionStarted = System.currentTimeMillis();

            //TODO   totalSteps = crafting.size() + processing.size();
        }

        ++ticks;

        extractInitial();

        if (this.crafts.isEmpty()) {
            List<Runnable> toPerform = new ArrayList<>();

            for (ItemStack stack : internalStorage.getStacks()) {
                ItemStack remainder = network.insertItem(stack, stack.getCount(), Action.PERFORM);

                toPerform.add(() -> internalStorage.extract(stack, stack.getCount() - remainder.getCount(), IComparer.COMPARE_NBT, Action.PERFORM));
            }

            for (FluidStack stack : internalFluidStorage.getStacks()) {
                FluidStack remainder = network.insertFluid(stack, stack.getAmount(), Action.PERFORM);

                toPerform.add(() -> internalFluidStorage.extract(stack, stack.getAmount() - remainder.getAmount(), IComparer.COMPARE_NBT, Action.PERFORM));
            }

            // Prevent CME.
            toPerform.forEach(Runnable::run);

            return internalStorage.getStacks().isEmpty() && internalFluidStorage.getStacks().isEmpty();
        } else {
            for (Craft craft : crafts) {
                if (craft instanceof Crafting) {
                    updateCrafting((Crafting) craft);
                } else {
                    updateProcessing((Processing) craft);
                }
            }

            counter.clear();

            for (Craft craft : toRemove) {
                crafts.remove(craft);
            }

            return false;
        }
    }

    @Override
    public void onCancelled() {
        for (ItemStack remainder : internalStorage.getStacks()) {
            network.insertItem(remainder, remainder.getCount(), Action.PERFORM);
        }

        for (FluidStack remainder : internalFluidStorage.getStacks()) {
            network.insertFluid(remainder, remainder.getAmount(), Action.PERFORM);
        }
    }

    @Override
    public int getQuantity() {
        return quantity;
    }

    @Override
    public int getQuantityPerCraft(ItemStack item, FluidStack fluid, ICraftingPattern pattern) {
        int qty = 0;

        if (item != null) {
            for (ItemStack output : pattern.getOutputs()) {
                if (API.instance().getComparer().isEqualNoQuantity(output, item)) {
                    qty += output.getCount();

                    if (!pattern.isProcessing()) {
                        break;
                    }
                }
            }
        } else {
            for (FluidStack output : pattern.getFluidOutputs()) {
                if (API.instance().getComparer().isEqual(output, fluid, IComparer.COMPARE_NBT)) {
                    qty += output.getAmount();
                }
            }
        }

        return qty;
    }

    @Override
    public ICraftingRequestInfo getRequested() {
        return requested;
    }

    @Override
    public int onTrackedInsert(ItemStack stack, int size) {
        for (Craft craft : this.crafts) {
            if (craft instanceof Processing) {
                Processing p = (Processing) craft;

                if (p.getState() != ProcessingState.EXTRACTED_ALL) {
                    continue;
                }

                ItemStack content = p.getItemsToReceive().get(stack);

                if (content != null) {
                    int needed = content.getCount();

                    if (needed > size) {
                        needed = size;
                    }

                    p.getItemsToReceive().remove(stack, needed);

                    size -= needed;

                    if (p.getItemsToReceive().isEmpty() && p.getFluidsToReceive().isEmpty()) {
                        p.setState(ProcessingState.PROCESSED);
                    }

                    if (!p.isRoot()) {
                        internalStorage.insert(stack, needed, Action.PERFORM);
                    } else {
                        ItemStack remainder = network.insertItem(stack, needed, Action.PERFORM);

                        internalStorage.insert(remainder, remainder.getCount(), Action.PERFORM);
                    }

                    if (size == 0) {
                        return 0;
                    }
                }
            }
        }

        return size;
    }

    @Override
    public int onTrackedInsert(FluidStack stack, int size) {
        for (Craft craft : this.crafts) {
            if (craft instanceof Processing) {
                Processing p = (Processing) craft;

                if (p.getState() != ProcessingState.EXTRACTED_ALL) {
                    continue;
                }

                FluidStack content = p.getFluidsToReceive().get(stack);

                if (content != null) {
                    int needed = content.getAmount();

                    if (needed > size) {
                        needed = size;
                    }

                    p.getFluidsToReceive().remove(stack, needed);

                    size -= needed;

                    if (p.getItemsToReceive().isEmpty() && p.getFluidsToReceive().isEmpty()) {
                        p.setState(ProcessingState.PROCESSED);
                    }

                    if (!p.isRoot()) {
                        internalFluidStorage.insert(stack, needed, Action.PERFORM);
                    } else {
                        FluidStack remainder = network.insertFluid(stack, needed, Action.PERFORM);

                        internalFluidStorage.insert(remainder, remainder.getAmount(), Action.PERFORM);
                    }

                    if (size == 0) {
                        return 0;
                    }
                }
            }
        }

        return size;
    }

    static CompoundNBT writePatternToNbt(ICraftingPattern pattern) {
        CompoundNBT tag = new CompoundNBT();

        tag.put(NBT_PATTERN_STACK, pattern.getStack().serializeNBT());
        tag.putLong(NBT_PATTERN_CONTAINER_POS, pattern.getContainer().getPosition().toLong());

        return tag;
    }

    static ICraftingPattern readPatternFromNbt(CompoundNBT tag, World world) throws CraftingTaskReadException {
        BlockPos containerPos = BlockPos.fromLong(tag.getLong(NBT_PATTERN_CONTAINER_POS));

        INetworkNode node = API.instance().getNetworkNodeManager((ServerWorld) world).getNode(containerPos);

        if (node instanceof ICraftingPatternContainer) {
            ItemStack stack = ItemStack.read(tag.getCompound(NBT_PATTERN_STACK));

            if (stack.getItem() instanceof ICraftingPatternProvider) {
                return ((ICraftingPatternProvider) stack.getItem()).create(world, stack, (ICraftingPatternContainer) node);
            } else {
                throw new CraftingTaskReadException("Pattern stack is not a crafting pattern provider");
            }
        } else {
            throw new CraftingTaskReadException("Crafting pattern container doesn't exist anymore");
        }
    }

    @Override
    public List<ICraftingMonitorElement> getCraftingMonitorElements() {
        ICraftingMonitorElementList elements = API.instance().createCraftingMonitorElementList();

        for (ItemStack stack : this.internalStorage.getStacks()) {
            elements.add(new ItemCraftingMonitorElement(stack, stack.getCount(), 0, 0, 0, 0));
        }

        for (StackListEntry<ItemStack> missing : this.missing.getStacks()) {
            elements.add(new ItemCraftingMonitorElement(missing.getStack(), 0, missing.getStack().getCount(), 0, 0, 0));
        }


        for (Craft craft : this.crafts) {
            if (craft instanceof Crafting) {
                Crafting crafting = (Crafting) craft;
                for (ItemStack receive : crafting.getPattern().getOutputs()) {
                    elements.add(new ItemCraftingMonitorElement(receive, 0, 0, 0, 0, receive.getCount()));
                }
            } else {
                Processing processing = (Processing) craft;
                if (processing.getState() == ProcessingState.PROCESSED) {
                    continue;
                }

                if (processing.getState() == ProcessingState.EXTRACTED_ALL) {
                    for (StackListEntry<ItemStack> put : processing.getItemsToUse().getStacks()) {
                        elements.add(new ItemCraftingMonitorElement(put.getStack(), 0, 0, put.getStack().getCount(), 0, 0));
                    }
                } else if (processing.getState() == ProcessingState.READY || processing.getState() == ProcessingState.MACHINE_DOES_NOT_ACCEPT || processing.getState() == ProcessingState.MACHINE_NONE || processing.getState() == ProcessingState.LOCKED) {
                    for (StackListEntry<ItemStack> receive : processing.getItemsToReceive().getStacks()) {
                        ICraftingMonitorElement element = new ItemCraftingMonitorElement(receive.getStack(), 0, 0, 0, receive.getStack().getCount(), 0);

                        if (processing.getState() == ProcessingState.MACHINE_DOES_NOT_ACCEPT) {
                            element = new ErrorCraftingMonitorElement(element, "gui.refinedstorage.crafting_monitor.machine_does_not_accept_item");
                        } else if (processing.getState() == ProcessingState.MACHINE_NONE) {
                            element = new ErrorCraftingMonitorElement(element, "gui.refinedstorage.crafting_monitor.machine_none");
                        } else if (processing.getState() == ProcessingState.LOCKED) {
                            element = new ErrorCraftingMonitorElement(element, "gui.refinedstorage.crafting_monitor.crafter_is_locked");
                        }

                        elements.add(element);
                    }
                }
            }
        }

        elements.commit();

        for (FluidStack stack : this.internalFluidStorage.getStacks()) {
            elements.add(new FluidCraftingMonitorElement(stack, stack.getAmount(), 0, 0, 0, 0));
        }

        for (StackListEntry<FluidStack> missing : this.missingFluids.getStacks()) {
            elements.add(new FluidCraftingMonitorElement(missing.getStack(), 0, missing.getStack().getAmount(), 0, 0, 0));
        }

        for (Craft craft : this.crafts) {
            if (craft instanceof Processing) {
                Processing processing = (Processing) craft;
                if (processing.getState() == ProcessingState.PROCESSED) {
                    continue;
                }

                if (processing.getState() == ProcessingState.EXTRACTED_ALL) {
                    for (StackListEntry<FluidStack> put : processing.getFluidsToPut().getStacks()) {
                        elements.add(new FluidCraftingMonitorElement(put.getStack(), 0, 0, put.getStack().getAmount(), 0, 0));
                    }
                } else if (processing.getState() == ProcessingState.READY || processing.getState() == ProcessingState.MACHINE_DOES_NOT_ACCEPT || processing.getState() == ProcessingState.MACHINE_NONE) {
                    for (StackListEntry<FluidStack> receive : processing.getFluidsToReceive().getStacks()) {
                        ICraftingMonitorElement element = new FluidCraftingMonitorElement(receive.getStack(), 0, 0, 0, receive.getStack().getAmount(), 0);

                        if (processing.getState() == ProcessingState.MACHINE_DOES_NOT_ACCEPT) {
                            element = new ErrorCraftingMonitorElement(element, "gui.refinedstorage.crafting_monitor.machine_does_not_accept_fluid");
                        } else if (processing.getState() == ProcessingState.MACHINE_NONE) {
                            element = new ErrorCraftingMonitorElement(element, "gui.refinedstorage.crafting_monitor.machine_none");
                        } else if (processing.getState() == ProcessingState.LOCKED) {
                            element = new ErrorCraftingMonitorElement(element, "gui.refinedstorage.crafting_monitor.crafter_is_locked");
                        }

                        elements.add(element);
                    }
                }
            }
        }

        elements.commit();

        return elements.getElements();
    }

    @Override
    public List<ICraftingPreviewElement> getPreviewStacks() {
        Map<Integer, ItemCraftingPreviewElement> map = new LinkedHashMap<>();
        Map<Integer, FluidCraftingPreviewElement> mapFluids = new LinkedHashMap<>();

        for (StackListEntry<ItemStack> stack : toCraft.getStacks()) {
            int hash = API.instance().getItemStackHashCode(stack.getStack());

            ItemCraftingPreviewElement previewStack = map.get(hash);

            if (previewStack == null) {
                previewStack = new ItemCraftingPreviewElement(stack.getStack());
            }

            previewStack.addToCraft(stack.getStack().getCount());

            map.put(hash, previewStack);
        }

        for (StackListEntry<FluidStack> stack : toCraftFluids.getStacks()) {
            int hash = API.instance().getFluidStackHashCode(stack.getStack());

            FluidCraftingPreviewElement previewStack = mapFluids.get(hash);

            if (previewStack == null) {
                previewStack = new FluidCraftingPreviewElement(stack.getStack());
            }

            previewStack.addToCraft(stack.getStack().getAmount());

            mapFluids.put(hash, previewStack);
        }

        for (StackListEntry<ItemStack> stack : missing.getStacks()) {
            int hash = API.instance().getItemStackHashCode(stack.getStack());

            ItemCraftingPreviewElement previewStack = map.get(hash);

            if (previewStack == null) {
                previewStack = new ItemCraftingPreviewElement(stack.getStack());
            }

            previewStack.setMissing(true);
            previewStack.addToCraft(stack.getStack().getCount());

            map.put(hash, previewStack);
        }

        for (StackListEntry<FluidStack> stack : missingFluids.getStacks()) {
            int hash = API.instance().getFluidStackHashCode(stack.getStack());

            FluidCraftingPreviewElement previewStack = mapFluids.get(hash);

            if (previewStack == null) {
                previewStack = new FluidCraftingPreviewElement(stack.getStack());
            }

            previewStack.setMissing(true);
            previewStack.addToCraft(stack.getStack().getAmount());

            mapFluids.put(hash, previewStack);
        }

        for (StackListEntry<ItemStack> stack : toTake.getStacks()) {
            int hash = API.instance().getItemStackHashCode(stack.getStack());

            ItemCraftingPreviewElement previewStack = map.get(hash);

            if (previewStack == null) {
                previewStack = new ItemCraftingPreviewElement(stack.getStack());
            }

            previewStack.addAvailable(stack.getStack().getCount());

            map.put(hash, previewStack);
        }

        for (StackListEntry<FluidStack> stack : toTakeFluids.getStacks()) {
            int hash = API.instance().getFluidStackHashCode(stack.getStack());

            FluidCraftingPreviewElement previewStack = mapFluids.get(hash);

            if (previewStack == null) {
                previewStack = new FluidCraftingPreviewElement(stack.getStack());
            }

            previewStack.addAvailable(stack.getStack().getAmount());

            mapFluids.put(hash, previewStack);
        }

        List<ICraftingPreviewElement> elements = new ArrayList<>();

        elements.addAll(map.values());
        elements.addAll(mapFluids.values());

        return elements;
    }

    @Override
    public ICraftingPattern getPattern() {
        return pattern;
    }

    @Override
    public long getExecutionStarted() {
        return executionStarted;
    }

    @Override
    public IStackList<ItemStack> getMissing() {
        return missing;
    }

    @Override
    public IStackList<FluidStack> getMissingFluids() {
        return missingFluids;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
