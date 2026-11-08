package com.loveproof.testimonials.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * A simple in-process rate limiter.
 *
 * <p>Each key gets a bucket that refills continuously: one token per {@code period}, with at most
 * {@code limit} tokens held at once. A request consumes one token; when the bucket is empty the
 * request is refused until enough time has passed for the next token to appear.
 *
 * <p>The limiter is deliberately in-process and per-instance (assumption A-8): the application is
 * self-hosted on one node, and a shared store would add an operational dependency for no benefit at
 * this scale. On a multi-instance deployment each instance would enforce the limit independently,
 * which makes the effective limit the per-instance limit times the instance count.
 *
 * <p>Keys are salted hashes of the client address, never the address itself, so the bucket keys
 * cannot be reversed into IP addresses (NFR-SEC-6).
 */
@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final Clock clock;

    /**
     * Creates the limiter.
     *
     * @param clock the clock, used for the refill arithmetic
     */
    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Consumes a token for a key.
     *
     * @param key        the bucket key
     * @param limit      how many requests are allowed per period
     * @param period     the period the limit applies over
     * @return the outcome
     */
    public Decision tryConsume(String key, int limit, Duration period) {
        if (key == null) {
            // Without a key there is nothing to count against. The request is
            // allowed rather than refused, so a missing address cannot be used
            // to lock a legitimate visitor out.
            return Decision.allowed(0);
        }

        Instant now = clock.instant();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now, limit));

        synchronized (bucket) {
            bucket.refill(now, limit, period);

            if (bucket.tokens >= 1.0d) {
                bucket.tokens -= 1.0d;
                return Decision.allowed((int) Math.floor(bucket.tokens));
            }

            // The bucket is empty. The caller has to wait for the next token.
            double secondsUntilNextToken = (1.0d - bucket.tokens) * period.toMillis() / 1000.0d;
            long retryAfter = Math.max(1L, (long) Math.ceil(secondsUntilNextToken));
            return Decision.refused(retryAfter);
        }
    }

    /**
     * Forgets the buckets that have been idle for longer than the given age.
     *
     * <p>Without this the map would grow for the lifetime of the process, one entry per distinct
     * client address ever seen.
     *
     * @param maxIdle how long a bucket may sit unused before it is forgotten
     * @return how many buckets were removed
     */
    public int evictIdleBuckets(Duration maxIdle) {
        Instant cutoff = clock.instant().minus(maxIdle);
        int before = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(cutoff));
        return before - buckets.size();
    }

    /**
     * Forgets every bucket. Used by the tests to isolate one case from another.
     */
    public void reset() {
        buckets.clear();
    }

    /**
     * The outcome of a rate-limit check.
     *
     * @param allowed    whether the request may proceed
     * @param retryAfter how many seconds to wait, when the request was refused
     */
    public record Decision(boolean allowed, long retryAfter) {

        static Decision allowed(int remaining) {
            return new Decision(true, 0L);
        }

        static Decision refused(long retryAfter) {
            return new Decision(false, retryAfter);
        }
    }

    /**
     * One client's bucket.
     */
    private static final class Bucket {

        /** The tokens left, as a fraction so the refill is continuous rather than stepped. */
        private double tokens;

        /** When the bucket was last used, for the idle eviction. */
        private Instant lastSeen;

        /**
         * Creates a bucket.
         *
         * <p>The bucket starts full: a client that has never been seen has spent nothing, so it must
         * be allowed its full allowance straight away. The refill then tops it back up over time.
         *
         * @param now   the current time
         * @param limit the bucket's capacity
         */
        Bucket(Instant now, int limit) {
            this.tokens = limit;
            this.lastSeen = now;
        }

        /**
         * Adds the tokens that have accrued since the last call.
         *
         * @param now    the current time
         * @param limit  the bucket's capacity
         * @param period the period the limit applies over
         */
        void refill(Instant now, int limit, Duration period) {
            long elapsedMillis = Duration.between(lastSeen, now).toMillis();
            if (elapsedMillis <= 0) {
                lastSeen = now;
                return;
            }
            double refillRate = limit / (double) period.toMillis();
            tokens = Math.min(limit, tokens + (elapsedMillis * refillRate));
            lastSeen = now;
        }
    }
}
