package com.financebot.mapper;

import com.financebot.dto.response.TransferResponse;
import com.financebot.entity.Transfer;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getAmount(),
                transfer.getTransferDate(),
                transfer.getDescription(),
                transfer.getSourceAccount().getId(),
                transfer.getSourceAccount().getName(),
                transfer.getDestinationAccount().getId(),
                transfer.getDestinationAccount().getName(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }
}
