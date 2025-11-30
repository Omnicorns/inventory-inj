package com.sarinah.product_bundling.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
@Data

public class InjSpecRowResponse {
    private Long locationId;
    private String locationName;
    private String pricelistName;

    private Integer quantityOdoo;        // stok asli Odoo
    private BigDecimal stockPctToInj;    // % stock yg boleh buat INJ
    private BigDecimal limitStock;// quantity * %
    private BigDecimal stock;

    private BigDecimal basePrice;        // harga dari Odoo
    private String pricingMode;          // ADDED_VALUE / MARGIN
    private BigDecimal addedValuePct;    // %
    private BigDecimal marginInjPct;     // %
    private BigDecimal sellPrice;        // harga jual ke INJ

    private Boolean active;              // ON/OFF (switch)
}
