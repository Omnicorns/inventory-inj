package com.sarinah.product_bundling.model.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InjSpecRowUpdateRequest {
    private Long locationId;

    private String pricingMode;          // ADDED_VALUE / MARGIN
    private BigDecimal addedValuePct;
    private BigDecimal marginInjPct;

    private BigDecimal stockPctToInj;    // % stock
    private Boolean active;

}
