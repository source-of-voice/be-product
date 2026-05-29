package org.example.sourceofvoice.services;

import org.example.sourceofvoice.entities.audio.AudioSubmission;
import org.example.sourceofvoice.entities.audio.AudioSubmissionStatus;
import org.example.sourceofvoice.entities.text.AudioText;
import org.example.sourceofvoice.entities.text.AudioTextStatus;
import org.example.sourceofvoice.helper.StoredAudioFile;
import org.example.sourceofvoice.repositories.AudioSubmissionRepository;
import org.example.sourceofvoice.repositories.AudioTextRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AudioSubmissionServiceTest {

    @Mock
    private AudioStorageService audioStorageService;

    @Mock
    private AudioSubmissionRepository audioSubmissionRepository;

    @Mock
    private AudioTextRepository audioTextRepository;

    @Mock
    private FilePart filePart;

    private AudioSubmissionService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new AudioSubmissionService(
                audioStorageService,
                audioSubmissionRepository,
                audioTextRepository
        );

        ReflectionTestUtils.setField(
                service,
                "multipartTempDirectory",
                tempDir.toString()
        );
    }

    @Test
    void submitAudio_shouldSaveSubmission_whenFileIsValid() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ACTIVE)
                .basePrice(new BigDecimal("5.00"))
                .build();

        StoredAudioFile storedFile = new StoredAudioFile(
                "audio",
                "users/1/texts/10/audio.mp3",
                "audio.mp3",
                "audio/mpeg",
                4L
        );

        AudioSubmission savedSubmission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.SUBMITTED)
                .bucketName(storedFile.getBucketName())
                .objectKey(storedFile.getObjectKey())
                .originalFileName(storedFile.getOriginalFileName())
                .contentType(storedFile.getContentType())
                .fileSizeBytes(storedFile.getFileSizeBytes())
                .payoutAmount(new BigDecimal("5.00"))
                .submittedAt(LocalDateTime.now())
                .build();

        when(audioTextRepository.findByIdAndStatus(10L, AudioTextStatus.ACTIVE))
                .thenReturn(Mono.just(text));

        when(filePart.filename()).thenReturn("audio.mp3");

        when(filePart.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            Files.write(path, new byte[]{1, 2, 3, 4});
            return Mono.empty();
        });

        when(audioStorageService.storeAudio(any(Path.class), eq("audio.mp3"), eq(1L), eq(10L)))
                .thenReturn(Mono.just(storedFile));

        when(audioSubmissionRepository.save(any(AudioSubmission.class)))
                .thenReturn(Mono.just(savedSubmission));

        StepVerifier.create(service.submitAudio(10L, 1L, filePart))
                .expectNextMatches(response ->
                        response.getId().equals(100L)
                                && response.getAudioTextId().equals(10L)
                                && response.getStatus() == AudioSubmissionStatus.SUBMITTED
                )
                .verifyComplete();

        verify(audioStorageService).storeAudio(any(Path.class), eq("audio.mp3"), eq(1L), eq(10L));
        verify(audioSubmissionRepository).save(any(AudioSubmission.class));
    }

    @Test
    void submitAudio_shouldRejectUnsupportedExtension() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ACTIVE)
                .basePrice(new BigDecimal("5.00"))
                .build();

        when(audioTextRepository.findByIdAndStatus(10L, AudioTextStatus.ACTIVE))
                .thenReturn(Mono.just(text));

        when(filePart.filename()).thenReturn("malware.exe");

        StepVerifier.create(service.submitAudio(10L, 1L, filePart))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.BAD_REQUEST
                                && "Unsupported audio file type".equals(exception.getReason())
                )
                .verify();

        verify(filePart, never()).transferTo(any(Path.class));
        verify(audioStorageService, never()).storeAudio(any(), anyString(), anyLong(), anyLong());
        verify(audioSubmissionRepository, never()).save(any());
    }

    @Test
    void submitAudio_shouldNormalizePathTraversalFilenameToBaseName() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ACTIVE)
                .basePrice(new BigDecimal("5.00"))
                .build();

        StoredAudioFile storedFile = new StoredAudioFile(
                "audio",
                "users/1/texts/10/generated-audio-name.mp3",
                "audio.mp3",
                "audio/mpeg",
                4L
        );

        AudioSubmission savedSubmission = AudioSubmission.builder()
                .id(100L)
                .userId(1L)
                .audioTextId(10L)
                .status(AudioSubmissionStatus.SUBMITTED)
                .bucketName(storedFile.getBucketName())
                .objectKey(storedFile.getObjectKey())
                .originalFileName(storedFile.getOriginalFileName())
                .contentType(storedFile.getContentType())
                .fileSizeBytes(storedFile.getFileSizeBytes())
                .payoutAmount(new BigDecimal("5.00"))
                .submittedAt(LocalDateTime.now())
                .build();

        when(audioTextRepository.findByIdAndStatus(10L, AudioTextStatus.ACTIVE))
                .thenReturn(Mono.just(text));

        when(filePart.filename()).thenReturn("../audio.mp3");

        when(filePart.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path tempPath = invocation.getArgument(0);

            assertTrue(
                    tempPath.startsWith(tempDir),
                    "Temporary file should be created inside configured temp directory"
            );

            assertFalse(
                    tempPath.toString().contains(".."),
                    "Temporary path should not contain path traversal sequence"
            );

            Files.write(tempPath, new byte[]{1, 2, 3, 4});
            return Mono.empty();
        });

        when(audioStorageService.storeAudio(any(Path.class), eq("audio.mp3"), eq(1L), eq(10L)))
                .thenReturn(Mono.just(storedFile));

        when(audioSubmissionRepository.save(any(AudioSubmission.class)))
                .thenReturn(Mono.just(savedSubmission));

        StepVerifier.create(service.submitAudio(10L, 1L, filePart))
                .expectNextMatches(response ->
                        response.getId().equals(100L)
                                && response.getAudioTextId().equals(10L)
                                && response.getStatus() == AudioSubmissionStatus.SUBMITTED
                )
                .verifyComplete();

        verify(audioStorageService).storeAudio(any(Path.class), eq("audio.mp3"), eq(1L), eq(10L));
        verify(audioSubmissionRepository).save(any(AudioSubmission.class));
    }

    @Test
    void submitAudio_shouldRejectFilenameContainingDoubleDotsInBaseName() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ACTIVE)
                .basePrice(new BigDecimal("5.00"))
                .build();

        when(audioTextRepository.findByIdAndStatus(10L, AudioTextStatus.ACTIVE))
                .thenReturn(Mono.just(text));

        when(filePart.filename()).thenReturn("audio..mp3");

        StepVerifier.create(service.submitAudio(10L, 1L, filePart))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.BAD_REQUEST
                                && "Unsupported audio file name".equals(exception.getReason())
                )
                .verify();

        verify(filePart, never()).transferTo(any(Path.class));
        verify(audioStorageService, never()).storeAudio(any(), anyString(), anyLong(), anyLong());
        verify(audioSubmissionRepository, never()).save(any());
    }

    @Test
    void submitAudio_shouldRejectEmptyFile() {
        AudioText text = AudioText.builder()
                .id(10L)
                .status(AudioTextStatus.ACTIVE)
                .basePrice(new BigDecimal("5.00"))
                .build();

        when(audioTextRepository.findByIdAndStatus(10L, AudioTextStatus.ACTIVE))
                .thenReturn(Mono.just(text));

        when(filePart.filename()).thenReturn("empty.mp3");

        when(filePart.transferTo(any(Path.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.submitAudio(10L, 1L, filePart))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.BAD_REQUEST
                                && "Audio file is empty".equals(exception.getReason())
                )
                .verify();

        verify(audioStorageService, never()).storeAudio(any(), anyString(), anyLong(), anyLong());
        verify(audioSubmissionRepository, never()).save(any());
    }

    @Test
    void submitAudio_shouldReturnNotFound_whenAudioTextIsNotActive() {
        when(audioTextRepository.findByIdAndStatus(10L, AudioTextStatus.ACTIVE))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.submitAudio(10L, 1L, filePart))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.NOT_FOUND
                                && "Requested resource not found".equals(exception.getReason())
                )
                .verify();

        verifyNoInteractions(audioStorageService);
        verify(audioSubmissionRepository, never()).save(any());
    }
}