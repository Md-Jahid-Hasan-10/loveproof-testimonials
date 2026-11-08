package com.loveproof.testimonials.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the embed script.
 *
 * <p>The script lives at {@code /embed/loveproof.js} rather than inside the
 * API so the snippet the owner copies reads like an ordinary asset: one
 * {@code <script>} tag and one {@code <div>}.
 *
 * <p>It is served from the backend rather than bundled with the frontend, so
 * there is exactly one copy of it and the host page never has to load the
 * React bundle to show a testimonial (PRD §4.8).
 */
@RestController
public class EmbedScriptController {

    /** How long a browser may cache the script. */
    private static final Duration CACHE_TTL = Duration.ofDays(1);

    /**
     * The script.
     *
     * @return the script
     */
    @GetMapping(value = "/embed/loveproof.js", produces = "application/javascript")
    public ResponseEntity<String> script() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/javascript"))
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(EmbedScript.SCRIPT);
    }
}
