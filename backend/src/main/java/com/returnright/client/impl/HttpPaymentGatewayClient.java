package com.returnright.client.impl;

import com.returnright.client.PaymentGatewayClient;
import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.client.dto.GatewayRefundResponse;
import com.returnright.exception.GatewayTimeoutException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls the mock gateway: {@code POST /v1/refunds} with an {@code Idempotency-Key} header.
 *
 * <p>Timeouts, connection errors and 5xx responses mean the outcome is unknown, so they raise
 * {@link GatewayTimeoutException} (the refund becomes UNKNOWN, never FAILED). A 4xx means the
 * gateway refused the request, so no money moved and the refund is declined.
 */
@Component
public class HttpPaymentGatewayClient implements PaymentGatewayClient {

  private static final String SUCCEEDED = "SUCCEEDED";

  private final RestClient restClient;

  public HttpPaymentGatewayClient(
      RestClient.Builder builder,
      @Value("${returnright.gateway.base-url}") String baseUrl,
      @Value("${returnright.gateway.timeout:PT3S}") Duration timeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);
    this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  @Override
  public GatewayRefundResponse refund(GatewayRefundRequest request) {
    try {
      GatewayResponseBody body =
          restClient
              .post()
              .uri("/v1/refunds")
              .header("Idempotency-Key", request.idempotencyKey())
              .body(new GatewayRequestBody(request.returnId(), request.amountPaise()))
              .retrieve()
              .body(GatewayResponseBody.class);
      if (body == null) {
        throw new GatewayTimeoutException("Empty response from payment gateway");
      }
      return SUCCEEDED.equals(body.status())
          ? GatewayRefundResponse.success(body.transactionId())
          : GatewayRefundResponse.declined(body.declineReason());
    } catch (ResourceAccessException | HttpServerErrorException unknownOutcome) {
      throw new GatewayTimeoutException(unknownOutcome.getMessage());
    } catch (HttpClientErrorException refused) {
      return GatewayRefundResponse.declined("GATEWAY_REJECTED_" + refused.getStatusCode().value());
    }
  }

  private record GatewayRequestBody(UUID returnId, long amountPaise) {}

  private record GatewayResponseBody(String status, String transactionId, String declineReason) {}
}
