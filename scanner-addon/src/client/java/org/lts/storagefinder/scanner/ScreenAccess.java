package org.lts.storagefinder.scanner;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Keeps the addon compatible with the screen API move between Minecraft 26.1 and 26.2. */
final class ScreenAccess {
    private static final Access ACCESS = createAccess();

    private ScreenAccess() {
    }

    static Screen get(Minecraft minecraft) {
        return ACCESS.get(minecraft);
    }

    private static Access createAccess() {
        try {
            return new DirectAccess(Minecraft.class.getField("screen"));
        } catch (NoSuchFieldException ignored) {
            try {
                Field guiField = Minecraft.class.getField("gui");
                return new GuiAccess(guiField, guiField.getType().getMethod("screen"));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported Minecraft screen API", exception);
            }
        }
    }

    private static RuntimeException failed(ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        return new IllegalStateException("Could not access the Minecraft screen", cause);
    }

    private interface Access {
        Screen get(Minecraft minecraft);
    }

    private record DirectAccess(Field screenField) implements Access {
        @Override
        public Screen get(Minecraft minecraft) {
            try {
                return (Screen) screenField.get(minecraft);
            } catch (ReflectiveOperationException exception) {
                throw failed(exception);
            }
        }
    }

    private record GuiAccess(Field guiField, Method screenMethod) implements Access {
        @Override
        public Screen get(Minecraft minecraft) {
            try {
                return (Screen) screenMethod.invoke(guiField.get(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw failed(exception);
            }
        }
    }
}
