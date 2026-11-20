package com.returnright.model.dto.response;

import com.returnright.model.enums.ReturnReason;

public record ReasonCountResponse(ReturnReason reason, long count) {}
