package com.sarinah.product_bundling.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VariantOdooInjSpecResponse {

    private Long variantId;
    private String name;
    private String barcode;
    private String sku;
    private BigDecimal listPrice;
    private Boolean image;
    private String imageBase64;
    private List<AttributeResponse> attributes;
    private List<InjSpecOdooRowResponse> rows;

    // ⬇️ TAMBAHKAN INI
    private Long locationCount;
    private Long activeLocationCount;
    private Instant lastSyncedAt;

    // ... getter & setter
}