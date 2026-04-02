package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Transaction;
import com.infinitematters.bookkeeping.service.CategorizerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CategorizeController {

    private final CategorizerService service;

    public CategorizeController(CategorizerService service) {
        this.service = service;
    }

    @PostMapping("/categorize")
    public CategorizationResult categorize(@RequestBody Transaction t) {
        return service.categorize(t);
    }
}
