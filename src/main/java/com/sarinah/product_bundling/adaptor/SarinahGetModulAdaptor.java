package com.sarinah.product_bundling.adaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.json.JsonMapper;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class SarinahGetModulAdaptor {

    @Value("${sarinah-portal.product_inj.url}")
    private String postProductInjUrl;

    private final RestClient defaultPointRestClient;
    private final JsonMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(SarinahGetModulAdaptor.class);


    public ArrayNode getCatalogueCart() {
        JsonNode root = defaultPointRestClient
                .post()
                .uri(postProductInjUrl)
                //.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);              // baca sebagai JsonNode


        if (root == null) {
            return objectMapper.createArrayNode();
        }


        if (root.isArray()) {
            return (ArrayNode) root;
        }


        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(root);
        return arr;

    }


}
