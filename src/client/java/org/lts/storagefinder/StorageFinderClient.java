package org.lts.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.lts.storagefinder.gui.StorageSearchScreen;
import org.lts.storagefinder.tooltip.ClientContainerPreviewTooltip;
import org.lts.storagefinder.tooltip.ContainerPreviewTooltip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class StorageFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "storagefinder";
    public static final ModLog LOGGER = ModLog.root();
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static final SearchSelection SELECTION = new SearchSelection();
    private static final StorageIndex INDEX = new StorageIndex();
    private static final StorageLocator LOCATOR = new StorageLocator();
    private static final long PENDING_STORAGE_TIMEOUT_MILLIS = 5_000L;
    private static final int SINGLE_CONTAINER_SLOTS = 27;
    private static final int DOUBLE_CHEST_SLOTS = 54;

    private static BlockPos pendingStorage;
    private static long pendingStorageAt;
    private static ClientLevel pendingStorageLevel;
    private static AbstractContainerMenu activeStorageMenu;
    private static int activeStorageSlots;
    private static BlockPos activeStorage;
    private static String activeScope;
    private static String activeFamily;
    private static String activeEnderScope;
    private static int activeFingerprint;
    private static boolean activeContentsChanged;
    private static Map<String, Integer> completionBaseline = Map.of();
    private static Set<Integer> completionColors = Set.of();
    private static ClientLevel lastLevel;
    private static KeyMapping searchKey;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        StorageFinderConfig.load();
        INDEX.load();
        EconomyPriceBridge.initialize();
        searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.storagefinder.search",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RALT,
                KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(StorageFinderClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(StorageFinderClient::onClientStopping);
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> StorageRenderer.render(LOCATOR));
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "active_searches"),
                (graphics, frame) -> StorageHudRenderer.render(Minecraft.getInstance(), graphics));
        ClientTooltipComponentCallback.EVENT.register(component ->
                component instanceof ContainerPreviewTooltip preview
                        ? new ClientContainerPreviewTooltip(preview) : null);
        LOGGER.ready();
    }

    public static void onStorageInteracted(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        StorageFinderConfig config = StorageFinderConfig.current();
        if (config.enabled && config.indexContainers && StorageLocator.isStorage(minecraft, pos)) {
            pendingStorage = pos.immutable();
            pendingStorageAt = System.currentTimeMillis();
            pendingStorageLevel = minecraft.level;
        } else {
            clearPendingStorage();
        }
    }

    private static void onTick(Minecraft minecraft) {
        StorageFinderConfig config = StorageFinderConfig.loadIfChanged();
        if (minecraft.level != lastLevel) {
            saveAndClearActiveContainer(minecraft);
            clearPendingStorage();
            LOCATOR.clear();
            EconomyPriceBridge.clearCache();
            if (lastLevel != null && minecraft.level == null) {
                WorldScopeTracker.clear();
            }
            lastLevel = minecraft.level;
        }
        handleSearchKey(minecraft);
        if (config.enabled && config.indexContainers) {
            observeContainer(minecraft);
        } else if (pendingStorage != null || activeStorageMenu != null) {
            clearPendingStorage();
            saveAndClearActiveContainer(minecraft);
        }
        INDEX.tick();
        if (INDEX.consumeSaveFailureNotice() && minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable("storagefinder.index.save_failed"));
        }

        ticks++;
        if (ticks % 10 == 0) {
            if (config.enabled) {
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            } else {
                LOCATOR.clear();
            }
        }
        if (config.enabled) {
            LOCATOR.tickRoutes(minecraft);
        }
    }

    private static void handleSearchKey(Minecraft minecraft) {
        StorageFinderConfig config = StorageFinderConfig.current();
        while (searchKey.consumeClick()) {
            if (!config.enabled || MinecraftScreenAccess.getScreen(minecraft) != null || minecraft.player == null) {
                continue;
            }
            ItemStack held = minecraft.player.getMainHandItem();
            if (held.isEmpty()) {
                minecraft.setScreenAndShow(new StorageSearchScreen());
            } else {
                Component displayName = SearchSelection.displayName(held);
                SearchSelection.Change change = SELECTION.toggle(held, displayName);
                LOGGER.debug("Held search {} {} as {}", change.added() ? "selected" : "removed",
                        displayName.getString(), SearchSelection.itemIds(held));
                announceToggle(minecraft, change, displayName);
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            }
        }
    }

    private static void observeContainer(Minecraft minecraft) {
        if (pendingStorage != null && (minecraft.level != pendingStorageLevel
                || System.currentTimeMillis() - pendingStorageAt > PENDING_STORAGE_TIMEOUT_MILLIS)) {
            clearPendingStorage();
        }

        AbstractContainerMenu openedMenu = null;
        if (MinecraftScreenAccess.getScreen(minecraft) instanceof AbstractContainerScreen<?> screen) {
            openedMenu = screen.getMenu();
        }

        if (activeStorageMenu != null && openedMenu != activeStorageMenu) {
            saveAndClearActiveContainer(minecraft);
        }

        if (activeStorageMenu == null && openedMenu != null && pendingStorage != null) {
            if (openedMenu.getStateId() == 0) {
                return;
            }
            BlockPos interactedStorage = pendingStorage;
            clearPendingStorage();
            int storageSlots = storageSlotCount(minecraft, interactedStorage, openedMenu);
            if (storageSlots > 0) {
                activeStorageMenu = openedMenu;
                activeStorageSlots = storageSlots;
                activeStorage = StorageIndex.canonicalPos(minecraft, interactedStorage);
                activeScope = ScopeUtil.current(minecraft);
                activeFamily = StorageBlocks.family(minecraft.level.getBlockState(activeStorage));
                activeEnderScope = StorageBlocks.isEnder(activeFamily) ? ScopeUtil.ender(minecraft) : null;
                activeFingerprint = StorageContents.fingerprint(activeStorageMenu, activeStorageSlots);
                Map<String, Integer> contents = StorageContents.collect(activeStorageMenu, activeStorageSlots);
                StorageIndex.UpdateResult result = recordActive(minecraft, contents);
                beginCompletion(minecraft, contents);
                if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
                    minecraft.player.sendOverlayMessage(indexMessage(result));
                }
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            }
        } else if (activeStorageMenu != null && openedMenu == activeStorageMenu) {
            int fingerprint = StorageContents.fingerprint(activeStorageMenu, activeStorageSlots);
            if (fingerprint != activeFingerprint) {
                activeFingerprint = fingerprint;
                StorageIndex.UpdateResult result = recordActive(minecraft,
                        StorageContents.collect(activeStorageMenu, activeStorageSlots));
                activeContentsChanged |= result.kind() == StorageIndex.UpdateKind.UPDATED;
            }
        }
    }

    private static StorageIndex.UpdateResult recordActive(Minecraft minecraft, Map<String, Integer> contents) {
        if (activeEnderScope != null) {
            INDEX.update(activeScope, activeStorage, List.of(activeStorage), StorageBlocks.ENDER_CHEST, Map.of());
            return INDEX.update(activeEnderScope, BlockPos.ZERO, List.of(BlockPos.ZERO),
                    StorageBlocks.ENDER_CONTENTS, contents);
        }
        return INDEX.update(activeScope, activeStorage, StorageIndex.physicalBlocks(minecraft, activeStorage),
                activeFamily, contents);
    }

    private static int storageSlotCount(Minecraft minecraft, BlockPos storage,
                                        AbstractContainerMenu menu) {
        if (minecraft.level == null) {
            return 0;
        }
        BlockState state = minecraft.level.getBlockState(storage);
        var block = state.getBlock();
        if (block instanceof ChestBlock && menu instanceof ChestMenu chestMenu) {
            boolean single = !state.hasProperty(ChestBlock.TYPE)
                    || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
            int expected = single ? SINGLE_CONTAINER_SLOTS : DOUBLE_CHEST_SLOTS;
            return chestMenu.getContainer().getContainerSize() == expected ? expected : 0;
        }
        if ((block instanceof BarrelBlock || block instanceof EnderChestBlock) && menu instanceof ChestMenu chestMenu) {
            return chestMenu.getContainer().getContainerSize() == SINGLE_CONTAINER_SLOTS
                    ? SINGLE_CONTAINER_SLOTS : 0;
        }
        if (block instanceof ShulkerBoxBlock && menu instanceof ShulkerBoxMenu
                && menu.slots.size() >= SINGLE_CONTAINER_SLOTS) {
            return SINGLE_CONTAINER_SLOTS;
        }
        return 0;
    }

    private static void onClientStopping(Minecraft minecraft) {
        saveAndClearActiveContainer(minecraft);
        if (!INDEX.flushBlocking()) {
            LOGGER.warn("Could not save the storage index while the client was stopping; recent changes are lost");
        }
    }

    private static void saveAndClearActiveContainer(Minecraft minecraft) {
        if (activeStorageMenu != null && activeStorage != null && activeScope != null) {
            StorageIndex.UpdateResult result = recordActive(minecraft,
                    StorageContents.collect(activeStorageMenu, activeStorageSlots));
            activeContentsChanged |= result.kind() == StorageIndex.UpdateKind.UPDATED;
            boolean persisted = INDEX.flush();
            finishCompletion(minecraft, activeStorageMenu);
            if (activeContentsChanged && minecraft.player != null && StorageFinderConfig.current().showMessages) {
                minecraft.player.sendOverlayMessage(persisted
                        ? Component.translatable("storagefinder.index.changed_on_close", result.itemTypes())
                        : Component.translatable("storagefinder.index.save_failed"));
            }
            LOCATOR.refresh(minecraft, SELECTION, INDEX);
        }
        activeStorageMenu = null;
        activeStorageSlots = 0;
        activeStorage = null;
        activeScope = null;
        activeFamily = null;
        activeEnderScope = null;
        activeFingerprint = 0;
        activeContentsChanged = false;
        completionBaseline = Map.of();
        completionColors = Set.of();
        INDEX.flush();
    }

    private static void beginCompletion(Minecraft minecraft, Map<String, Integer> contents) {
        String mode = StorageFinderConfig.current().searchCompletion;
        completionBaseline = Map.of();
        completionColors = Set.of();
        if (StorageFinderConfig.COMPLETE_NEVER.equals(mode) || minecraft.player == null) {
            return;
        }
        Set<Integer> reached = new LinkedHashSet<>();
        Set<Integer> containing = new LinkedHashSet<>();
        Set<BlockPos> physical = new LinkedHashSet<>(StorageIndex.physicalBlocks(minecraft, activeStorage));
        for (SearchSelection.Query query : SELECTION.active()) {
            if (query.kind() == SearchSelection.Kind.POSITION) {
                if (activeScope.equals(query.scope()) && physical.contains(query.pos())) {
                    reached.add(query.color());
                }
            } else if (query.matches(contents)) {
                containing.add(query.color());
            }
        }
        if (StorageFinderConfig.COMPLETE_ON_OPEN.equals(mode)) {
            reached.addAll(containing);
        } else if (!containing.isEmpty()) {
            completionColors = Set.copyOf(containing);
            completionBaseline = StorageContents.collect(minecraft.player.getInventory(),
                    activeStorageMenu.getCarried());
        }
        completeSearches(minecraft, reached);
    }

    private static void finishCompletion(Minecraft minecraft, AbstractContainerMenu menu) {
        if (completionColors.isEmpty() || minecraft.player == null) {
            return;
        }
        Map<String, Integer> now = StorageContents.collect(minecraft.player.getInventory(), menu.getCarried());
        Set<Integer> taken = new LinkedHashSet<>();
        for (SearchSelection.Query query : SELECTION.active()) {
            if (completionColors.contains(query.color())
                    && query.countIn(now) > query.countIn(completionBaseline)) {
                taken.add(query.color());
            }
        }
        completeSearches(minecraft, taken);
    }

    private static void completeSearches(Minecraft minecraft, Set<Integer> colors) {
        if (colors.isEmpty()) {
            return;
        }
        int removed = SELECTION.removeColors(colors);
        if (removed > 0 && minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(Component.translatable("storagefinder.selected.completed", removed));
        }
    }

    private static void clearPendingStorage() {
        pendingStorage = null;
        pendingStorageAt = 0L;
        pendingStorageLevel = null;
    }

    public static long lastIndexedAt(Minecraft minecraft, BlockPos canonical) {
        IndexRecord record = INDEX.get(ScopeUtil.current(minecraft), canonical);
        return record == null ? -1L : record.updatedAt();
    }

    public static boolean recordWithoutScreen(Minecraft minecraft, BlockPos interacted, AbstractContainerMenu menu) {
        StorageFinderConfig config = StorageFinderConfig.current();
        if (!config.enabled || !config.indexContainers || minecraft.level == null) {
            return false;
        }
        if (interacted.equals(pendingStorage)) {
            clearPendingStorage();
        }
        int storageSlots = storageSlotCount(minecraft, interacted, menu);
        if (storageSlots <= 0) {
            return false;
        }
        BlockPos canonical = StorageIndex.canonicalPos(minecraft, interacted);
        String family = StorageBlocks.family(minecraft.level.getBlockState(canonical));
        if (family == null || StorageBlocks.isEnder(family)) {
            return false;
        }
        INDEX.update(ScopeUtil.current(minecraft), canonical, StorageIndex.physicalBlocks(minecraft, canonical),
                family, StorageContents.collect(menu, storageSlots));
        return true;
    }

    public static void onContainerScreenRemoved(AbstractContainerMenu menu) {
        if (menu == activeStorageMenu) {
            saveAndClearActiveContainer(Minecraft.getInstance());
        }
    }

    public static void onConfigChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        LOCATOR.clear();
        if (StorageFinderConfig.current().enabled) {
            LOCATOR.refresh(minecraft, SELECTION, INDEX);
        }
    }

    public static void selectFromSearch(ItemStack stack, Component displayName) {
        Minecraft minecraft = Minecraft.getInstance();
        SearchSelection.Change change = SELECTION.toggle(stack, displayName);
        LOGGER.debug("Text search {} {} as {}", change.added() ? "selected" : "removed",
                displayName.getString(), SearchSelection.itemIds(stack));
        announceToggle(minecraft, change, displayName);
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
    }

    public static void selectGroup(String needle, ItemStack icon, Component displayName, Set<String> keys,
                                   boolean matchCustomNames) {
        Minecraft minecraft = Minecraft.getInstance();
        SearchSelection.Change change = SELECTION.toggleGroup(needle, icon, displayName, keys, matchCustomNames);
        LOGGER.debug("Group search {} {} with {} item keys", change.added() ? "selected" : "removed",
                needle, keys.size());
        if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(change.added()
                    ? Component.translatable("storagefinder.selected.group_added", displayName, keys.size(),
                    String.format("%06X", change.color() & 0xFFFFFF))
                    : Component.translatable("storagefinder.selected.removed", displayName));
        }
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
    }

    public static Integer groupColor(String needle) {
        return SELECTION.groupColor(needle);
    }

    public static boolean routeToRecord(StorageIndex.RecordView record) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ScopeUtil.current(minecraft).equals(record.scope())) {
            return false;
        }
        BlockPos pos = record.pos();
        Component name = Component.translatable("storagefinder.query.position", pos.getX(), pos.getY(), pos.getZ());
        SearchSelection.Change change = SELECTION.togglePosition(record.scope(), pos, iconFor(record.block()), name);
        if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(change.added()
                    ? Component.translatable("storagefinder.selected.route_added", name)
                    : Component.translatable("storagefinder.selected.removed", name));
        }
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
        return change.added();
    }

    private static ItemStack iconFor(String family) {
        if (family == null) {
            return new ItemStack(Items.CHEST);
        }
        if (family.equals(StorageBlocks.BARREL)) {
            return new ItemStack(Items.BARREL);
        }
        if (family.equals(StorageBlocks.ENDER_CHEST)) {
            return new ItemStack(Items.ENDER_CHEST);
        }
        if (family.startsWith("shulker:")) {
            Identifier id = Identifier.tryParse(family.substring("shulker:".length()));
            if (id != null) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return new ItemStack(Items.SHULKER_BOX);
        }
        return new ItemStack(Items.CHEST);
    }

    private static void announceToggle(Minecraft minecraft, SearchSelection.Change change, Component displayName) {
        if (minecraft.player == null || !StorageFinderConfig.current().showMessages) {
            return;
        }
        minecraft.player.sendOverlayMessage(change.added()
                ? Component.translatable("storagefinder.selected.added", displayName,
                String.format("%06X", change.color() & 0xFFFFFF))
                : Component.translatable("storagefinder.selected.removed", displayName));
    }

    public static Integer searchColor(ItemStack stack) {
        return SELECTION.colorFor(stack);
    }

    public static boolean togglePinned(ItemStack stack) {
        return SELECTION.togglePinned(stack);
    }

    public static boolean isPinned(ItemStack stack) {
        return SELECTION.isPinned(stack);
    }

    public static Map<String, Integer> nearbyItemStatistics() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return Map.of();
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        String scope = ScopeUtil.current(minecraft);
        BlockPos center = minecraft.player.blockPosition();
        for (IndexRecord record : INDEX.near(scope, center, StorageFinderConfig.current().searchRadiusChunks + 1)) {
            BlockPos pos = StorageIndex.pos(record);
            if (StorageLocator.withinSearchRadius(center, pos)
                    && minecraft.level.hasChunk(record.chunkX(), record.chunkZ())
                    && StorageLocator.isStorage(minecraft, pos)) {
                addStatistics(record, totals);
            }
        }
        return Map.copyOf(totals);
    }

    public static Map<String, Integer> allKnownItemStatistics() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Map.of();
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        INDEX.forEachInScope(ScopeUtil.current(minecraft), record -> addStatistics(record, totals));
        return Map.copyOf(totals);
    }

    private static void addStatistics(IndexRecord record, Map<String, Integer> totals) {
        record.counts().forEach((item, count) -> {
            if (!ItemKeys.isNameKey(item)) {
                totals.merge(item, count, ItemKeys::addCounts);
            }
        });
    }

    public static List<IndexRecordView> indexRecords(boolean allScopes) {
        Minecraft minecraft = Minecraft.getInstance();
        String currentScope = minecraft.level == null ? "" : ScopeUtil.current(minecraft);
        List<IndexRecordView> views = new ArrayList<>();
        for (StorageIndex.RecordView record : INDEX.recordViews()) {
            if (allScopes || currentScope.equals(record.scope())) {
                views.add(new IndexRecordView(record, recordPresence(minecraft, currentScope, record)));
            }
        }
        views.sort(Comparator.comparingLong((IndexRecordView view) -> view.record().updatedAt()).reversed());
        return views;
    }

    public static boolean removeIndexRecord(String key) {
        boolean removed = INDEX.removeRecord(key);
        if (removed) {
            LOCATOR.refresh(Minecraft.getInstance(), SELECTION, INDEX);
        }
        return removed;
    }

    public static int clearCurrentScopeIndex() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0;
        }
        int removed = INDEX.removeScope(ScopeUtil.current(minecraft));
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
        return removed;
    }

    public static void exportIndex(Consumer<java.nio.file.Path> onComplete) {
        INDEX.exportSnapshot(onComplete);
    }

    private static RecordPresence recordPresence(Minecraft minecraft, String currentScope,
                                                 StorageIndex.RecordView record) {
        if (minecraft.level == null || !currentScope.equals(record.scope())) {
            return RecordPresence.OTHER_SCOPE;
        }
        BlockPos pos = record.pos();
        if (!minecraft.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return RecordPresence.UNLOADED;
        }
        return StorageLocator.isStorage(minecraft, pos) ? RecordPresence.PRESENT : RecordPresence.MISSING;
    }

    public static List<SearchSelection.Query> activeQueries() {
        return SELECTION.queries();
    }

    public static Map<Integer, StorageLocator.QueryStats> queryStats() {
        return LOCATOR.queryStats();
    }

    public static void clearSearch() {
        Minecraft minecraft = Minecraft.getInstance();
        SELECTION.clear();
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
        if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(Component.translatable("storagefinder.selected.cleared"));
        }
    }

    private static Component indexMessage(StorageIndex.UpdateResult result) {
        if (!result.persisted()) {
            return Component.translatable("storagefinder.index.save_failed");
        }
        String key = switch (result.kind()) {
            case NEW -> "storagefinder.index.remembered";
            case UPDATED -> "storagefinder.index.updated";
            case UNCHANGED -> "storagefinder.index.already_remembered";
        };
        return Component.translatable(key, result.itemTypes());
    }

    public enum RecordPresence {
        PRESENT,
        UNLOADED,
        MISSING,
        OTHER_SCOPE
    }

    public record IndexRecordView(StorageIndex.RecordView record, RecordPresence presence) {
    }
}
