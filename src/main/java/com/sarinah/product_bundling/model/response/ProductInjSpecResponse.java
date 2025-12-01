package com.sarinah.product_bundling.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductInjSpecResponse {
    private Long odooProductId;
    private String sku;
    private String productName;
    private String imageBase64;              // untuk gambar di bawah tabel

    private List<InjSpecRowResponse> rows;
}
