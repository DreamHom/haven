package com.dreamhomes.haven.verification.dto;

/**
 * Wire response for {@code POST /api/verifications/files}. Returns the URL the
 * uploaded file is hostable at so the client can paste it into a subsequent
 * {@code POST /api/verifications} call as part of {@code documentRefs}.
 */
public record UploadedDocumentResponse(String url) {
}
