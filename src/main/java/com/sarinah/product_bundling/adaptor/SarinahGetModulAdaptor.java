package com.sarinah.product_bundling.adaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

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
    private final ObjectMapper objectMapper;

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
            return JsonNodeFactory.instance.arrayNode();
        }


        if (root.isArray()) {
            return (ArrayNode) root;
        }


        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(root);
        return arr;

    }


}
