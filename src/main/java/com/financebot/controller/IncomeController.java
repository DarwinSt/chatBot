package com.financebot.controller;

import com.financebot.dto.request.IncomeCreateRequest;
import com.financebot.dto.response.IncomeResponse;
import com.financebot.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incomes")
@Validated
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @GetMapping
    public List<IncomeResponse> listAll() {
        return incomeService.findAll();
    }

    @GetMapping("/{id}")
    public IncomeResponse getById(@PathVariable Long id) {
        return incomeService.getById(id);
    }

    @PostMapping
    public ResponseEntity<IncomeResponse> create(@Valid @RequestBody IncomeCreateRequest request) {
        IncomeResponse body = incomeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
