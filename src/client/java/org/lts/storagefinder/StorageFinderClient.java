package org.lts.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.lts.storagefinder.gui.StorageSearchScreen;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StorageFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "storagefinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
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
    private static boolean activeContentsChanged;
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
        LOGGER.info("Storage Finder initialized");
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
        } else {
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
                LOGGER.info("Held search {} {} as {}", change.added() ? "selected" : "removed",
                        displayName.getString(), SearchSelection.itemIds(held));
                if (config.showMessages && change.added()) {
                    minecraft.player.sendOverlayMessage(Component.translatable(
                            "storagefinder.selected.added", displayName, String.format("%06X", change.color() & 0xFFFFFF)));
                } else if (config.showMessages) {
                    minecraft.player.sendOverlayMessage(Component.translatable(
                            "storagefinder.selected.removed", displayName));
                }
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
        if (MinecraftScreenAccess.getScreen(minecraft) instanceof AbstractContainerScreen<?> screen && screen.getMenu() instanceof ChestMenu chestMenu) {
            openedMenu = chestMenu;
        } else if (MinecraftScreenAccess.getScreen(minecraft) instanceof AbstractContainerScreen<?> screen) {
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
                StorageIndex.UpdateResult result = INDEX.update(
                        activeScope, activeStorage, activeStorageMenu, activeStorageSlots);
                dismissSearchesForStorage(minecraft, activeScope, activeStorage);
                if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
                    minecraft.player.sendOverlayMessage(indexMessage(result));
                }
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            }
        } else if (activeStorageMenu != null && openedMenu == activeStorageMenu) {
            StorageIndex.UpdateResult result = INDEX.update(
                    activeScope, activeStorage, activeStorageMenu, activeStorageSlots);
            activeContentsChanged |= result.kind() == StorageIndex.UpdateKind.UPDATED;
        }
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
        if (block instanceof BarrelBlock && menu instanceof ChestMenu chestMenu) {
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
            LOGGER.warn("Storage index could not be saved while the client was stopping");
        }
    }

    private static void saveAndClearActiveContainer(Minecraft minecraft) {
        if (activeStorageMenu != null && activeStorage != null && activeScope != null) {
            StorageIndex.UpdateResult result = INDEX.update(
                    activeScope, activeStorage, activeStorageMenu, activeStorageSlots);
            activeContentsChanged |= result.kind() == StorageIndex.UpdateKind.UPDATED;
            boolean persisted = INDEX.flush();
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
        activeContentsChanged = false;
        INDEX.flush();
    }

    private static void clearPendingStorage() {
        pendingStorage = null;
        pendingStorageAt = 0L;
        pendingStorageLevel = null;
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
        LOGGER.info("Text search {} {} as {}", change.added() ? "selected" : "removed",
                displayName.getString(), SearchSelection.itemIds(stack));
        if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(change.added()
                    ? Component.translatable("storagefinder.selected.added", displayName,
                            String.format("%06X", change.color() & 0xFFFFFF))
                    : Component.translatable("storagefinder.selected.removed", displayName));
        }
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
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

    public static java.util.Map<String, Integer> nearbyItemStatistics() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, Integer> totals = new java.util.LinkedHashMap<>();
        String scope = ScopeUtil.current(minecraft);
        BlockPos center = minecraft.player.blockPosition();
        for (StorageIndex.Record record : INDEX.recordsInScope(scope)) {
            BlockPos pos = record.pos();
            if (StorageLocator.withinSearchRadius(center, pos)
                    && minecraft.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    && StorageLocator.isStorage(minecraft, pos)) {
                record.normalizedCounts().forEach((item, count) ->
                        totals.merge(item, count, Integer::sum));
            }
        }
        return java.util.Map.copyOf(totals);
    }

    public static java.util.Map<String, Integer> allKnownItemStatistics() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, Integer> totals = new java.util.LinkedHashMap<>();
        String scope = ScopeUtil.current(minecraft);
        for (StorageIndex.Record record : INDEX.recordsInScope(scope)) {
            record.normalizedCounts().forEach((item, count) ->
                    totals.merge(item, count, Integer::sum));
        }
        return java.util.Map.copyOf(totals);
    }

    public static java.util.List<IndexRecordView> indexRecords(boolean allScopes) {
        Minecraft minecraft = Minecraft.getInstance();
        String currentScope = minecraft.level == null ? "" : ScopeUtil.current(minecraft);
        return INDEX.recordViews().stream()
                .filter(record -> allScopes || currentScope.equals(record.scope()))
                .map(record -> new IndexRecordView(record, recordPresence(minecraft, currentScope, record)))
                .sorted(java.util.Comparator.comparingLong(
                        (IndexRecordView view) -> view.record().updatedAt()).reversed())
                .toList();
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

    public static java.util.List<SearchSelection.Query> activeQueries() {
        return SELECTION.queries();
    }

    public static java.util.Map<Integer, StorageLocator.QueryStats> queryStats() {
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

    private static void dismissSearchesForStorage(Minecraft minecraft, String scope, BlockPos storage) {
        Set<String> contents = INDEX.itemsAt(scope, storage);
        if (contents.isEmpty()) {
            return;
        }
        Set<Integer> matchedColors = new LinkedHashSet<>();
        SELECTION.entries().forEach((itemKey, color) -> {
            if (contents.contains(itemKey)) {
                matchedColors.add(color);
            }
        });
        int removed = SELECTION.removeColors(matchedColors);
        if (removed > 0 && minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(Component.translatable("storagefinder.selected.completed", removed));
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
