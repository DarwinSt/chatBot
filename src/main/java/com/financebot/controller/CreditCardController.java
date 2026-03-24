package com.financebot.controller;

import com.financebot.dto.request.CreditCardCreateRequest;
import com.financebot.dto.request.CreditCardPaymentRequest;
import com.financebot.dto.response.CreditCardPaymentResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.service.CreditCardService;
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
@RequestMapping("/api/credit-cards")
@Validated
public class CreditCardController {

    private final CreditCardService creditCardService;

    public CreditCardController(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @GetMapping
    public List<CreditCardResponse> listAll() {
        return creditCardService.listAll();
    }

    @GetMapping("/{id}")
    public CreditCardResponse getById(@PathVariable Long id) {
        return creditCardService.getById(id);
    }

    @PostMapping
    public ResponseEntity<CreditCardResponse> create(@Valid @RequestBody CreditCardCreateRequest request) {
        CreditCardResponse body = creditCardService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<CreditCardPaymentResponse> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody CreditCardPaymentRequest request) {
        CreditCardPaymentResponse body = creditCardService.registerPayment(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
