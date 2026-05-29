package org.example.sourceofvoice.services;

import org.example.sourceofvoice.entities.audio.AudioSubmissionStatus;
import org.example.sourceofvoice.entities.text.AudioText;
import org.example.sourceofvoice.entities.text.AudioTextStatus;
import org.example.sourceofvoice.repositories.AudioSubmissionRepository;
import org.example.sourceofvoice.repositories.AudioTextRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AudioTextClosingServiceTest {

    @Mock
    private AudioSubmissionRepository audioSubmissionRepository;

    @Mock
    private AudioTextRepository audioTextRepository;

    private AudioTextClosingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AudioTextClosingService(
                audioSubmissionRepository,
                audioTextRepository,
                5
        );
    }

    @Test
    void closeTextIfLimitReached_shouldNotArchiveWhenApprovedCountIsBelowLimit() {
        when(audioSubmissionRepository.countByAudioTextIdAndStatusIn(
                eq(10L),
                any(Collection.class)
        )).thenReturn(Mono.just(4L));

        StepVerifier.create(service.closeTextIfLimitReached(10L))
                .verifyComplete();

        verify(audioTextRepository, never()).findById(anyLong());
        verify(audioTextRepository, never()).save(any());
    }

    @Test
    void closeTextIfLimitReached_shouldArchiveActiveTextWhenLimitIsReached() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ACTIVE)
                .build();

        when(audioSubmissionRepository.countByAudioTextIdAndStatusIn(
                eq(10L),
                any(Collection.class)
        )).thenReturn(Mono.just(5L));

        when(audioTextRepository.findById(10L))
                .thenReturn(Mono.just(text));

        when(audioTextRepository.save(any(AudioText.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.closeTextIfLimitReached(10L))
                .verifyComplete();

        verify(audioTextRepository).save(argThat(saved ->
                saved.getStatus() == AudioTextStatus.ARCHIVED
        ));
    }

    @Test
    void closeTextIfLimitReached_shouldNotSaveWhenTextIsAlreadyArchived() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ARCHIVED)
                .build();

        when(audioSubmissionRepository.countByAudioTextIdAndStatusIn(
                eq(10L),
                any(Collection.class)
        )).thenReturn(Mono.just(5L));

        when(audioTextRepository.findById(10L))
                .thenReturn(Mono.just(text));

        StepVerifier.create(service.closeTextIfLimitReached(10L))
                .verifyComplete();

        verify(audioTextRepository, never()).save(any());
    }
}