package com.sarinah.product_bundling.controller;

import com.sarinah.product_bundling.model.response.ProductInjListRowResponse;
import com.sarinah.product_bundling.model.response.ProductInjSpecResponse;
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

@Controller
@RequiredArgsConstructor
@RequestMapping("/catalogue-ui")
public class CatalogueUiController {

    private final ProductInjSpecService productInjSpecService;

    /**
     * Contoh akses:
     * GET /catalogue-ui/inj-spec?odooProductId=541486
     */
    @GetMapping("/inj-spec")
    public String injSpecPage(@RequestParam Long odooProductId, Model model) {

        ProductInjSpecResponse spec = productInjSpecService.getSpecForProduct(odooProductId);

        model.addAttribute("spec", spec);
        model.addAttribute("rows", spec.getRows());

        // templates/catalogue/inj-spec.html
        return "inj-spec";
    }

    @GetMapping("/inj-spec-list")
    public String injSpecListPage(
            @RequestParam(required = false) String q,              // search sku/nama
            @RequestParam(required = false) Long locationId,       // filter location
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, size);

        Page<ProductInjListRowResponse> resultPage =
                productInjSpecService.getProductInjList(q, locationId, pageable);

        // list lokasi utk dropdown filter (isi sendiri dari service/locationRepo-mu)
        model.addAttribute("locations", productInjSpecService.getAllLocations());

        model.addAttribute("page", resultPage);
        model.addAttribute("q", q);
        model.addAttribute("locationId", locationId);
        model.addAttribute("size", size);

        return "inj-spec-list";
    }
}
