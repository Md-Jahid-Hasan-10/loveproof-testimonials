package com.loveproof.testimonials.service;

import java.util.List;
import java.util.Optional;

import com.loveproof.testimonials.domain.Plan;

/**
 * The themes a Space can use.
 *
 * <p>The catalogue lives in the backend rather than in the frontend because
 * the plan rule has to be enforced here. A Free account asking for a premium
 * theme is refused by the server, not merely by the interface -- otherwise
 * the restriction would be one request away from being bypassed.
 *
 * <p>A Space that already uses a premium theme keeps rendering it after a
 * downgrade (PRD 4.4). The rule is therefore about *choosing* a theme, not
 * about *holding* one, so the check lives in the update path and not in the
 * read path.
 */
public final class ThemeCatalog {

    /** The theme a Space uses unless it is told otherwise. */
    public static final String DEFAULT_THEME = "basic";

    /**
     * One theme.
     *
     * @param key     the identifier stored on the Space
     * @param label   the name shown in the interface
     * @param premium whether the theme needs a Premium plan
     */
    public record Theme(String key, String label, boolean premium) {
    }

    /**
     * Every theme, in the order the picker shows them.
     *
     * <p>The basic themes come first so a new account lands on one that works
     * without an upgrade.
     */
    private static final List<Theme> THEMES = List.of(
            new Theme("basic", "Basic", false),
            new Theme("cards", "Cards", false),
            new Theme("minimal", "Minimal", false),
            new Theme("bold", "Bold", true),
            new Theme("gradient", "Gradient", true),
            new Theme("spotlight", "Spotlight", true));

    private ThemeCatalog() {
        // A holder for constants.
    }

    /**
     * Every theme.
     *
     * <p>A Free account still sees the premium themes -- they are simply
     * marked as locked, so the interface can explain what an upgrade buys.
     *
     * @return the themes
     */
    public static List<Theme> all() {
        return THEMES;
    }

    /**
     * Whether an account on the given plan may choose the given theme.
     *
     * @param plan  the account's plan
     * @param theme the theme key
     * @return {@code true} when the theme may be chosen
     */
    public static boolean isAvailable(Plan plan, String theme) {
        return find(theme).map(candidate -> !candidate.premium() || plan == Plan.PREMIUM).orElse(false);
    }

    /**
     * Looks a theme up by its key.
     *
     * @param theme the theme key
     * @return the theme, when it exists
     */
    public static Optional<Theme> find(String theme) {
        if (theme == null) {
            return Optional.empty();
        }
        return THEMES.stream().filter(candidate -> candidate.key().equals(theme)).findFirst();
    }
}
