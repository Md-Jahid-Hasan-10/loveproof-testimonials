package com.loveproof.testimonials.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loveproof.testimonials.domain.StripeEvent;

/**
 * The webhook ledger.
 *
 * <p>Nothing is read from here in the ordinary course of business: the
 * insert is the point. A replayed event fails to insert, and the failure is
 * what makes the replay a no-op.
 */
public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {
}
