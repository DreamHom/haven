package com.dreamhomes.haven.common.seed;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AdminAuditLog;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListing;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;
import com.dreamhomes.haven.photo.ListingPhoto;
import com.dreamhomes.haven.photo.ListingPhotoRepository;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Auto-seeds the demo dataset on app boot when {@code haven.demo.auto-seed=true}
 * AND the listings table is empty. Idempotent — once any listing exists the
 * seeder is a no-op, so multiple boots / liveness-restart loops don't pollute.
 *
 * <p>Designed for the Railway-bundled-Postgres deploy where every redeploy wipes
 * the DB. Setting {@code HAVEN_DEMO_AUTO_SEED=true} in the Railway service
 * Variables makes the demo state self-heal on every fresh start without anyone
 * having to remember to run the Bruno collection.</p>
 *
 * <p><b>Off by default.</b> Real production environments should never enable
 * this — the demo accounts have a known shared password (see {@link #DEMO_PW_HASH}),
 * which is fine for a public bootcamp demo and unacceptable anywhere else.</p>
 *
 * <p><b>What it lays down:</b></p>
 * <ul>
 *   <li>10 demo users (4 owners, 2 agents, 4 applicants) with stable emails</li>
 *   <li>2 enriched agent profiles (license, agency, service areas, specializations)</li>
 *   <li>8 Lagos-themed properties + 8 LIVE listings</li>
 *   <li>8 unique listing photo URLs (Unsplash CDN — direct, no R2 round-trip)</li>
 *   <li>4 ACCEPTED agent assignments (Emeka on every listing he handles)</li>
 *   <li>8 verifications: 1 PENDING per type for queue diversity, plus
 *       2 APPROVED (Amaka identity, Emeka credentials) and 1 REJECTED (Biodun)</li>
 *   <li>1 PENDING offer from Ngozi on Amaka's Lekki listing — the
 *       "owner has unanswered work" demo prop</li>
 *   <li>2 audit-log entries (LISTING_TAKEDOWN + LISTING_APPROVED) for
 *       admin-dashboard colour</li>
 * </ul>
 *
 * <p><b>What it deliberately doesn't do</b> (to avoid Kafka / notification
 * side effects on a fresh boot): inspections, completed-deal arcs, reviews.
 * Run the {@code audit/bruno/Demo} collection post-boot if you need those.</p>
 */
@Component
@ConditionalOnProperty(value = "haven.demo.auto-seed", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    /**
     * Bcrypt-10 hash of {@code Demo2026!}. Pre-computed so the seeder doesn't
     * pay BCrypt cost on every boot. Same password the Bruno collections use,
     * so the demo presenter has one credential to remember.
     */
    private static final String DEMO_PW_HASH =
            "$2y$10$t1ESkHKmr1MKRVgatD3ROOfItcqu5xdKWUpvbMwY.rz.dsLf/no3K";

    /** Static admin id from the V11 seed migration — always 1. */
    private static final long PLATFORM_ADMIN_ID = 1L;

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final PropertyRepository propertyRepository;
    private final ListingRepository listingRepository;
    private final ListingPhotoRepository listingPhotoRepository;
    private final AgentListingRepository agentListingRepository;
    private final VerificationRepository verificationRepository;
    private final OfferRepository offerRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;

    @Value("${haven.demo.auto-seed:false}")
    private boolean autoSeed;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!autoSeed) {
            return; // belt + braces alongside @ConditionalOnProperty
        }
        long existing = listingRepository.count();
        if (existing > 0) {
            log.info("Demo seeder skipped — {} listings already present", existing);
            return;
        }
        log.info("Demo seeder running — empty catalogue detected, populating demo dataset…");
        seed();
        log.info("Demo seeder done — {} users, {} properties, {} listings, {} verifications, {} offers, {} audit entries.",
                userRepository.count(), propertyRepository.count(), listingRepository.count(),
                verificationRepository.count(), offerRepository.count(), adminAuditLogRepository.count());
    }

    // ─── seed body ──────────────────────────────────────────────────────────

    private void seed() {
        Instant now = Instant.now();

        // Owners
        User amaka = saveUser("amaka.okafor@demo.dreamhomes.local", Role.OWNER, "Amaka Okafor", "Amaka",
                "+2348031000001", "Solo Lekki landlord — two flats I take care of personally. I respond fast and never use middle-men.");
        User biodun = saveUser("biodun.adekunle@demo.dreamhomes.local", Role.OWNER, "Biodun Adekunle", "Biodun",
                "+2348031000002", "Developer of three Lagos estates. I list units once turnover paint is done and prefer agents on the ground.");
        User funmi = saveUser("funmi.adebayo@demo.dreamhomes.local", Role.OWNER, "Funmi Adebayo", "Funmi",
                "+2348031000003", "Yaba landlord. Walk-up flats and a studio for postgrad students. Long-term tenants preferred.");
        User tunde = saveUser("tunde.olumide@demo.dreamhomes.local", Role.OWNER, "Tunde Olumide", "Tunde",
                "+2348031000004", "Ikoyi family-home owner with two units on rotation. Flexible on inspection windows.");

        // Agents
        User emeka = saveUser("emeka.eze@demo.dreamhomes.local", Role.AGENT, "Emeka Eze", "Emeka",
                "+2348032000001", "Lekki + Ajah specialist agent. Built up the practice on first-time renter handholding — I walk every viewing, write up the meeting notes the same evening, and stay on the lease paperwork until it is signed.");
        User chika = saveUser("chika.okoro@demo.dreamhomes.local", Role.AGENT, "Chika Okoro", "Chika",
                "+2348032000002", "Old Ikoyi + Victoria Island agent. Luxury sales and serviced apartments — discreet, thorough, and very good at structural-due-diligence walkthroughs.");
        saveAgentProfile(emeka, "LIC-EM-1029", "Lekki Realty Partners",
                List.of("Lekki", "Ikate Elegushi", "Ajah", "Victoria Island"),
                List.of("English", "Igbo", "Yoruba"),
                List.of("Apartments", "First-time renters", "Serviced units"),
                "Standard 5% on sale closings; rent-only listings flat ₦150k once tenancy is signed.");
        saveAgentProfile(chika, "LIC-CK-2014", "Ikoyi Bridge Agency",
                List.of("Old Ikoyi", "Victoria Island", "Banana Island"),
                List.of("English", "Igbo"),
                List.of("Luxury sales", "Serviced apartments"),
                "5% on sale; serviced-unit lets quoted on application.");

        // Applicants — Temi is INTENTIONALLY left without notifications + inspections so the
        // live demo's first-inspection-ever moment is clean.
        User temi = saveUser("temi.balogun@demo.dreamhomes.local", Role.APPLICANT, "Temi Balogun", "Temi",
                "+2348033000001", null);
        User ngozi = saveUser("ngozi.eze@demo.dreamhomes.local", Role.APPLICANT, "Ngozi Eze", "Ngozi",
                "+2348033000002", null);
        User adaeze = saveUser("adaeze.nwosu@demo.dreamhomes.local", Role.APPLICANT, "Adaeze Nwosu", "Adaeze",
                "+2348033000003", null);
        User babatunde = saveUser("babatunde.dada@demo.dreamhomes.local", Role.APPLICANT, "Babatunde Dada", "Babatunde",
                "+2348033000004", null);

        // ─── Properties + listings + photos ─────────────────────────────────
        // Each entry: owner, type, address, beds, baths, sqm, lat, lng, listingType,
        //             price, cautionFee, serviceCharge, agencyFee, headline,
        //             unsplashId (for the photo)
        record SeedListing(User owner, PropertyType type, String address, Integer beds, Integer baths,
                           BigDecimal sqm, Double lat, Double lng,
                           ListingType listingType, BigDecimal price,
                           BigDecimal cautionFee, BigDecimal serviceCharge, BigDecimal agencyFee,
                           String headline, String description, String unsplashId, String photoCaption) {}

        List<SeedListing> rows = List.of(
                new SeedListing(amaka, PropertyType.APARTMENT, "12B Admiralty Way, Lekki Phase 1, Lagos",
                        3, 2, new BigDecimal("145.00"), 6.45410, 3.47470,
                        ListingType.RENT, new BigDecimal("3500000"),
                        new BigDecimal("700000"), new BigDecimal("250000"), null,
                        "Sea-view 3-bed, ready to move in",
                        "Top-floor 3-bedroom apartment off Admiralty Way. En-suite master, fitted kitchen, dedicated parking, 24/7 power, borehole water. Walking distance to the Oniru beach + Coliseum mall.",
                        "1564013799919-ab600027ffc6", "Sea-view living room"),
                new SeedListing(amaka, PropertyType.APARTMENT, "7 Sapphire Crescent, Ikate Elegushi, Lekki, Lagos",
                        2, 2, new BigDecimal("95.00"), 6.44820, 3.46530,
                        ListingType.RENT, new BigDecimal("2200000"),
                        new BigDecimal("500000"), new BigDecimal("180000"), null,
                        "Renovated 2-bed off Lekki-Epe expressway",
                        "Quiet street, two-minute walk to Sandfill bus stop. New wiring, fresh tiling. Cats welcome with deposit.",
                        "1502672260266-1c1ef2d93688", "Renovated 2-bed interior"),
                new SeedListing(biodun, PropertyType.HOUSE, "4 Bourdillon Road, Old Ikoyi, Lagos",
                        5, 5, new BigDecimal("420.00"), 6.45330, 3.44040,
                        ListingType.SALE, new BigDecimal("450000000"),
                        null, null, new BigDecimal("22500000"),
                        "5-bed Old Ikoyi duplex with pool",
                        "Detached 5-bed duplex with swimming pool, BQ, gardener's shed. Original owner; full title docs available.",
                        "1600596542815-ffad4c1539a9", "Front elevation"),
                new SeedListing(biodun, PropertyType.HOUSE, "21 Adeniyi Jones Avenue, Ikeja GRA, Lagos",
                        4, 3, new BigDecimal("280.00"), 6.58060, 3.35030,
                        ListingType.SALE, new BigDecimal("180000000"),
                        null, null, new BigDecimal("9000000"),
                        "Semi-detached 4-bed in Ikeja GRA gated cluster",
                        "Quiet road, gated cluster of six units. Walking distance to Ikeja City Mall, ten minutes to the international airport link.",
                        "1568605114967-8130f3a36994", "Side elevation"),
                new SeedListing(funmi, PropertyType.APARTMENT, "17 Hughes Avenue, Yaba, Lagos",
                        3, 2, new BigDecimal("110.00"), 6.50950, 3.37110,
                        ListingType.RENT, new BigDecimal("1800000"),
                        new BigDecimal("360000"), null, null,
                        "3-bed walk-up, two minutes to UNILAG",
                        "Second-floor flat with cross-ventilation, fitted wardrobes, bath + shower in main. Long-term tenants preferred.",
                        "1600585154340-be6161a56a0c", "Fitted kitchen"),
                new SeedListing(funmi, PropertyType.APARTMENT, "9 Herbert Macaulay Way, Yaba, Lagos",
                        1, 1, new BigDecimal("35.00"), 6.51060, 3.37430,
                        ListingType.RENT, new BigDecimal("950000"),
                        new BigDecimal("200000"), null, null,
                        "Self-con studio for postgrad students",
                        "Single-room self-contained with kitchenette, security door, smart prepaid meter. Six-minute walk to UNILAG postgrad school.",
                        "1565183997392-2f6f122e5912", "Studio interior"),
                new SeedListing(tunde, PropertyType.HOUSE, "3 Macpherson Avenue, Old Ikoyi, Lagos",
                        4, 4, new BigDecimal("320.00"), 6.45050, 3.43890,
                        ListingType.SALE, new BigDecimal("320000000"),
                        null, null, new BigDecimal("16000000"),
                        "Terraced 4-bed with private gated parking",
                        "End-of-terrace unit with two-car covered parking, recently re-roofed. Three reception rooms, en-suite master with walk-in closet.",
                        "1600210492486-724fe5c67fb0", "Master bedroom"),
                new SeedListing(tunde, PropertyType.APARTMENT, "88 Awolowo Road, Ikoyi, Lagos",
                        2, 2, new BigDecimal("88.00"), 6.45630, 3.43520,
                        ListingType.RENT, new BigDecimal("5500000"),
                        new BigDecimal("1100000"), new BigDecimal("800000"), null,
                        "Serviced 2-bed with gym + daily cleaning",
                        "Building service: daily cleaning, 24/7 power, on-site gym, two parking bays. All bills included in service charge — tenant only adds DStv.",
                        "1613490493576-7fde63acd811", "Master bedroom suite")
        );

        Listing lekkiListing = null;
        Listing ikejaListing = null;
        Listing bourdillonListing = null;
        Listing ikateListing = null;
        for (SeedListing row : rows) {
            Property property = propertyRepository.save(Property.builder()
                    .ownerId(row.owner().getId()).type(row.type()).address(row.address())
                    .bedrooms(row.beds()).bathrooms(row.baths()).sizeSqm(row.sqm())
                    .description(row.description()).latitude(row.lat()).longitude(row.lng())
                    .createdAt(now)
                    .build());
            Listing listing = listingRepository.save(Listing.builder()
                    .propertyId(property.getId()).ownerId(row.owner().getId())
                    .listingType(row.listingType()).askingPrice(row.price()).currency("NGN")
                    .cautionFee(row.cautionFee()).serviceCharge(row.serviceCharge()).agencyFee(row.agencyFee())
                    .headline(row.headline()).description(row.description())
                    .priceNegotiable(true).status(ListingStatus.LIVE)
                    .createdAt(now).updatedAt(now)
                    .build());
            listingPhotoRepository.save(ListingPhoto.builder()
                    .listingId(listing.getId())
                    .url("https://images.unsplash.com/photo-" + row.unsplashId() + "?w=1600&h=1067&fit=crop&q=80&auto=format")
                    .caption(row.photoCaption()).displayOrder(0)
                    .uploadedAt(now)
                    .build());
            // Capture references for downstream wiring.
            if (row.address().startsWith("12B Admiralty")) lekkiListing = listing;
            else if (row.address().startsWith("21 Adeniyi Jones")) ikejaListing = listing;
            else if (row.address().startsWith("4 Bourdillon")) bourdillonListing = listing;
            else if (row.address().startsWith("7 Sapphire")) ikateListing = listing;
        }

        // ─── Agent assignments — Emeka assigned to 4 listings, all ACCEPTED ─
        for (Listing l : List.of(lekkiListing, ikateListing, bourdillonListing, ikejaListing)) {
            if (l == null) continue;
            agentListingRepository.save(AgentListing.builder()
                    .listingId(l.getId()).agentUserId(emeka.getId())
                    .requestedByOwnerId(l.getOwnerId())
                    .status(AgentListingStatus.ACCEPTED)
                    .requestedAt(now).decidedAt(now)
                    .build());
        }

        // ─── Verifications: mix of statuses across all 4 types ──────────────
        // 2 APPROVED (Amaka + Emeka), 1 REJECTED (Biodun), 5 PENDING.
        // The decided ones also stamp the badge on the user row + add audit log entries.
        Verification amakaV = saveVerification(amaka.getId(), VerificationType.OWNER_IDENTITY, null, now);
        decideVerification(amakaV, VerificationStatus.APPROVED, null, now);
        amaka.setIdentityVerifiedAt(now); userRepository.save(amaka);
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(PLATFORM_ADMIN_ID).action(AdminAction.VERIFICATION_APPROVED)
                .targetType(AuditTargetType.VERIFICATION).targetId(amakaV.getId()).createdAt(now).build());

        Verification emekaV = saveVerification(emeka.getId(), VerificationType.AGENT_CREDENTIALS, null, now);
        decideVerification(emekaV, VerificationStatus.APPROVED, null, now);
        AgentProfile ep = agentProfileRepository.findById(emeka.getId()).orElseThrow();
        ep.setCredentialVerifiedAt(now); agentProfileRepository.save(ep);
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(PLATFORM_ADMIN_ID).action(AdminAction.VERIFICATION_APPROVED)
                .targetType(AuditTargetType.VERIFICATION).targetId(emekaV.getId()).createdAt(now).build());

        Verification biodunV = saveVerification(biodun.getId(), VerificationType.OWNER_IDENTITY, null, now);
        decideVerification(biodunV, VerificationStatus.REJECTED,
                "Selfie image is blurry and the passport scan is partially obscured. Please re-upload both with better lighting; we will re-review within 24 hours.", now);
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(PLATFORM_ADMIN_ID).action(AdminAction.VERIFICATION_REJECTED)
                .targetType(AuditTargetType.VERIFICATION).targetId(biodunV.getId()).createdAt(now).build());

        // PENDING queue across all 4 verification types — gives the admin dashboard
        // visible workload + every type represented at least once.
        saveVerification(chika.getId(), VerificationType.AGENT_CREDENTIALS, null, now);
        saveVerification(funmi.getId(), VerificationType.OWNER_IDENTITY, null, now);
        saveVerification(tunde.getId(), VerificationType.OWNER_IDENTITY, null, now);
        saveVerification(ngozi.getId(), VerificationType.APPLICANT_IDENTITY, null, now);
        saveVerification(adaeze.getId(), VerificationType.APPLICANT_IDENTITY, null, now);

        // ─── Ngozi's PENDING offer on Amaka's Lekki — owner-has-unanswered-work ─
        if (lekkiListing != null) {
            offerRepository.save(Offer.builder()
                    .listingId(lekkiListing.getId())
                    .applicantId(ngozi.getId())
                    .ownerId(lekkiListing.getOwnerId())
                    .amount(new BigDecimal("3200000")).currency("NGN")
                    .message("Liked the photos and the location. Title docs question first — when was the C of O issued? Open to ₦3.2M for a 12-month commitment.")
                    .status(OfferStatus.PENDING)
                    .proposedByUserId(ngozi.getId())
                    .createdAt(now).updatedAt(now)
                    .build());
        }

        // ─── Admin moderation: takedown + re-approve on Yaba studio for ──────
        // ─── audit-log diversity (LISTING_TAKEDOWN + LISTING_APPROVED).      ─
        // We don't actually flip the listing's status — just record the audit
        // entries — so the demo browse keeps showing all 8 LIVE listings.
        Listing yabaStudio = listingRepository.findAll().stream()
                .filter(l -> "9 Herbert Macaulay Way, Yaba, Lagos".equals(
                        propertyRepository.findById(l.getPropertyId()).map(Property::getAddress).orElse("")))
                .findFirst().orElse(null);
        if (yabaStudio != null) {
            adminAuditLogRepository.save(AdminAuditLog.builder()
                    .adminId(PLATFORM_ADMIN_ID).action(AdminAction.LISTING_TAKEDOWN)
                    .targetType(AuditTargetType.LISTING).targetId(yabaStudio.getId())
                    .metadata("{\"reason\":\"Spot-check moderation cycle for audit-log demonstration.\"}")
                    .createdAt(now).build());
            adminAuditLogRepository.save(AdminAuditLog.builder()
                    .adminId(PLATFORM_ADMIN_ID).action(AdminAction.LISTING_APPROVED)
                    .targetType(AuditTargetType.LISTING).targetId(yabaStudio.getId())
                    .createdAt(now).build());
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private User saveUser(String email, Role role, String fullName, String displayName,
                          String phone, String publicBio) {
        return userRepository.save(User.builder()
                .email(email).passwordHash(DEMO_PW_HASH).role(role)
                .fullName(fullName).displayName(displayName).phone(phone)
                .publicBio(publicBio)
                .createdAt(Instant.now())
                .build());
    }

    private void saveAgentProfile(User agent, String licenseNumber, String agency,
                                  List<String> serviceAreas, List<String> languages,
                                  List<String> specializations, String feeSchedule) {
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber(licenseNumber)
                .agency(agency)
                .serviceAreas(serviceAreas).languages(languages).specializationTags(specializations)
                .feeSchedule(feeSchedule)
                .createdAt(Instant.now())
                .build());
    }

    private Verification saveVerification(Long submitterId, VerificationType type, Long propertyId, Instant now) {
        return verificationRepository.save(Verification.builder()
                .submitterUserId(submitterId)
                .type(type)
                .targetUserId(type == VerificationType.PROPERTY_DOCUMENTS ? null : submitterId)
                .targetPropertyId(propertyId)
                .status(VerificationStatus.PENDING)
                .documentRefs("{\"nin\":\"00000000000\",\"selfie\":\"https://r2.example/seed.jpg\"}")
                .submittedAt(now)
                .build());
    }

    private void decideVerification(Verification v, VerificationStatus status, String reason, Instant now) {
        v.setStatus(status);
        v.setDecidedAt(now);
        v.setDecidedByAdminId(PLATFORM_ADMIN_ID);
        v.setDecisionReason(reason);
        verificationRepository.save(v);
    }
}
