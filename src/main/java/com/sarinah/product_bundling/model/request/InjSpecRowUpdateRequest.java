package com.sarinah.product_bundling.model.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InjSpecRowUpdateRequest {
    @NotNull(message = "locationId wajib diisi")
    private Long locationId;

    /** NONE / ADDED_VALUE / MARGIN (kosong dianggap NONE). */
    @Pattern(regexp = "NONE|ADDED_VALUE|MARGIN", message = "pricingMode harus NONE, ADDED_VALUE, atau MARGIN")
    private String pricingMode;

    @DecimalMin(value = "0", message = "addedValuePct tidak boleh negatif")
    @DecimalMax(value = "1000", message = "addedValuePct maksimal 1000")
    @Digits(integer = 4, fraction = 4, message = "addedValuePct tidak valid")
    private BigDecimal addedValuePct;

    // Harga jual MARGIN = harga dasar / (1 - margin/100): margin 100% atau lebih menyebabkan pembagian nol / harga negatif.
    @DecimalMin(value = "0", message = "marginInjPct tidak boleh negatif")
    @DecimalMax(value = "99.99", message = "marginInjPct harus kurang dari 100")
    @Digits(integer = 2, fraction = 4, message = "marginInjPct tidak valid")
    private BigDecimal marginInjPct;

    /** Batas stok dalam QTY (bukan persen). */
    @DecimalMin(value = "0", message = "limit stok tidak boleh negatif")
    @DecimalMax(value = "1000000", message = "limit stok terlalu besar")
    @Digits(integer = 7, fraction = 2, message = "limit stok tidak valid")
    private BigDecimal stockPctToInj;

    private Boolean active;

}
