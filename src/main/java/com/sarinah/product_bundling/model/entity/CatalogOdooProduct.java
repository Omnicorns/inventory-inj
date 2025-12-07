package com.sarinah.product_bundling.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalogue_odoo_product")
public class CatalogOdooProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK lokal

    @Column(name = "odoo_product_id", nullable = false, unique = true)
    private Long odooProductId; // id VARIANT Odoo (85212, 85213, dst)

    @Column(name = "sku", length = 100)
    private String sku; // default_code

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "list_price", precision = 19, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "has_image")
    private Boolean hasImage;

    // ==== META TEMPLATE (dari level template) ====

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_name", length = 512)
    private String templateName;

    @Column(name = "category", length = 255)
    private String category;

    @Column(name ="product_type",length = 255)
    private String productType;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "owner", length = 255)
    private String owner;

    // attribute "Warna / Jenis"
    @Column(name = "warna_jenis", length = 100)
    private String warnaJenis;

    // =============================================

    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @ToString.Exclude
    @Builder.Default
    private List<CatalogueOdooProductStock> stocks = new ArrayList<>();

    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @Setter(AccessLevel.NONE)  // ⛔ JANGAN ada setter otomatis
    private List<CatalogOdooProductAttribute> attributes = new ArrayList<>();


    @Lob
    @Column(name = "image_base64")
    private String imageBase64;

    @Column(name = "synced_at")
    private Instant syncedAt;


// set ke product
public void clearAttributes() {
    for (CatalogOdooProductAttribute attr : this.attributes) {
        attr.setProduct(null);
    }
    this.attributes.clear();
}

    /** Tambah attribute baru dan set relasi balik ke product */
    public void addAttribute(CatalogOdooProductAttribute attr) {
        if (attr == null) return;
        attr.setProduct(this);
        this.attributes.add(attr);
    }

    /** Optional: ganti isi attributes dengan list baru (tanpa ganti instance list) */
    public void replaceAttributes(List<CatalogOdooProductAttribute> newAttributes) {
        clearAttributes();
        if (newAttributes != null) {
            for (CatalogOdooProductAttribute attr : newAttributes) {
                addAttribute(attr);  // otomatis set product
            }
        }
    }
}
