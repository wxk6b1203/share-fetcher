package com.github.ak.fetcher.controller;

import com.github.ak.fetcher.entity.Stock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/gg")
    public Stock test() {
        return new Stock();
    }
}
