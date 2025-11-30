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
@AllArgsConstructor
@Entity
@Table(
        name = "inventory_inj",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inj_product_location_pricelist",
                columnNames = {"odoo_product_id", "location_id", "pricelist_name"}
        )
)
public class InventoryInj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "odoo_product_id", nullable = false)
    private Long odooProductId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "product_name", length = 512)
    private String productName;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "pricelist_name", length = 255)
    private String pricelistName;

    // STOK
    @Column(name = "qty_odoo", precision = 19, scale = 3, nullable = false)
    private BigDecimal qtyOdoo;

    @Column(name = "stock_pct_to_inj", precision = 7, scale = 2, nullable = false)
    private BigDecimal stockPctToInj;

    @Column(name = "limit_stock", precision = 19, scale = 3, nullable = false)
    private BigDecimal limitStock;

    // HARGA
    @Column(name = "base_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Column(name = "pricing_mode", length = 20, nullable = false)
    private String pricingMode;

    @Column(name = "added_value_pct", precision = 7, scale = 4)
    private BigDecimal addedValuePct;

    @Column(name = "margin_inj_pct", precision = 7, scale = 4)
    private BigDecimal marginInjPct;

    @Column(name = "sell_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal sellPrice;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @PrePersist
    public void prePersist() {
        if (syncedAt == null) {
            syncedAt = Instant.now();
        }
    }

    public InventoryInj() {
    }

}
