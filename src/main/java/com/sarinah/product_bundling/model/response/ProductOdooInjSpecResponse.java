package com.sarinah.product_bundling.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductOdooInjSpecResponse {
    private Long templateId;
    private String templateName;
    private Long productId;
    private String productName;
    private Boolean image;        // kalau mau tanda ada gambar parent
    private String productType;   // "product"
    private String category;
    private String brand;
    private String ownerId;

    private Long locationCount;        // berapa lokasi punya stok
    private Long activeLocationCount;  // berapa lokasi yg punya config active = true

    private Instant lastSyncedAt;

    // ===== VARIANTS =====
    private List<VariantOdooInjSpecResponse> variants;
}