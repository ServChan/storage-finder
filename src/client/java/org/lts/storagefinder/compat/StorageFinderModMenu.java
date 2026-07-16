package org.lts.storagefinder.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.lts.storagefinder.gui.StorageFinderConfigScreen;

public final class StorageFinderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StorageFinderConfigScreen::new;
    }
}
