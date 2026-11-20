package com.returnright.model.dto.response;

import java.util.UUID;

/**
 * A customer photo on a return line.
 *
 * @param url relative URL that serves the image to signed-in warehouse staff
 */
public record PhotoResponse(UUID photoId, String contentType, String url) {}
