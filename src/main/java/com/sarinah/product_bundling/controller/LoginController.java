package com.sarinah.product_bundling.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage() {
        // akan render templates/login.html
        return "login";
    }
}
