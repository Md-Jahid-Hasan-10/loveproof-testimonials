package com.loveproof.testimonials.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loveproof.testimonials.domain.AppUser;
import com.loveproof.testimonials.domain.Plan;
import com.loveproof.testimonials.domain.Space;
import com.loveproof.testimonials.exception.ConflictException;
import com.loveproof.testimonials.exception.NotFoundException;
import com.loveproof.testimonials.exception.PlanLimitExceededException;
import com.loveproof.testimonials.repository.AppUserRepository;
import com.loveproof.testimonials.repository.SpaceRepository;
import com.loveproof.testimonials.support.PostgresIntegrationTest;

/**
 * The Space rules.
 *
 * <p>These tests exercise the real database, so the unique slug index and the
 * plan limits are tested as they are rather than as they are described.
 */
class SpaceServiceTest extends PostgresIntegrationTest {

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private AppUserRepository userRepository;

    private UUID ownerId;

    @BeforeEach
    void clearTheSpaces() {
        spaceRepository.deleteAll();
        userRepository.deleteAll();
        ownerId = createUser("ada@example.com", Plan.FREE).getId();
    }

    @Test
    @DisplayName("creating a Space stores the title and derives a slug from it")
    void creatingASpaceDerivesTheSlug() {
        Space space = spaceService.create(ownerId, "Ada's Reviews", "What people say", null, null);

        assertThat(space.getId()).isNotNull();
        assertThat(space.getTitle()).isEqualTo("Ada's Reviews");
        assertThat(space.getSubtitle()).isEqualTo("What people say");
        assertThat(space.getSlug()).isEqualTo("ada-s-reviews");
        assertThat(space.getOwnerId()).isEqualTo(ownerId);
        assertThat(space.getTheme()).isEqualTo("basic");
        assertThat(space.isSlugLocked()).isFalse();
    }

    @Test
    @DisplayName("a slug that is already taken is rejected")
    void aTakenSlugIsRejected() {
        spaceService.create(ownerId, "Reviews", null, "reviews", null);

        // Another account cannot take the same address: the slug is unique
        // across the platform, not per owner.
        UUID otherOwner = createUser("grace@example.com", Plan.FREE).getId();

        assertThatThrownBy(() -> spaceService.create(otherOwner, "Reviews", null, "reviews", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    @DisplayName("a derived slug that is taken gets a number appended")
    void aTakenDerivedSlugGetsANumberAppended() {
        spaceService.create(ownerId, "Reviews", null, "reviews", null);
        UUID otherOwner = createUser("grace@example.com", Plan.FREE).getId();

        Space space = spaceService.create(otherOwner, "Reviews", null, null, null);

        assertThat(space.getSlug()).isEqualTo("reviews-2");
    }

    @Test
    @DisplayName("a slug is normalised into the form the address bar shows")
    void aSlugIsNormalised() {
        Space space = spaceService.create(ownerId, "Reviews", null, "My Reviews!", null);

        assertThat(space.getSlug()).isEqualTo("my-reviews");
    }

    @Test
    @DisplayName("creating beyond the plan limit is rejected")
    void creatingBeyondThePlanLimitIsRejected() {
        for (int index = 0; index < PlanLimits.FREE_SPACES; index++) {
            spaceService.create(ownerId, "Space " + index, null, "space-" + index, null);
        }

        assertThatThrownBy(() -> spaceService.create(ownerId, "One too many", null, "one-too-many", null))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessageContaining("limit");

        assertThat(spaceRepository.countByOwnerId(ownerId)).isEqualTo(PlanLimits.FREE_SPACES);
    }

    @Test
    @DisplayName("a Premium account may own more Spaces than a Free one")
    void aPremiumAccountMayOwnMoreSpaces() {
        UUID premium = createUser("premium@example.com", Plan.PREMIUM).getId();
        for (int index = 0; index < PlanLimits.PREMIUM_SPACES; index++) {
            spaceService.create(premium, "Space " + index, null, "premium-" + index, null);
        }

        assertThatThrownBy(() -> spaceService.create(premium, "One too many", null, "premium-too-many", null))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    @DisplayName("another account's Space is not found")
    void anotherAccountsSpaceIsNotFound() {
        Space space = spaceService.create(ownerId, "Reviews", null, "reviews", null);
        UUID otherOwner = createUser("grace@example.com", Plan.FREE).getId();

        assertThatThrownBy(() -> spaceService.get(otherOwner, space.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("does not exist");

        assertThatThrownBy(() -> spaceService.update(otherOwner, space.getId(),
                new SpaceService.SpaceChanges("Hijacked", null, null, null, null,
                        null, null, null, null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> spaceService.delete(otherOwner, space.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the owner can list and read their own Spaces")
    void theOwnerCanListAndReadTheirSpaces() {
        spaceService.create(ownerId, "Reviews", null, "reviews", null);
        spaceService.create(ownerId, "Wall", null, "wall", null);

        List<Space> spaces = spaceService.list(ownerId);

        assertThat(spaces).hasSize(2);
        assertThat(spaceService.count(ownerId)).isEqualTo(2);
        assertThat(spaceService.get(ownerId, spaces.get(0).getId()).getTitle()).isNotBlank();
    }

    @Test
    @DisplayName("updating changes only the fields that were sent")
    void updatingChangesOnlyTheFieldsThatWereSent() {
        Space space = spaceService.create(ownerId, "Reviews", "A subtitle", "reviews", null);

        Space updated = spaceService.update(ownerId, space.getId(),
                new SpaceService.SpaceChanges("New title", null, null, null, null,
                        null, null, null, null, null, null, null, null));

        assertThat(updated.getTitle()).isEqualTo("New title");
        // The subtitle was not sent, so it is left alone rather than cleared.
        assertThat(updated.getSubtitle()).isEqualTo("A subtitle");
        assertThat(updated.getSlug()).isEqualTo("reviews");
    }

    @Test
    @DisplayName("a locked slug cannot be changed")
    void aLockedSlugCannotBeChanged() {
        Space space = spaceService.create(ownerId, "Reviews", null, "reviews", null);
        space.setSlugLocked(true);
        spaceRepository.save(space);

        assertThatThrownBy(() -> spaceService.update(ownerId, space.getId(),
                new SpaceService.SpaceChanges(null, null, "something-else", null, null,
                        null, null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("can no longer be changed");
    }

    @Test
    @DisplayName("a slug can still be changed before the first submission")
    void aSlugCanStillBeChangedBeforeTheFirstSubmission() {
        Space space = spaceService.create(ownerId, "Reviews", null, "reviews", null);

        Space updated = spaceService.update(ownerId, space.getId(),
                new SpaceService.SpaceChanges(null, null, "new-address", null, null,
                        null, null, null, null, null, null, null, null));

        assertThat(updated.getSlug()).isEqualTo("new-address");
    }

    @Test
    @DisplayName("a premium theme is rejected on a Free account")
    void aPremiumThemeIsRejectedOnAFreeAccount() {
        assertThatThrownBy(() -> spaceService.create(ownerId, "Reviews", null, "reviews", "bold"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Premium");
    }

    @Test
    @DisplayName("a premium theme is allowed on a Premium account")
    void aPremiumThemeIsAllowedOnAPremiumAccount() {
        UUID premium = createUser("premium@example.com", Plan.PREMIUM).getId();

        Space space = spaceService.create(premium, "Reviews", null, "reviews", "bold");

        assertThat(space.getTheme()).isEqualTo("bold");
    }

    @Test
    @DisplayName("a theme that does not exist is rejected")
    void aThemeThatDoesNotExistIsRejected() {
        assertThatThrownBy(() -> spaceService.create(ownerId, "Reviews", null, "reviews", "no-such-theme"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("a required field cannot be switched on while the field is off")
    void aRequiredFieldCannotBeSwitchedOnWhileTheFieldIsOff() {
        Space space = spaceService.create(ownerId, "Reviews", null, "reviews", null);

        Space updated = spaceService.update(ownerId, space.getId(),
                new SpaceService.SpaceChanges(null, null, null, null, null,
                        false, true, null, null, null, null, null, null));

        assertThat(updated.isFieldCompany()).isFalse();
        assertThat(updated.isFieldCompanyRequired()).isFalse();
    }

    @Test
    @DisplayName("switching a field off also clears its required flag")
    void switchingAFieldOffClearsItsRequiredFlag() {
        Space space = spaceService.create(ownerId, "Reviews", null, "reviews", null);
        spaceService.update(ownerId, space.getId(),
                new SpaceService.SpaceChanges(null, null, null, null, null,
                        true, true, null, null, null, null, null, null));

        Space updated = spaceService.update(ownerId, space.getId(),
                new SpaceService.SpaceChanges(null, null, null, null, null,
                        false, null, null, null, null, null, null, null));

        assertThat(updated.isFieldCompany()).isFalse();
        assertThat(updated.isFieldCompanyRequired()).isFalse();
    }

    @Test
    @DisplayName("deleting a Space removes it")
    void deletingASpaceRemovesIt() {
        Space space = spaceService.create(ownerId, "Reviews", null, "reviews", null);

        spaceService.delete(ownerId, space.getId());

        assertThat(spaceRepository.count()).isZero();
    }

    @Test
    @DisplayName("after a downgrade the three oldest Spaces stay editable and the rest do not")
    void afterADowngradeTheOldestSpacesStayEditable() {
        // Five Spaces on a Premium account.
        UUID premium = createUser("premium@example.com", Plan.PREMIUM).getId();
        List<Space> spaces = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            spaces.add(spaceService.create(premium, "Space " + index, null, "premium-" + index, null));
        }

        // The account is downgraded to Free.
        AppUser account = userRepository.findById(premium).orElseThrow();
        account.setPlan(Plan.FREE);
        userRepository.save(account);

        // The three oldest stay editable; the two newest become read-only.
        assertThat(spaceService.isEditable(premium, spaces.get(0).getId())).isTrue();
        assertThat(spaceService.isEditable(premium, spaces.get(1).getId())).isTrue();
        assertThat(spaceService.isEditable(premium, spaces.get(2).getId())).isTrue();
        assertThat(spaceService.isEditable(premium, spaces.get(3).getId())).isFalse();
        assertThat(spaceService.isEditable(premium, spaces.get(4).getId())).isFalse();
    }

    @Test
    @DisplayName("an account within its limit may edit all of its Spaces")
    void anAccountWithinItsLimitMayEditAllOfItsSpaces() {
        Space first = spaceService.create(ownerId, "First", null, "first", null);
        Space second = spaceService.create(ownerId, "Second", null, "second", null);

        assertThat(spaceService.isEditable(ownerId, first.getId())).isTrue();
        assertThat(spaceService.isEditable(ownerId, second.getId())).isTrue();
    }

    @Test
    @DisplayName("Spaces created in the same millisecond are ordered deterministically")
    void spacesCreatedInTheSameMillisecondAreOrderedDeterministically() {
        // Five Spaces, all with the same creation timestamp. Without a second
        // sort key the order would depend on the database's execution plan,
        // and the three that stay editable would differ between calls.
        UUID premium = createUser("same-instant@example.com", Plan.PREMIUM).getId();
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        List<Space> spaces = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            Space space = new Space(premium, "Space " + index, "same-instant-" + index);
            space.setCreatedAt(instant);
            space.setUpdatedAt(instant);
            spaces.add(spaceRepository.save(space));
        }

        AppUser account = userRepository.findById(premium).orElseThrow();
        account.setPlan(Plan.FREE);
        userRepository.save(account);

        // The same three are editable on every call. Which three they are
        // depends on the identifiers, which are not ordered by creation --
        // what matters is that the answer never changes.
        List<UUID> first = editableIds(premium, spaces);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(editableIds(premium, spaces)).containsExactlyInAnyOrderElementsOf(first);
        }
        assertThat(first).hasSize(3);
    }

    /**
     * The identifiers of the Spaces an account may still edit.
     *
     * @param ownerId the owning account
     * @param spaces  the account's Spaces
     * @return the editable identifiers
     */
    private List<UUID> editableIds(UUID ownerId, List<Space> spaces) {
        return spaces.stream()
                .filter(space -> spaceService.isEditable(ownerId, space.getId()))
                .map(Space::getId)
                .toList();
    }

    @Test
    @DisplayName("a title made of characters that cannot appear in a slug still gets one")
    void aTitleWithNoUsableCharactersStillGetsASlug() {
        Space space = spaceService.create(ownerId, "!!!", null, null, null);

        assertThat(space.getSlug()).isNotEmpty();
        assertThat(space.getSlug()).matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }

    @Test
    @DisplayName("accents in a title are folded rather than dropped")
    void accentsInATitleAreFolded() {
        Space space = spaceService.create(ownerId, "Café Löve", null, null, null);

        assertThat(space.getSlug()).isEqualTo("cafe-love");
    }

    /**
     * Creates an account.
     *
     * @param email the email address
     * @param plan  the plan
     * @return the account
     */
    private AppUser createUser(String email, Plan plan) {
        AppUser user = new AppUser(email, "{noop}not-a-real-hash", "Test User");
        user.setPlan(plan);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }
}
