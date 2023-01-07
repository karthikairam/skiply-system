package com.skiply.system.payment.infrastructure.apiclient;

import com.skiply.system.common.domain.model.valueobject.PaymentReferenceNumber;
import com.skiply.system.payment.infrastructure.apiclient.dto.PaymentGatewayRequest;
import com.skiply.system.payment.infrastructure.apiclient.dto.PaymentGatewayResponse;
import com.skiply.system.payment.infrastructure.apiclient.dto.PaymentGatewayStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;
import java.util.Set;

public interface PaymentGatewayProvider {
    PaymentGatewayResponse makePayment(PaymentGatewayRequest request);
}

@RequiredArgsConstructor
@Component
class MockPaymentGatewayProvider implements PaymentGatewayProvider {

    private static final Set<String> supportedCardTypes = Set.of("MC", "VI");

    private final ReferenceNumberGenerator referenceNumberGenerator;

    @Override
    public PaymentGatewayResponse makePayment(PaymentGatewayRequest request) {
        final var responseBuilder = PaymentGatewayResponse.builder();
        if(!supportedCardTypes.contains(request.cardType())) {
            responseBuilder.status(PaymentGatewayStatus.FAILED)
                    .failureReason("CardType " + request.cardType() + "is not supported");
        } else {
            responseBuilder.status(PaymentGatewayStatus.SUCCESS)
                    .referenceNumber(new PaymentReferenceNumber(referenceNumberGenerator.generateReceiptNumber()));
        }

        return responseBuilder.build();
    }
}

@Component
class ReferenceNumberGenerator {

    private final long nodeId = new Random().nextLong();
    private long counter;

    public ReferenceNumberGenerator() {
        this.counter = 0;
    }

    public synchronized String generateReceiptNumber() {
        long timestamp = Instant.now().toEpochMilli();
        return Long.toString((timestamp << 32) | (nodeId << 16) | counter++);
    }
}

