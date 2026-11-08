package com.loveproof.testimonials.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.loveproof.testimonials.domain.Plan;

/**
 * The embed options, and which of them need a Premium plan.
 *
 * <p>The catalogue lives in the backend rather than in the frontend because
 * the plan rule has to be enforced here. A Free account asking for the
 * carousel is refused by the server, not merely by the interface --
 * otherwise the restriction would be one request away from being bypassed.
 *
 * <p>A configuration that already uses a premium option keeps rendering it
 * after a downgrade (PRD §4.8). The rule is therefore about *choosing* an
 * option, not about *holding* one, so the check lives in the update path and
 * not in the read path.
 */
public final class EmbedCatalog {

    /** The layout an embed uses unless it is told otherwise. */
    public static final String DEFAULT_LAYOUT = "masonry";

    /** The theme mode an embed uses unless it is told otherwise. */
    public static final String DEFAULT_THEME_MODE = "light";

    /**
     * One embed option.
     *
     * @param key     the identifier stored on the configuration
     * @param label   the name shown in the interface
     * @param premium whether the option needs a Premium plan
     */
    public record Option(String key, String label, boolean premium) {
    }

    /** Every layout, in the order the picker shows them. */
    private static final List<Option> LAYOUTS = List.of(
            new Option("masonry", "Masonry", false),
            new Option("carousel", "Carousel", true));

    /** Every theme mode, in the order the picker shows them. */
    private static final List<Option> THEME_MODES = List.of(
            new Option("light", "Light", false),
            new Option("dark", "Dark", false));

    private EmbedCatalog() {
        // A holder for constants.
    }

    /**
     * Every layout.
     *
     * <p>A Free account still sees the premium ones -- they are simply marked
     * as locked, so the interface can explain what an upgrade buys.
     *
     * @return the layouts
     */
    public static List<Option> layouts() {
        return LAYOUTS;
    }

    /**
     * Every theme mode.
     *
     * @return the theme modes
     */
    public static List<Option> themeModes() {
        return THEME_MODES;
    }

    /**
     * Looks a layout up by its key.
     *
     * @param layout the layout key
     * @return the option, when it exists
     */
    public static Optional<Option> findLayout(String layout) {
        return find(LAYOUTS, layout);
    }

    /**
     * Looks a theme mode up by its key.
     *
     * @param themeMode the theme mode key
     * @return the option, when it exists
     */
    public static Optional<Option> findThemeMode(String themeMode) {
        return find(THEME_MODES, themeMode);
    }

    /**
     * Whether an account on the given plan may choose the given layout.
     *
     * @param plan   the account's plan
     * @param layout the layout key
     * @return {@code true} when the layout may be chosen
     */
    public static boolean isLayoutAvailable(Plan plan, String layout) {
        return findLayout(layout)
                .map(candidate -> !candidate.premium() || plan == Plan.PREMIUM)
                .orElse(false);
    }

    /**
     * Whether an account on the given plan may choose a background colour.
     *
     * @param plan the account's plan
     * @return {@code true} when a background colour may be chosen
     */
    public static boolean isBackgroundAvailable(Plan plan) {
        return plan == Plan.PREMIUM;
    }

    /**
     * Whether an account on the given plan may turn the animation on.
     *
     * @param plan the account's plan
     * @return {@code true} when the animation may be turned on
     */
    public static boolean isAnimationAvailable(Plan plan) {
        return plan == Plan.PREMIUM;
    }

    /**
     * Whether a background colour is a colour the embed can render.
     *
     * <p>Only a six-digit hex colour is accepted. The value ends up inside a
     * stylesheet on somebody else's page, so it is checked rather than
     * escaped and hoped for.
     *
     * @param color the colour
     * @return {@code true} when the colour is usable
     */
    public static boolean isValidColor(String color) {
        return color != null && color.matches("^#[0-9a-fA-F]{6}$");
    }

    /**
     * Normalises an option key.
     *
     * @param value the raw value
     * @return the trimmed, lower-cased key
     */
    public static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks an option up in a list.
     *
     * @param options the list
     * @param key     the key
     * @return the option, when it exists
     */
    private static Optional<Option> find(List<Option> options, String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalised = normalise(key);
        return options.stream()
                .filter(candidate -> candidate.key().equals(normalised))
                .findFirst();
    }
}
