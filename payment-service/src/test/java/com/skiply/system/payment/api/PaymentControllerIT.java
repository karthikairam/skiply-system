package com.skiply.system.payment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skiply.system.common.api.jackson.error.dto.ErrorDTO;
import com.skiply.system.common.api.jackson.error.handler.GlobalExceptionHandler;
import com.skiply.system.common.domain.model.valueobject.PaymentTransactionStatus;
import com.skiply.system.common.messaging.kafka.message.payment.PaymentSuccessMessage;
import com.skiply.system.payment.TestKafkaServerConfiguration;
import com.skiply.system.payment.api.payment.CollectPaymentResponse;
import com.skiply.system.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@Import(TestKafkaServerConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
@DirtiesContext
@EmbeddedKafka
class PaymentControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentTransactionRepository repository;

    @Autowired
    private ConsumerFactory<String, PaymentSuccessMessage> consumerFactory;

    private KafkaMessageListenerContainer<String, PaymentSuccessMessage> container;
    private BlockingQueue<ConsumerRecord<String, PaymentSuccessMessage>> queue;

    @BeforeEach
    void setup() {
        queue = new LinkedBlockingQueue<>();
        ContainerProperties containerProperties = new ContainerProperties("payment-success-response");
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener((MessageListener<String, PaymentSuccessMessage>) queue::add);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);
    }

    @AfterEach
    void cleanUp() {
        container.stop();
        repository.deleteAll();
    }

    @Test
    @Transactional
    void givenValidPaymentRequestThenSystemResponseWithSuccess() throws Exception {
        var result = mockMvc.perform(post("/v1/payments")
                        .contentType("application/json")
                        .accept("application/json")
                        .content("""
                                {
                                        "studentId": "98989899",
                                        "paidBy": "Karthik",
                                        "idempotencyKey": "20220102123520210",
                                        "cardDetail": {
                                                "cardNumber": "54021928179322",
                                                "cardType": "MC",
                                                "cardCvv": "9465",
                                                "cardExpiry": "01/31"
                                        },
                                        "totalPrice": 150,
                                        "purchaseItems": [
                                                {
                                                        "feeType": "Tuition",
                                                        "name": "KG2",
                                                        "quantity": 3,
                                                        "price": 50
                                                }
                                        ]
                                }
                                """)
                )
                .andExpect(status().isCreated())
                .andExpectAll(
                        jsonPath("$.paymentReferenceNumber").exists(),
                        jsonPath("$.status").value(PaymentTransactionStatus.COMPLETED.name())
                ).andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), CollectPaymentResponse.class);

        var entity = repository.findByPaymentReferenceNumber(response.paymentReferenceNumber());
        assertThat(entity.isPresent()).isTrue();
        entity.ifPresentOrElse(transactionEntity -> {
            assertThat(transactionEntity.getStatus()).isEqualTo(PaymentTransactionStatus.COMPLETED);
            assertThat(transactionEntity.getPurchaseItems()).isNotEmpty();
            assertThat(transactionEntity.getIdempotencyKey()).isNotNull();
        }, () -> fail("Entity has not saved."));

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> queue.size() > 0);

        var record = queue.poll();
        assertThat(record).isNotNull();
        assertThat(record.key()).isNotNull();
        assertThat(record.value()).isNotNull();
        assertThat(record.value().studentId()).isEqualTo(entity.get().getStudentId());
        assertThat(record.value().transactionDetail().paymentReferenceNumber())
                .isEqualTo(entity.get().getPaymentReferenceNumber());
    }

    @Test
    void givenBadPaymentRequestThenSystemResponseWithError() throws Exception {
        var result = mockMvc.perform(post("/v1/payments")
                        .contentType("application/json")
                        .accept("application/json")
                        .content("""
                                {
                                        "studentId": "",
                                        "paidBy": "Karthik",
                                        "idempotencyKey": "20220102123520210",
                                        "cardDetail": {
                                                "cardNumber": "54021928179322",
                                                "cardType": "MC",
                                                "cardCvv": "9465",
                                                "cardExpiry": "01/31"
                                        },
                                        "totalPrice": 150,
                                        "purchaseItems": [
                                                {
                                                        "feeType": "Tuition",
                                                        "name": "KG2",
                                                        "quantity": 3,
                                                        "price": 50
                                                }
                                        ]
                                }
                                """)
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorDTO.class);
        assertThat(response.code()).isEqualTo(GlobalExceptionHandler.CLIENT_ERROR);
        assertThat(response.messages()).contains("studentId: Invalid StudentId");

        var entities = repository.findAll();
        assertThat(entities.size()).isZero();

        Thread.sleep(100);
        assertThat(queue.size()).isZero();
    }

    @Test
    void givenInvalidCardBadPaymentRequestThenSystemResponseWithError() throws Exception {
        var result = mockMvc.perform(post("/v1/payments")
                        .contentType("application/json")
                        .accept("application/json")
                        .content("""
                                {
                                        "studentId": "83748738",
                                        "paidBy": "Karthik",
                                        "idempotencyKey": "20220102123520210",
                                        "cardDetail": {
                                                "cardNumber": "54021928179322",
                                                "cardType": "AE",
                                                "cardCvv": "9465",
                                                "cardExpiry": "01/31"
                                        },
                                        "totalPrice": 150,
                                        "purchaseItems": [
                                                {
                                                        "feeType": "Tuition",
                                                        "name": "KG2",
                                                        "quantity": 3,
                                                        "price": 50
                                                }
                                        ]
                                }
                                """)
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorDTO.class);
        assertThat(response.code()).isEqualTo(GlobalExceptionHandler.CLIENT_ERROR);
        assertThat(response.messages()).contains("CardType AE is not supported");

        var entities = repository.findAll();
        assertThat(entities.size()).isZero();

        Thread.sleep(100);
        assertThat(queue.size()).isZero();
    }
}