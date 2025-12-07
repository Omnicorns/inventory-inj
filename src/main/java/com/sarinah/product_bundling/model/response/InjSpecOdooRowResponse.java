package com.sarinah.product_bundling.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InjSpecOdooRowResponse {

    private Long   locationId;
    private String locationName;
    private String pricelistName;

    private Integer    quantityOdoo;
    private BigDecimal limitStock;
    private BigDecimal stock;
    private BigDecimal stockPctToInj;

    private BigDecimal basePrice;
    private String     pricingMode;
    private BigDecimal addedValuePct;
    private BigDecimal marginInjPct;
    private BigDecimal sellPrice;

    private Boolean active;
}
