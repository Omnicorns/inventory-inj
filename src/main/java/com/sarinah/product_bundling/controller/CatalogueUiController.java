package com.sarinah.product_bundling.controller;

import com.sarinah.product_bundling.model.response.*;
import com.sarinah.product_bundling.service.ProductInjSpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;


@Controller
@RequiredArgsConstructor
@RequestMapping("/catalogue-ui")
public class CatalogueUiController {

    private final ProductInjSpecService productInjSpecService;

    /**
     * Contoh akses:
     * GET /catalogue-ui/inj-spec?odooProductId=541486
     */
//    @GetMapping("/inj-spec")
//    public String injSpecPage(@RequestParam Long odooProductId,
//                              @RequestParam(required = false) Long locationId,
//                              Model model) {
//        ProductOdooInjSpecResponse spec =
//                productInjSpecService.getSpecOdooForProduct(odooProductId);
//
//        // Cari variant yang sesuai dengan odooProductId yang diklik
//        VariantOdooInjSpecResponse selectedVariant = null;
//        if (spec.getVariants() != null && !spec.getVariants().isEmpty()) {
//            selectedVariant = spec.getVariants().stream()
//                    .filter(v -> odooProductId.equals(v.getVariantId()))
//                    .findFirst()
//                    // fallback: kalau tidak ketemu, pakai varian pertama (biar tidak NPE)
//                    .orElse(spec.getVariants().get(0));
//        }
//
//        // Lokasi yang ditampilkan hanya lokasi milik selectedVariant
//        List<InjSpecOdooRowResponse> rows =
//                (selectedVariant != null && selectedVariant.getRows() != null)
//                        ? selectedVariant.getRows()
//                        : Collections.emptyList();
//
//        model.addAttribute("spec", spec);                     // info template
//        model.addAttribute("rows", rows);                     // lokasi per product (variant) ini
//        model.addAttribute("selectedVariant", selectedVariant);
//        model.addAttribute("odooProductId", odooProductId);   // dipakai JS untuk /save/inj-spec/{id}
//
//        // templates/inj-spec.html
//        return "inj-spec";
//
//
//
//    }
//
//    @GetMapping("/inj-spec-list")
//    public String injSpecListPage(
//            @RequestParam(required = false) String q,              // search sku/nama
//            @RequestParam(required = false) Long locationId,       // filter location
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            Model model
//    ) {
//        Pageable pageable = PageRequest.of(page, size);
//
//        Page<ProductOdooInjSpecResponse> resultPage =
//                productInjSpecService.getOdooProductInjList(q, locationId, pageable);
//
//        // list lokasi utk dropdown filter (isi sendiri dari service/locationRepo-mu)
//        model.addAttribute("locations", productInjSpecService.getAllLocations());
//        model.addAttribute("page", resultPage);
//        model.addAttribute("q", q);
//        model.addAttribute("locationId", locationId);
//        model.addAttribute("size", size);
//
//        return "inj-spec-list";
//    }
    @GetMapping("/inj-spec")
    public String injSpecPage(@RequestParam Long odooProductId,
                              @RequestParam(required = false) Long locationId,
                              Model model) {

        ProductOdooInjSpecResponse spec =
                productInjSpecService.getSpecOdooForProduct(odooProductId);

        // Varian yang diklik dipakai sebagai "anchor" untuk header (gambar, sku default)
        VariantOdooInjSpecResponse selectedVariant = null;
        if (spec.getVariants() != null && !spec.getVariants().isEmpty()) {
            selectedVariant = spec.getVariants().stream()
                    .filter(v -> odooProductId.equals(v.getVariantId()))
                    .findFirst()
                    .orElse(spec.getVariants().get(0));
        }

        // ⬇️ SEKARANG KIRIM SEMUA VARIANTS (bukan cuma rows 1 variant)
        List<VariantOdooInjSpecResponse> variants =
                (spec.getVariants() != null) ? spec.getVariants() : Collections.emptyList();

        model.addAttribute("spec", spec);                   // info template (parent)
        model.addAttribute("variants", variants);           // semua child variants
        model.addAttribute("selectedVariant", selectedVariant);
        model.addAttribute("odooProductId", odooProductId); // varian yg diklik (untuk back-compat)

        return "inj-spec";
    }

    @GetMapping("/inj-spec-list")
    public String injSpecListPage(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, size);

        Page<ProductOdooInjSpecResponse> resultPage =
                productInjSpecService.getOdooProductInjList(q, locationId, pageable);

        model.addAttribute("locations", productInjSpecService.getAllLocations());
        model.addAttribute("page", resultPage);
        model.addAttribute("q", q);
        model.addAttribute("locationId", locationId);
        model.addAttribute("size", size);

        return "inj-spec-list";
    }


}
