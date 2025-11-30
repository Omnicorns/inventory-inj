package com.sarinah.product_bundling.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInjListRowResponse {
    private Long odooProductId;
    private String sku;
    private String productName;

    private Long locationCount;        // berapa lokasi punya stok
    private Long activeLocationCount;  // berapa lokasi yg punya config active = true

    private Instant lastSyncedAt;
}
