package com.sarinah.product_bundling.service;

import tools.jackson.databind.node.ArrayNode;
import com.sarinah.product_bundling.adaptor.SarinahGetModulAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostCatalogueList {
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;

    public ArrayNode execute() {
     return sarinahGetModulAdaptor.getCatalogueCart();

    }


}
