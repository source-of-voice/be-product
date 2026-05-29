package org.example.sourceofvoice.services;

import org.example.sourceofvoice.entities.audio.AudioSubmission;
import org.example.sourceofvoice.entities.audio.AudioSubmissionStatus;
import org.example.sourceofvoice.helper.PaymentClient;
import org.example.sourceofvoice.repositories.AudioSubmissionRepository;
import org.example.sourceofvoice.repositories.AudioTextRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewerAudioSubmissionServiceTest {

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private AudioSubmissionRepository audioSubmissionRepository;

    @Mock
    private AudioTextRepository audioTextRepository;

    @Mock
    private AudioStorageService audioStorageService;

    @Mock
    private AudioTextClosingService audioTextClosingService;

    private ReviewerAudioSubmissionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new ReviewerAudioSubmissionService(
                paymentClient,
                audioSubmissionRepository,
                "http://localhost:8888",
                audioTextRepository,
                audioStorageService,
                audioTextClosingService
        );
    }

    @Test
    void claimSubmission_shouldAssignSubmissionToReviewer_whenSubmissionNeedsReview() {
        AudioSubmission submission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.NEEDS_REVIEW)
                .payoutAmount(new BigDecimal("5.00"))
                .build();

        when(audioSubmissionRepository.findById(100L))
                .thenReturn(Mono.just(submission));

        when(audioSubmissionRepository.save(any(AudioSubmission.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.claimSubmission(100L, 50L))
                .expectNextMatches(response ->
                        response.getId().equals(100L)
                                && response.getStatus() == AudioSubmissionStatus.IN_REVIEW
                )
                .verifyComplete();

        verify(audioSubmissionRepository).save(argThat(saved ->
                saved.getAssignedReviewerId().equals(50L)
                        && saved.getStatus() == AudioSubmissionStatus.IN_REVIEW
                        && saved.getAssignedAt() != null
        ));
    }

    @Test
    void claimSubmission_shouldRejectAlreadyAssignedSubmission() {
        AudioSubmission submission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.NEEDS_REVIEW)
                .assignedReviewerId(99L)
                .build();

        when(audioSubmissionRepository.findById(100L))
                .thenReturn(Mono.just(submission));

        StepVerifier.create(service.claimSubmission(100L, 50L))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.CONFLICT
                )
                .verify();

        verify(audioSubmissionRepository, never()).save(any());
    }

    @Test
    void approveSubmission_shouldRejectWhenReviewerDoesNotOwnSubmission() {
        AudioSubmission submission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.IN_REVIEW)
                .assignedReviewerId(99L)
                .payoutAmount(new BigDecimal("5.00"))
                .build();

        when(audioSubmissionRepository.findById(100L))
                .thenReturn(Mono.just(submission));

        StepVerifier.create(service.approveSubmission(100L, 50L))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.FORBIDDEN
                )
                .verify();

        verify(audioSubmissionRepository, never()).save(any());
        verify(paymentClient, never()).rewardAudio(anyLong(), anyLong(), any());
    }

    @Test
    void approveSubmission_shouldRejectWhenSubmissionIsNotInReview() {
        AudioSubmission submission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.NEEDS_REVIEW)
                .assignedReviewerId(50L)
                .payoutAmount(new BigDecimal("5.00"))
                .build();

        when(audioSubmissionRepository.findById(100L))
                .thenReturn(Mono.just(submission));

        StepVerifier.create(service.approveSubmission(100L, 50L))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.CONFLICT
                )
                .verify();

        verify(audioSubmissionRepository, never()).save(any());
        verify(paymentClient, never()).rewardAudio(anyLong(), anyLong(), any());
    }

    @Test
    void approveSubmission_shouldCallPaymentAndCloseTextWhenReviewerOwnsSubmission() {
        AudioSubmission submission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.IN_REVIEW)
                .assignedReviewerId(50L)
                .payoutAmount(new BigDecimal("5.00"))
                .build();

        when(audioSubmissionRepository.findById(100L))
                .thenReturn(Mono.just(submission));

        when(audioSubmissionRepository.save(any(AudioSubmission.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(paymentClient.rewardAudio(1L, 100L, new BigDecimal("5.00")))
                .thenReturn(Mono.empty());

        when(audioTextClosingService.closeTextIfLimitReached(10L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.approveSubmission(100L, 50L))
                .expectNextMatches(response ->
                        response.getId().equals(100L)
                                && response.getStatus() == AudioSubmissionStatus.APPROVED_FOR_PAYMENT
                )
                .verifyComplete();

        verify(paymentClient).rewardAudio(1L, 100L, new BigDecimal("5.00"));
        verify(audioTextClosingService).closeTextIfLimitReached(10L);
    }

    @Test
    void rejectSubmission_shouldRejectWhenReviewerDoesNotOwnSubmission() {
        AudioSubmission submission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.IN_REVIEW)
                .assignedReviewerId(99L)
                .build();

        when(audioSubmissionRepository.findById(100L))
                .thenReturn(Mono.just(submission));

        StepVerifier.create(service.rejectSubmission(100L, 50L))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.FORBIDDEN
                )
                .verify();

        verify(audioSubmissionRepository, never()).save(any());
    }
}
