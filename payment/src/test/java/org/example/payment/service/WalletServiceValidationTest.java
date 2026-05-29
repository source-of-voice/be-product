package org.example.payment.service;

import org.example.payment.DTO.request.RewardAudioPaymentRequest;
import org.example.payment.repositories.WalletRepository;
import org.example.payment.repositories.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

class WalletServiceValidationTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private TransactionalOperator transactionalOperator;

    private WalletService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new WalletService(
                walletRepository,
                walletTransactionRepository,
                databaseClient,
                transactionalOperator,
                new BigDecimal("100.00")
        );
    }

    @Test
    void rewardForAudio_shouldRejectNullRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Request body is required", exception.getReason());

        verifyNoExternalCalls();
    }

    @Test
    void rewardForAudio_shouldRejectMissingUserId() {
        RewardAudioPaymentRequest request = new RewardAudioPaymentRequest();
        request.setAudioSubmissionId(100L);
        request.setAmount(new BigDecimal("5.00"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("userId is required", exception.getReason());

        verifyNoExternalCalls();
    }

    @Test
    void rewardForAudio_shouldRejectMissingAudioSubmissionId() {
        RewardAudioPaymentRequest request = new RewardAudioPaymentRequest();
        request.setUserId(1L);
        request.setAmount(new BigDecimal("5.00"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("audioSubmissionId is required", exception.getReason());

        verifyNoExternalCalls();
    }

    @Test
    void rewardForAudio_shouldRejectMissingAmount() {
        RewardAudioPaymentRequest request = new RewardAudioPaymentRequest();
        request.setUserId(1L);
        request.setAudioSubmissionId(100L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("amount must be positive", exception.getReason());

        verifyNoExternalCalls();
    }

    @Test
    void rewardForAudio_shouldRejectNegativeAmount() {
        RewardAudioPaymentRequest request = new RewardAudioPaymentRequest();
        request.setUserId(1L);
        request.setAudioSubmissionId(100L);
        request.setAmount(new BigDecimal("-1.00"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("amount must be positive", exception.getReason());

        verifyNoExternalCalls();
    }

    @Test
    void rewardForAudio_shouldRejectZeroAmount() {
        RewardAudioPaymentRequest request = new RewardAudioPaymentRequest();
        request.setUserId(1L);
        request.setAudioSubmissionId(100L);
        request.setAmount(BigDecimal.ZERO);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("amount must be positive", exception.getReason());

        verifyNoExternalCalls();
    }

    @Test
    void rewardForAudio_shouldRejectAmountAboveMaximum() {
        RewardAudioPaymentRequest request = new RewardAudioPaymentRequest();
        request.setUserId(1L);
        request.setAudioSubmissionId(100L);
        request.setAmount(new BigDecimal("101.00"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.rewardForAudio(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("amount exceeds maximum allowed audio reward", exception.getReason());

        verifyNoExternalCalls();
    }

    private void verifyNoExternalCalls() {
        verifyNoInteractions(walletRepository);
        verifyNoInteractions(walletTransactionRepository);
        verifyNoInteractions(databaseClient);
        verifyNoInteractions(transactionalOperator);
    }
}
