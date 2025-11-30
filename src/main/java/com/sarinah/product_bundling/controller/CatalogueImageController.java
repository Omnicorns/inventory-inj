package com.sarinah.product_bundling.controller;

import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import com.sarinah.product_bundling.repository.CatalogueProductRepository;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/catalogue")
public class CatalogueImageController {
    private final CatalogueProductRepository catalogueProductRepository;

    @GetMapping("/{id}/image")
    public void getImage(
            @PathVariable Long id,
            HttpServletResponse response
    ) throws IOException {

        CatalogueProduct product = catalogueProductRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // kalau tidak ada image
        if (product.getHasImage() == null || !product.getHasImage()
                || product.getImageBase64() == null || product.getImageBase64().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }

        // ambil base64
        String base64 = product.getImageBase64();

        // kalau ternyata disimpan full "data:image/png;base64,xxxxx"
        if (base64.startsWith("data:")) {
            int commaIdx = base64.indexOf(',');
            if (commaIdx > 0) {
                base64 = base64.substring(commaIdx + 1);
            }
        }

        byte[] imageBytes;
        try {
            imageBytes = java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid image base64");
        }

        // set content-type (sesuaikan kalau JPEG)
        response.setContentType("image/png");
        response.setContentLength(imageBytes.length);

        // tulis ke output stream
        try (ServletOutputStream os = response.getOutputStream()) {
            os.write(imageBytes);
            os.flush();
        }
    }
}
