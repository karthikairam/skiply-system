package com.skiply.system.payment.api.mapper;

import com.skiply.system.common.domain.model.valueobject.Money;
import com.skiply.system.payment.api.payment.CollectPaymentCommand;
import com.skiply.system.payment.domain.model.CardDetail;
import com.skiply.system.payment.domain.model.PaymentTransaction;
import com.skiply.system.payment.domain.model.PurchaseItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentTransactionMapper {

    public PaymentTransaction commandToDomainModel(CollectPaymentCommand command) {
        return PaymentTransaction.builder()
                .studentId(command.studentId())
                .paidBy(command.paidBy())
                .idempotencyKey(command.idempotencyKey())
                .cardDetail(prepareCardDetail(command.cardDetail()))
                .totalPrice(new Money(command.totalPrice()))
                .purchaseItems(preparePurchaseItem(command.purchaseItems()))
                .build();
    }

    private CardDetail prepareCardDetail(CollectPaymentCommand.CardDetail cardDetail) {
        return CardDetail.builder()
                .cardExpiry(cardDetail.cardExpiry())
                .cardCvv(cardDetail.cardCvv())
                .cardNumber(cardDetail.cardNumber())
                .cardType(cardDetail.cardType())
                .build();
    }

    private List<PurchaseItem> preparePurchaseItem(List<CollectPaymentCommand.PurchaseItem> purchaseItems) {
        return purchaseItems.stream()
                .map(this::commandToPurchaseItem).toList();
    }

    private PurchaseItem commandToPurchaseItem(CollectPaymentCommand.PurchaseItem purchaseItemCommand) {
        return PurchaseItem.builder()
                .price(new Money(purchaseItemCommand.price()))
                .name(purchaseItemCommand.name())
                .quantity(purchaseItemCommand.quantity())
                .feeType(purchaseItemCommand.feeType())
                .build();
    }
}
