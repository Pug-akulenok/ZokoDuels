package ru.zokocraft.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {}

    public static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    /** Для предметов: без курсива по умолчанию. */
    public static Component item(String s) {
        return c(s).decoration(TextDecoration.ITALIC, false);
    }
}
