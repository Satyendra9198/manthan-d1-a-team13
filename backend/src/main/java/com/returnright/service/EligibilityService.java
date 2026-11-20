package com.returnright.service;

import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;

/** Return-eligibility rules (assumptions A-01, A-02, A-18). */
public interface EligibilityService {

  EligibilityResult evaluate(ItemEligibilityInput input);
}
