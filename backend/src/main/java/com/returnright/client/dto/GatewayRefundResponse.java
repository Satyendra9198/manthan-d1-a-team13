package com.returnright.client.dto;

/** Definitive gateway answer: the payout either succeeded or was declined. */
public record GatewayRefundResponse(boolean succeeded, String transactionId, String declineReason) {

  public static GatewayRefundResponse success(String transactionId) {
    return new GatewayRefundResponse(true, transactionId, null);
  }

  public static GatewayRefundResponse declined(String declineReason) {
    return new GatewayRefundResponse(false, null, declineReason);
  }
}
