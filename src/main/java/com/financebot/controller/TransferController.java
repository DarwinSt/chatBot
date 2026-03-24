package com.financebot.controller;

import com.financebot.dto.request.TransferCreateRequest;
import com.financebot.dto.response.TransferResponse;
import com.financebot.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@Validated
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    public List<TransferResponse> listAll() {
        return transferService.findAll();
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferCreateRequest request) {
        TransferResponse body = transferService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
