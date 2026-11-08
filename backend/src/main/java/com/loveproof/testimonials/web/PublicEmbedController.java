package com.loveproof.testimonials.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loveproof.testimonials.service.EmbedService;
import com.loveproof.testimonials.web.dto.embed.PublicEmbedResponse;

/**
 * The public embed surface.
 *
 * <p>Everything here is reachable without authentication: the embed script
 * runs on somebody else's website, where nobody is signed in (PRD §4.8).
 *
 * <p>The data endpoint is read-only and cacheable. It carries no email
 * address and no owner identifier: a stranger learns what the owner chose to
 * publish and nothing else.
 */
@RestController
@RequestMapping("/api/app/public/embeds")
public class PublicEmbedController {

    /** How long a browser may cache the embed data. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final EmbedService embedService;

    /**
     * Creates the controller.
     *
     * @param embedService the embed rules
     */
    public PublicEmbedController(EmbedService embedService) {
        this.embedService = embedService;
    }

    /**
     * Reads the data an embed renders.
     *
     * @param token the embed token
     * @return the testimonials and the settings the owner chose
     */
    @GetMapping("/{token}")
    public ResponseEntity<PublicEmbedResponse> read(@PathVariable String token) {
        PublicEmbedResponse data = PublicEmbedResponse.from(embedService.readPublicData(token));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(data);
    }
}
