package com.sarinah.product_bundling.controller;

//import com.sarinah.product_bundling.service.PublishEventService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/health")
//@RequiredArgsConstructor
//public class HealthController {
//    private final PublishEventService publishEventService;
//    @GetMapping("/check")
//    public Map<String, String> health() {
//        return Map.of("status", "Success");
//    }
//
//
//    @GetMapping("/kafka")
//    public Map<String, Object> kafka() {
//    publishEventService.publishToKafka();
//    return Map.of("data","success");
//    }
//}
