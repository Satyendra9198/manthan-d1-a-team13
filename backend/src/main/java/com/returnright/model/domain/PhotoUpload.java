package com.returnright.model.domain;

import java.util.UUID;

/**
 * A photo as received from the client, before any validation.
 *
 * @param itemId order item the photo belongs to, taken from the multipart part name
 * @param content raw file bytes
 */
public record PhotoUpload(UUID itemId, byte[] content) {}
