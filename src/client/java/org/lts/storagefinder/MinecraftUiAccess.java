package org.lts.storagefinder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

/**
 * Bridges HUD services that moved from Minecraft/Gui to Gui/Hud in 26.2.
 */
public final class MinecraftUiAccess {
    private static final Access ACCESS = createAccess();

    private MinecraftUiAccess() {
    }

    public static boolean isHudHidden(Minecraft minecraft) {
        return ACCESS.isHudHidden(minecraft);
    }

    public static void addClientSystemMessage(Minecraft minecraft, Component message) {
        ACCESS.chat(minecraft).addClientSystemMessage(message);
    }

    public static ToastManager getToastManager(Minecraft minecraft) {
        return ACCESS.toastManager(minecraft);
    }

    private static Access createAccess() {
        try {
            Field guiField = Minecraft.class.getField("gui");
            Field hudField = guiField.getType().getField("hud");
            Class<?> hudType = hudField.getType();
            return new ModernAccess(
                    guiField,
                    hudField,
                    hudType.getMethod("isHidden"),
                    hudType.getMethod("getChat"),
                    guiField.getType().getMethod("toastManager")
            );
        } catch (NoSuchFieldException | NoSuchMethodException ignored) {
            return createLegacyAccess();
        }
    }

    private static Access createLegacyAccess() {
        try {
            Field optionsField = Minecraft.class.getField("options");
            return new LegacyAccess(
                    optionsField,
                    optionsField.getType().getField("hideGui"),
                    Minecraft.class.getField("gui"),
                    Minecraft.class.getField("gui").getType().getMethod("getChat"),
                    Minecraft.class.getMethod("getToastManager")
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Minecraft HUD API", exception);
        }
    }

    private static RuntimeException accessFailed(ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        return new IllegalStateException("Could not access the Minecraft HUD", cause);
    }

    private interface Access {
        boolean isHudHidden(Minecraft minecraft);

        ChatComponent chat(Minecraft minecraft);

        ToastManager toastManager(Minecraft minecraft);
    }

    private record LegacyAccess(
            Field optionsField,
            Field hideGuiField,
            Field guiField,
            Method getChatMethod,
            Method getToastManagerMethod
    ) implements Access {
        @Override
        public boolean isHudHidden(Minecraft minecraft) {
            try {
                return hideGuiField.getBoolean(optionsField.get(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }

        @Override
        public ChatComponent chat(Minecraft minecraft) {
            try {
                return (ChatComponent) getChatMethod.invoke(guiField.get(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }

        @Override
        public ToastManager toastManager(Minecraft minecraft) {
            try {
                return (ToastManager) getToastManagerMethod.invoke(minecraft);
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }
    }

    private record ModernAccess(
            Field guiField,
            Field hudField,
            Method isHiddenMethod,
            Method getChatMethod,
            Method toastManagerMethod
    ) implements Access {
        private Object gui(Minecraft minecraft) throws IllegalAccessException {
            return guiField.get(minecraft);
        }

        private Object hud(Minecraft minecraft) throws ReflectiveOperationException {
            return hudField.get(gui(minecraft));
        }

        @Override
        public boolean isHudHidden(Minecraft minecraft) {
            try {
                return (boolean) isHiddenMethod.invoke(hud(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }

        @Override
        public ChatComponent chat(Minecraft minecraft) {
            try {
                return (ChatComponent) getChatMethod.invoke(hud(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }

        @Override
        public ToastManager toastManager(Minecraft minecraft) {
            try {
                return (ToastManager) toastManagerMethod.invoke(gui(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }
    }
}
