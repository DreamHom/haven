package com.dreamhomes.haven.verification.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Strategy interface for storing verification documents (NIN slip, C of O, agent
 * licence, etc.). Separate from {@code PhotoStorage} because:
 * <ul>
 *   <li>Different R2 key prefix ({@code verifications/} not {@code listings/}) so
 *       lifecycle policies + access controls can diverge.</li>
 *   <li>Larger / sensitive payloads (PDFs, scans) — different content-type set.</li>
 *   <li>Per-user scoping rather than per-listing.</li>
 * </ul>
 *
 * <p>Persona audit: every persona who submits a verification flagged the absence
 * of a real upload endpoint. They were being asked to host their own NIN /
 * Certificate of Occupancy on a public CDN — a data-leakage scenario.</p>
 */
public interface VerificationDocumentStorage {

    /**
     * Upload {@code file} for a verification owned by the given user. Returns the
     * public-but-deep URL the file is now hostable at — store it on the
     * {@code Verification.documentRefs} map.
     *
     * @throws com.dreamhomes.haven.photo.exception.PhotoUploadException on any
     *     read or upload failure (reuses the photo exception family — both are
     *     opaque "could not store the file" 400s to callers).
     */
    String upload(MultipartFile file, Long userId);
}
