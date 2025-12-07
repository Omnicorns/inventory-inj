package com.sarinah.product_bundling.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttributeResponse {
    private String attribute;     // "Warna / Jenis"
    private String value;         // "HITAM"
    private String displayName;
}
