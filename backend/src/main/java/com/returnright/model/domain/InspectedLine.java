package com.returnright.model.domain;

import com.returnright.model.enums.InspectionGrade;

/**
 * Warehouse result for one returned line. A rejected line has {@code acceptedQuantity == 0}.
 *
 * @param unitPricePaise price paid per unit, in paise
 */
public record InspectedLine(
    long unitPricePaise, int returnedQuantity, int acceptedQuantity, InspectionGrade grade) {}
