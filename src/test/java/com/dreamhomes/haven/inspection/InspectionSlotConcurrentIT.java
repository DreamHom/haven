package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent race-condition proof for the inspection_slots GIST EXCLUDE constraint
 * (V8). Twenty threads fire identical slot inserts in the same millisecond; the
 * GIST index serialises the conflicting range and lets exactly one commit. The
 * other nineteen see DataIntegrityViolationException.
 *
 * <p>Deliberately not {@code @Transactional} at class level — each worker thread
 * needs its own independent transaction so the race actually plays out across
 * separate JDBC connections, not inside a single shared test transaction.</p>
 */
class InspectionSlotConcurrentIT extends AbstractPostgresIT {

    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired InspectionSlotRepository slotRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void twentyParallelInsertsForTheSameSlotProduceOneWinnerAndNineteenRejected()
            throws InterruptedException {
        Long listingId = newLiveListing().getId();
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    tx.execute(status -> {
                        slotRepository.saveAndFlush(slot(listingId,
                                "2026-06-01T10:00:00Z", "2026-06-01T11:00:00Z"));
                        return null;
                    });
                    successes.incrementAndGet();
                } catch (DataAccessException expected) {
                    // GIST EXCLUDE may surface as DataIntegrityViolationException (the
                    // common case: lost the lock race normally) or DeadlockLoserDataAccessException
                    // (Postgres detected mutual waiting and aborted us). Both mean "your insert
                    // didn't commit" — the platform-level outcome is identical.
                    rejections.incrementAndGet();
                } catch (Throwable other) {
                    unexpected.add(other);
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(finished).as("all 20 threads finished within 30s").isTrue();
        assertThat(unexpected).as("no unexpected exceptions").isEmpty();
        assertThat(successes.get()).as("exactly one insert commits").isEqualTo(1);
        assertThat(rejections.get()).as("the other 19 are rejected").isEqualTo(threads - 1);
    }

    private InspectionSlot slot(Long listingId, String starts, String ends) {
        return InspectionSlot.builder()
                .listingId(listingId)
                .startsAt(Instant.parse(starts))
                .endsAt(Instant.parse(ends))
                .createdAt(Instant.now())
                .build();
    }

    private Listing newLiveListing() {
        User owner = userRepository.save(User.builder()
                .email("owner-slotconcurrent-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Owner")
                .displayName("Owner")
                .tokenVersion(1).createdAt(Instant.now()).build());
        Property property = propertyRepository.save(Property.builder()
                .ownerId(owner.getId()).type(PropertyType.HOUSE)
                .address("Address").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        return listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(owner.getId())
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("100.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }
}
