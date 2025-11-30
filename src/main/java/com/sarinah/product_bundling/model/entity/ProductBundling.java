package com.sarinah.product_bundling.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_bundling")
public class ProductBundling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // refer ke product Odoo / catalogue_product
    @Column(name = "odoo_product_id", nullable = false)
    private Long odooProductId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "product_name", length = 512)
    private String productName;

    // kalau aturan mau beda per lokasi / pricelist, bisa diisi
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "pricelist_name", length = 255)
    private String pricelistName;

    // CONFIG LIMIT STOCK (% stok Odoo yg boleh ke INJ)
    @Column(name = "stock_pct_to_inj", precision = 7, scale = 2)
    private BigDecimal stockPctToInj; // contoh: 80.00

    // MODE HARGA: ADDED_VALUE / MARGIN / NONE
    @Column(name = "pricing_mode", length = 20, nullable = false)
    private String pricingMode; // default nanti di service: "ADDED_VALUE" / "MARGIN" / "NONE"

    // kalau pricing_mode = ADDED_VALUE
    @Column(name = "added_value_pct", precision = 7, scale = 4)
    private BigDecimal addedValuePct;

    // kalau pricing_mode = MARGIN
    @Column(name = "margin_inj_pct", precision = 7, scale = 4)
    private BigDecimal marginInjPct;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (active == null) {
            active = Boolean.TRUE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}