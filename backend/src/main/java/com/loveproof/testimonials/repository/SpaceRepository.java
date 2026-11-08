package com.loveproof.testimonials.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loveproof.testimonials.domain.Space;

/**
 * The Space store.
 *
 * <p>Every owner-scoped query filters by the owner, so a Space belonging to
 * another account is simply not found -- the caller answers 404 rather than
 * 403, so the API does not confirm that the Space exists.
 */
public interface SpaceRepository extends JpaRepository<Space, UUID> {

    /**
     * Finds a Space by its public URL segment.
     *
     * @param slug the slug
     * @return the Space, or empty
     */
    Optional<Space> findBySlug(String slug);

    /**
     * Whether the slug is already taken.
     *
     * @param slug the slug
     * @return {@code true} when taken
     */
    boolean existsBySlug(String slug);

    /**
     * Counts the Spaces an account owns.
     *
     * @param ownerId the owning account
     * @return the number of Spaces
     */
    long countByOwnerId(UUID ownerId);

    /**
     * Lists the Spaces an account owns, newest first.
     *
     * @param ownerId  the owning account
     * @param pageable the page request
     * @return the page of Spaces
     */
    Page<Space> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    /**
     * Lists every Space an account owns, newest first.
     *
     * <p>Used where the whole list is needed regardless of size -- the
     * dashboard sidebar and the downgrade rule, which has to see every Space
     * to work out which three stay editable.
     *
     * <p>The id breaks the tie. Two Spaces created in the same millisecond
     * share a {@code created_at}, and without a second sort key the order
     * would depend on the database's own execution plan rather than on
     * anything the application can reason about.
     *
     * @param ownerId the owning account
     * @return the Spaces, newest first
     */
    List<Space> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId);

    /**
     * Lists every Space an account owns, oldest first.
     *
     * <p>The oldest Spaces are the ones that stay editable when a downgraded
     * user has more Spaces than the Free plan allows (OPEN-B).
     *
     * <p>The id breaks the tie, for the same reason as in the descending
     * ordering: two Spaces created in the same millisecond share a
     * {@code created_at}, and the rule that decides which of them stay
     * editable has to be the same on every call.
     *
     * @param ownerId the owning account
     * @return the Spaces, oldest first
     */
    List<Space> findByOwnerIdOrderByCreatedAtAscIdAsc(UUID ownerId);
}
