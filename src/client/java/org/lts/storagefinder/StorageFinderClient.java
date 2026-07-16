package org.lts.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.lts.storagefinder.gui.StorageSearchScreen;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StorageFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "storagefinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final SearchSelection SELECTION = new SearchSelection();
    private static final StorageIndex INDEX = new StorageIndex();
    private static final StorageLocator LOCATOR = new StorageLocator();

    private static BlockPos pendingStorage;
    private static long pendingStorageAt;
    private static ChestMenu activeChestMenu;
    private static BlockPos activeStorage;
    private static String activeScope;
    private static boolean activeContentsChanged;
    private static ClientLevel lastLevel;
    private static boolean rightAltWasDown;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        StorageFinderConfig.load();
        INDEX.load();
        ClientTickEvents.END_CLIENT_TICK.register(StorageFinderClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(StorageFinderClient::saveAndClearActiveContainer);
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> StorageRenderer.render(LOCATOR));
        LOGGER.info("Storage Finder initialized");
    }

    public static void onStorageInteracted(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        StorageFinderConfig config = StorageFinderConfig.current();
        if (config.enabled && config.indexContainers && StorageLocator.isStorage(minecraft, pos)) {
            pendingStorage = pos.immutable();
            pendingStorageAt = System.currentTimeMillis();
        } else {
            pendingStorage = null;
        }
    }

    private static void onTick(Minecraft minecraft) {
        StorageFinderConfig config = StorageFinderConfig.loadIfChanged();
        if (minecraft.level != lastLevel) {
            saveAndClearActiveContainer(minecraft);
            pendingStorage = null;
            if (lastLevel != null && minecraft.level == null) {
                WorldScopeTracker.clear();
            }
            lastLevel = minecraft.level;
        }
        handleRightAlt(minecraft);
        if (config.enabled && config.indexContainers) {
            observeContainer(minecraft);
        } else {
            pendingStorage = null;
            saveAndClearActiveContainer(minecraft);
        }

        ticks++;
        if (ticks % 10 == 0) {
            if (config.enabled) {
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            } else {
                LOCATOR.clear();
            }
        }
    }

    private static void handleRightAlt(Minecraft minecraft) {
        boolean down = InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RALT);
        StorageFinderConfig config = StorageFinderConfig.current();
        if (config.enabled && down && !rightAltWasDown && minecraft.screen == null && minecraft.player != null) {
            ItemStack held = minecraft.player.getMainHandItem();
            if (held.isEmpty()) {
                minecraft.setScreen(new StorageSearchScreen());
            } else {
                SearchSelection.Change change = SELECTION.toggle(held);
                LOGGER.info("Held search {} {} as {}", change.added() ? "selected" : "removed",
                        held.getHoverName().getString(), SearchSelection.itemIds(held));
                if (config.showMessages && change.added()) {
                    minecraft.player.sendOverlayMessage(Component.translatable(
                            "storagefinder.selected.added", held.getHoverName(), String.format("%06X", change.color() & 0xFFFFFF)));
                } else if (config.showMessages) {
                    minecraft.player.sendOverlayMessage(Component.translatable(
                            "storagefinder.selected.removed", held.getHoverName()));
                }
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            }
        }
        rightAltWasDown = down;
    }

    private static void observeContainer(Minecraft minecraft) {
        if (pendingStorage != null && System.currentTimeMillis() - pendingStorageAt > 15_000L) {
            pendingStorage = null;
        }

        ChestMenu visibleMenu = null;
        if (minecraft.screen instanceof AbstractContainerScreen<?> screen && screen.getMenu() instanceof ChestMenu chestMenu) {
            visibleMenu = chestMenu;
        }

        if (visibleMenu != activeChestMenu) {
            saveAndClearActiveContainer(minecraft);
            if (visibleMenu != null && pendingStorage != null) {
                activeChestMenu = visibleMenu;
                activeStorage = StorageIndex.canonicalPos(minecraft, pendingStorage);
                activeScope = ScopeUtil.current(minecraft);
                pendingStorage = null;
                StorageIndex.UpdateResult result = INDEX.update(activeScope, activeStorage, activeChestMenu);
                dismissSearchesForStorage(minecraft, activeStorage);
                if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
                    minecraft.player.sendOverlayMessage(indexMessage(result));
                }
                LOCATOR.refresh(minecraft, SELECTION, INDEX);
            }
        } else if (activeChestMenu != null) {
            StorageIndex.UpdateResult result = INDEX.update(activeScope, activeStorage, activeChestMenu);
            activeContentsChanged |= result.kind() == StorageIndex.UpdateKind.UPDATED;
        }
    }

    private static void saveAndClearActiveContainer(Minecraft minecraft) {
        if (activeChestMenu != null && activeStorage != null && activeScope != null) {
            StorageIndex.UpdateResult result = INDEX.update(activeScope, activeStorage, activeChestMenu);
            activeContentsChanged |= result.kind() == StorageIndex.UpdateKind.UPDATED;
            if (activeContentsChanged && minecraft.player != null && StorageFinderConfig.current().showMessages) {
                minecraft.player.sendOverlayMessage(result.persisted()
                        ? Component.translatable("storagefinder.index.changed_on_close", result.itemTypes())
                        : Component.translatable("storagefinder.index.save_failed"));
            }
            LOCATOR.refresh(minecraft, SELECTION, INDEX);
        }
        activeChestMenu = null;
        activeStorage = null;
        activeScope = null;
        activeContentsChanged = false;
    }

    public static void onContainerScreenRemoved(ChestMenu menu) {
        if (menu == activeChestMenu) {
            saveAndClearActiveContainer(Minecraft.getInstance());
        }
    }

    public static void onConfigChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        if (StorageFinderConfig.current().enabled) {
            LOCATOR.refresh(minecraft, SELECTION, INDEX);
        } else {
            LOCATOR.clear();
        }
    }

    public static void selectFromSearch(ItemStack stack, Component displayName) {
        Minecraft minecraft = Minecraft.getInstance();
        SELECTION.clear();
        SearchSelection.Change change = SELECTION.toggle(stack);
        LOGGER.info("Text search selected {} as {}", displayName.getString(), SearchSelection.itemIds(stack));
        if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(Component.translatable(
                    change.added() ? "storagefinder.selected.added" : "storagefinder.selected.already_selected",
                    displayName, String.format("%06X", change.color() & 0xFFFFFF)));
        }
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
    }

    public static void clearSearch() {
        Minecraft minecraft = Minecraft.getInstance();
        SELECTION.clear();
        LOCATOR.refresh(minecraft, SELECTION, INDEX);
        if (minecraft.player != null && StorageFinderConfig.current().showMessages) {
            minecraft.player.sendOverlayMessage(Component.translatable("storagefinder.selected.cleared"));
        }
    }

    private static void dismissSearchesForStorage(Minecraft minecraft, BlockPos storage) {
        Set<Integer> matchedColors = new LinkedHashSet<>();
        for (BlockPos physical : StorageIndex.physicalBlocks(minecraft, storage)) {
            matchedColors.addAll(LOCATOR.matches().getOrDefault(physical, java.util.List.of()));
        }
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
}
