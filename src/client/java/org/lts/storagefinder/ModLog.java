package org.lts.storagefinder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModLog {
    public static final String MOD_ID = "storagefinder";
    public static final String NAME = "StorageFinder";

    private static final String ACCENT = "38;2;205;150;95";
    private static final String ESC = "\u001b[";
    private static final boolean COLOR = colorEnabled();
    private static final Path GAME_DIR = gameDir();

    private final Logger logger;
    private final String tag;

    private ModLog(String area) {
        this.logger = LoggerFactory.getLogger(area == null ? MOD_ID : MOD_ID + "/" + area.toLowerCase(java.util.Locale.ROOT));
        this.tag = renderTag(area);
    }

    public static ModLog root() {
        return new ModLog(null);
    }

    public static ModLog of(String area) {
        return new ModLog(area);
    }

    public void ready() {
        ready(null);
    }

    public void ready(String detail, Object... args) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        StringBuilder line = new StringBuilder(tag);
        line.append(paint("v" + version, "1;" + ACCENT, Level.INFO)).append(' ');
        line.append(paint("ready", "32", Level.INFO));
        if (detail != null && !detail.isBlank()) {
            line.append(paint(" · ", "90", Level.INFO));
            format(line, detail, args, Level.INFO);
        }
        logger.info(finish(line, Level.INFO));
    }

    public void info(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            emit(Level.INFO, message, args);
        }
    }

    public void warn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            emit(Level.WARN, message, args);
        }
    }

    public void error(String message, Object... args) {
        if (logger.isErrorEnabled()) {
            emit(Level.ERROR, message, args);
        }
    }

    public void debug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            emit(Level.DEBUG, message, args);
        }
    }

    public boolean debugEnabled() {
        return logger.isDebugEnabled();
    }

    private void emit(Level level, String message, Object[] args) {
        Throwable failure = args.length > 0 && args[args.length - 1] instanceof Throwable throwable ? throwable : null;
        Object[] values = failure == null ? args : java.util.Arrays.copyOf(args, args.length - 1);
        StringBuilder line = new StringBuilder(tag);
        line.append(level.base);
        format(line, message, values, level);
        if (failure != null) {
            line.append(paint(" (" + describe(failure) + ")", "2", level));
        }
        String text = finish(line, level);
        Throwable trace = failure != null && (level == Level.ERROR || level == Level.DEBUG || unexpected(failure)) ? failure : null;
        switch (level) {
            case INFO -> logger.info(text, trace);
            case WARN -> logger.warn(text, trace);
            case ERROR -> logger.error(text, trace);
            case DEBUG -> logger.debug(text, trace);
        }
    }

    private static void format(StringBuilder line, String message, Object[] args, Level level) {
        String template = message == null ? "null" : message;
        int index = 0;
        int cursor = 0;
        while (cursor < template.length()) {
            int hole = template.indexOf("{}", cursor);
            if (hole < 0 || index >= args.length) {
                line.append(template, cursor, template.length());
                break;
            }
            line.append(template, cursor, hole);
            line.append(paint(value(args[index++]), level.value, level));
            cursor = hole + 2;
        }
    }

    private static String value(Object value) {
        if (value instanceof Path path) {
            return shortPath(path);
        }
        if (value instanceof File file) {
            return shortPath(file.toPath());
        }
        if (value instanceof Throwable throwable) {
            return describe(throwable);
        }
        if (value instanceof Object[] array) {
            return java.util.Arrays.toString(array);
        }
        return String.valueOf(value);
    }

    private static String shortPath(Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            if (GAME_DIR != null && absolute.startsWith(GAME_DIR) && !absolute.equals(GAME_DIR)) {
                return GAME_DIR.relativize(absolute).toString().replace('\\', '/');
            }
            return absolute.toString();
        } catch (RuntimeException exception) {
            return String.valueOf(path);
        }
    }

    public static String describe(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException
                || current instanceof InvocationTargetException) && current.getCause() != null) {
            current = current.getCause();
        }
        String name = current.getClass().getSimpleName();
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            Throwable cause = current.getCause();
            return cause != null && cause != current ? name + " ← " + describe(cause) : name;
        }
        String clean = message.strip().replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > 240) {
            clean = clean.substring(0, 237) + "...";
        }
        return name + ": " + clean;
    }

    private static boolean unexpected(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException
                || current instanceof InvocationTargetException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof RuntimeException || current instanceof Error;
    }

    private static String renderTag(String area) {
        if (!COLOR) {
            return area == null ? "[" + NAME + "] " : "[" + NAME + "/" + area + "] ";
        }
        StringBuilder tag = new StringBuilder();
        tag.append(ESC).append("90m[").append(ESC).append("1;").append(ACCENT).append('m').append(NAME);
        if (area != null) {
            tag.append(ESC).append("22m/").append(area);
        }
        tag.append(ESC).append("22;90m]").append(ESC).append("39m ");
        return tag.toString();
    }

    private static String paint(String text, String style, Level level) {
        if (!COLOR) {
            return text;
        }
        return ESC + style + "m" + text + ESC + "22;39m" + level.base;
    }

    private static String finish(StringBuilder line, Level level) {
        if (COLOR) {
            line.append(ESC).append("22;39m");
        }
        return line.toString();
    }

    private static boolean colorEnabled() {
        String property = System.getProperty("lts.log.color");
        if (property != null) {
            return Boolean.parseBoolean(property);
        }
        return System.getenv("NO_COLOR") == null;
    }

    private static Path gameDir() {
        try {
            return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private enum Level {
        INFO("", "96"),
        WARN("33", "1;33"),
        ERROR("31", "1;31"),
        DEBUG("90", "37");

        private final String base;
        private final String value;

        Level(String base, String value) {
            this.base = base.isEmpty() || !COLOR ? "" : ESC + base + "m";
            this.value = value;
        }
    }
}
