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
import org.springframework.http.ResponseEntity;

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
        // Baca sebagai byte[] agar Content-Type yang salah/kosong dari portal tidak menggagalkan parsing,
        // dan agar status + isi respons bisa dilaporkan jelas saat gagal.
        ResponseEntity<byte[]> resp = defaultPointRestClient
                .post()
                .uri(postProductInjUrl)
                //.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(byte[].class);

        byte[] raw = resp.getBody() != null ? resp.getBody() : new byte[0];
        if (!resp.getStatusCode().is2xx()) {
            throw new IllegalStateException("Portal product_inj membalas HTTP " + resp.getStatusCode().value()
                    + " (Content-Type=" + resp.getHeaders().getContentType() + "): " + snippet(raw));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Respons portal product_inj bukan JSON (HTTP "
                    + resp.getStatusCode().value() + ", Content-Type=" + resp.getHeaders().getContentType()
                    + "): " + snippet(raw), e);
        }


        if (root == null || root.isMissingNode()) {
            return JsonNodeFactory.instance.arrayNode();
        }


        if (root.isArray()) {
            return (ArrayNode) root;
        }


        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(root);
        return arr;

    }


    private static String snippet(byte[] raw) {
        String t = new String(raw, java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

}
