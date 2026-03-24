package com.financebot.controller;

import com.financebot.dto.request.DebtCreateRequest;
import com.financebot.dto.request.DebtPaymentRequest;
import com.financebot.dto.response.DebtPaymentResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.service.DebtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/debts")
@Validated
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @GetMapping
    public List<DebtResponse> listAll() {
        return debtService.findAll();
    }

    @GetMapping("/{id}")
    public DebtResponse getById(@PathVariable Long id) {
        return debtService.getById(id);
    }

    @PostMapping
    public ResponseEntity<DebtResponse> create(@Valid @RequestBody DebtCreateRequest request) {
        DebtResponse body = debtService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<DebtPaymentResponse> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody DebtPaymentRequest request) {
        DebtPaymentResponse body = debtService.registerPayment(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
