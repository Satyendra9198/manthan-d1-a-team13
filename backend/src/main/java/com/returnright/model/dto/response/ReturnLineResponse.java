package com.returnright.model.dto.response;

import com.returnright.model.enums.ReturnReason;
import java.util.UUID;

/**
 * One item on a submitted return.
 *
 * @param itemId the order item id
 */
public record ReturnLineResponse(
    UUID itemId,
    String productName,
    int quantity,
    ReturnReason reason,
    long lineTotalPaise,
    int photoCount) {}
