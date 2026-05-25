package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.photo.storage.LocalPresignedPhotoStorage;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item 2 — end-to-end coverage of the pre-signed upload dance against a real Postgres
 * + {@link LocalPresignedPhotoStorage}. Confirms the V46 migration applied, the intent
 * row is persisted and consumed, and the resulting {@code listings_photos} row is
 * visible to the public read path.
 */
@AutoConfigureMockMvc
class ListingPhotoUploadIntentIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired LocalPresignedPhotoStorage presignedStorage;
    @Autowired PhotoUploadIntentRepository intentRepository;
    @Autowired ListingPhotoRepository photoRepository;

    @Test
    void ownerMintsUrlPutsToR2ThenConfirmsAndPhotoAppearsInGallery() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());
        String bearer = jwtTestSupport.bearerFor(owner);

        // 1. Mint pre-signed URL.
        MvcResult mintResult = mockMvc.perform(post("/api/listings/" + listingId + "/photos/upload-url")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1024,\"originalFilename\":\"hero.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.fileKey").exists())
                .andExpect(jsonPath("$.maxSizeBytes").value(10485760))
                .andReturn();
        JsonNode mintJson = objectMapper.readTree(mintResult.getResponse().getContentAsString());
        String fileKey = mintJson.get("fileKey").asText();
        assertThat(intentRepository.findByFileKey(fileKey)).isPresent();

        // 2. Simulate the browser PUTting bytes to R2 (LocalPresigned in IT mode just bookkeeps).
        presignedStorage.recordUpload(fileKey, 1024L, "image/jpeg");

        // 3. Confirm the upload — server HEADs R2, records the photo, marks intent consumed.
        mockMvc.perform(post("/api/listings/" + listingId + "/photos/confirm")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"" + fileKey + "\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024,\"caption\":\"Living room\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(listingId.intValue()))
                .andExpect(jsonPath("$.url").value("https://media.dreamhomes.com/" + fileKey))
                .andExpect(jsonPath("$.caption").value("Living room"));

        assertThat(photoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(listingId)).hasSize(1);
        assertThat(intentRepository.findByFileKey(fileKey).get().getConfirmedAt()).isNotNull();

        // 4. Confirming the same fileKey twice is rejected with 409.
        mockMvc.perform(post("/api/listings/" + listingId + "/photos/confirm")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"" + fileKey + "\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmingWithoutR2ObjectReturns422() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());
        String bearer = jwtTestSupport.bearerFor(owner);

        MvcResult mintResult = mockMvc.perform(post("/api/listings/" + listingId + "/photos/upload-url")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":2048}"))
                .andExpect(status().isCreated())
                .andReturn();
        String fileKey = objectMapper.readTree(mintResult.getResponse().getContentAsString())
                .get("fileKey").asText();
        // Do NOT call recordUpload — simulates browser failing the PUT.

        mockMvc.perform(post("/api/listings/" + listingId + "/photos/confirm")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"" + fileKey + "\",\"contentType\":\"image/jpeg\",\"sizeBytes\":2048}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private Long persistLiveListingFor(Long ownerId) {
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("addr").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        Listing listing = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        return listing.getId();
    }
}
