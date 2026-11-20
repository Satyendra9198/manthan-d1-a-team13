package com.returnright.service;

import com.returnright.model.dto.request.CreateReturnRequest;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.LabelResponse;
import com.returnright.model.dto.response.OrderResponse;
import com.returnright.model.dto.response.ReturnResponse;
import java.util.UUID;

/**
 * Customer self-service. Customers don't log in; every call is verified by order number plus the
 * email used on the order (assumption: hackathon-level customer auth).
 */
public interface CustomerReturnService {

  OrderResponse lookupOrder(OrderLookupRequest request);

  ReturnResponse createReturn(CreateReturnRequest request);

  ReturnResponse getReturn(UUID returnId, String orderNumber, String email);

  ReturnResponse cancelReturn(UUID returnId, String orderNumber, String email);

  ReturnResponse markShipped(UUID returnId, String orderNumber, String email);

  LabelResponse getLabel(UUID returnId, String orderNumber, String email);
}
