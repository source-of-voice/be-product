package org.example.sourceofvoice.services;


import org.example.sourceofvoice.DTO.requests.text.GenerateWikipediaTextsRequest;
import org.example.sourceofvoice.DTO.responses.text.GenerateWikipediaTextsResponse;
import org.example.sourceofvoice.DTO.responses.text.WikipediaExtractResponse;
import org.example.sourceofvoice.DTO.responses.text.WikipediaRandomResponse;
import org.example.sourceofvoice.entities.text.AudioText;
import org.example.sourceofvoice.entities.text.AudioTextBatch;
import org.example.sourceofvoice.entities.text.AudioTextBatchStatus;
import org.example.sourceofvoice.entities.text.AudioTextStatus;
import org.example.sourceofvoice.helper.WikipediaClient;
import org.example.sourceofvoice.repositories.AudioTextBatchRepository;
import org.example.sourceofvoice.repositories.AudioTextRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminGenerateTextService {

    private static final int DEFAULT_WIKIPEDIA_FETCH_LIMIT = 100;
    private static final int MAX_WIKIPEDIA_FETCH_LIMIT = 500;

    private final WikipediaClient wikipediaClient;
    private final AudioTextRepository audioTextRepository;
    private final TextMetricsService textMetricsService;
    private final AudioTextBatchRepository audioTextBatchRepository;

    public AdminGenerateTextService(WikipediaClient wikipediaClient, AudioTextRepository audioTextRepository, TextMetricsService textMetricsService, AudioTextBatchRepository audioTextBatchRepository){
        this.wikipediaClient = wikipediaClient;
        this.audioTextRepository = audioTextRepository;
        this.textMetricsService = textMetricsService;
        this.audioTextBatchRepository = audioTextBatchRepository;
    }

    public Mono<GenerateWikipediaTextsResponse> generateTextFromWikipedia(GenerateWikipediaTextsRequest request, Long userId){
        AudioTextBatch batch = AudioTextBatch.builder()
                .createdByAdminId(userId)
                .status(AudioTextBatchStatus.STARTED)
                .languageCode(request.getLanguageCode())
                .requestedCount(request.getRequestedCount())
                .savedCount(0)
                .skippedCount(0)
                .minWords(request.getMinWords())
                .maxWords(request.getMaxWords())
                .startedAt(LocalDateTime.now())
                .build();

        return audioTextBatchRepository.save(batch)
                .flatMap(savedBatch -> generateTexts(request, savedBatch)
                        .flatMap(result -> {
                            savedBatch.setSavedCount(result.savedCount());
                            savedBatch.setSkippedCount(result.skippedCount());
                            savedBatch.setStatus(AudioTextBatchStatus.COMPLETED);
                            savedBatch.setFinishedAt(LocalDateTime.now());

                            return audioTextBatchRepository.save(savedBatch);
                        })
                        .onErrorResume(error -> {
                            savedBatch.setStatus(AudioTextBatchStatus.FAILED);
                            savedBatch.setErrorMessage(error.getMessage());
                            savedBatch.setFinishedAt(LocalDateTime.now());

                            return audioTextBatchRepository.save(savedBatch);
                        })

                ).map(this::toResponse);
    }

    private Mono<GenerationResult> generateTexts(
            GenerateWikipediaTextsRequest request,
            AudioTextBatch batch
    ) {
        int fetchLimit = resolveFetchLimit(request);

        return wikipediaClient.getRandomPages(request.getLanguageCode(), fetchLimit)
                .map(this::extractPageIds)
                .flatMap(pageIds -> {
                    if (pageIds.isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Wikipedia returned no random pages"
                        ));
                    }

                    return wikipediaClient.getExtracts(
                            request.getLanguageCode(),
                            pageIds,
                            Boolean.TRUE.equals(request.getIntroOnly())
                    );
                })
                .flatMapMany(response -> {
                    if (response == null
                            || response.getQuery() == null
                            || response.getQuery().getPages() == null) {
                        return Flux.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Wikipedia returned no extracts"
                        ));
                    }

                    return Flux.fromIterable(response.getQuery().getPages().values());
                })
                .concatMap(page -> trySavePageAsText(page, request, batch))
                .takeUntil(new java.util.function.Predicate<Boolean>() {
                    private int savedCount = 0;

                    @Override
                    public boolean test(Boolean saved) {
                        if (Boolean.TRUE.equals(saved)) {
                            savedCount++;
                        }

                        return savedCount >= request.getRequestedCount();
                    }
                })
                .collectList()
                .map(this::calculateGenerationResult);
    }

    private GenerationResult calculateGenerationResult(List<Boolean> results) {
        int saved = 0;
        int skipped = 0;

        for (Boolean result : results) {
            if (Boolean.TRUE.equals(result)) {
                saved++;
            } else {
                skipped++;
            }
        }

        return new GenerationResult(saved, skipped);
    }

    private Mono<Boolean> trySavePageAsText(WikipediaExtractResponse.PageExtract page , GenerateWikipediaTextsRequest request, AudioTextBatch batch){

        if(page == null || page.getPageid() == null){
            return Mono.just(false);
        }

        return audioTextRepository.existsBySourcePageIdAndLanguageCode(
                page.getPageid(),
                request.getLanguageCode()
        ).flatMap( exists -> {
            if(exists) return Mono.just(false);


            String cleanedText = textMetricsService.cleanText(page.getExtract());

            if(cleanedText.isBlank()) return Mono.just(false);

            int wordCount  = textMetricsService.countWords(cleanedText);

            if (wordCount < request.getMinWords()) return Mono.just(false);


            if (wordCount > request.getMaxWords()) return Mono.just(false);

            int characterCount = textMetricsService.countCharacters(cleanedText);
            double difficultyScore = textMetricsService.calculateDifficultyScore(cleanedText);

            boolean difficultyOk = textMetricsService.difficultyMatches(
                    difficultyScore,
                    request.getMinDifficultyScore(),
                    request.getMaxDifficultyScor()
            );

            if (!difficultyOk) return Mono.just(false);

            int estimatedReadingSeconds = textMetricsService.estimateReadingSeconds(wordCount);

            var basePrice = textMetricsService.calculateBasePrice(wordCount,difficultyScore,request.getBaseRatePerWord());

            LocalDateTime now = LocalDateTime.now();

            AudioTextStatus status = Boolean.TRUE.equals(request.getActivateImmediately())
                    ? AudioTextStatus.ACTIVE
                    : AudioTextStatus.GENERATED;

            LocalDateTime activatedAt = status == AudioTextStatus.ACTIVE
                    ? now
                    : null;

            AudioText audioText = AudioText.builder()
                    .batchId(batch.getId())
                    .status(status)
                    .languageCode(request.getLanguageCode())
                    .sourcePageId(page.getPageid())
                    .sourceTitle(page.getTitle())
                    .sourceUrl(buildWikipediaUrl(request.getLanguageCode(), page.getPageid()))
                    .content(cleanedText)
                    .wordCount(wordCount)
                    .characterCount(characterCount)
                    .difficultyScore(difficultyScore)
                    .estimatedReadingSeconds(estimatedReadingSeconds)
                    .basePrice(basePrice)
                    .createdAt(now)
                    .activatedAt(activatedAt)
                    .build();

            return audioTextRepository.save(audioText)
                    .thenReturn(true)
                    .onErrorReturn(false);
        });
    }

    private String buildWikipediaUrl(String languageCode, Long pageId) {
        return "https://" + languageCode + ".wikipedia.org/?curid=" + pageId;
    }

    private List<Long> extractPageIds(WikipediaRandomResponse response){
        if (response == null) return List.of();

        if (response.getQuery() == null) return List.of();


        if (response.getQuery().getRandom() == null) return List.of();

        return response.getQuery()
                .getRandom()
                .stream()
                .map(WikipediaRandomResponse.RandomPage::getId)
                .filter(id -> id != null)
                .toList();
    }
    private int resolveFetchLimit(GenerateWikipediaTextsRequest request) {
        if (request.getWikipediaFetchLimit() == null) {
            return Math.min(request.getRequestedCount() * 5, DEFAULT_WIKIPEDIA_FETCH_LIMIT);
        }

        return Math.min(request.getWikipediaFetchLimit(), MAX_WIKIPEDIA_FETCH_LIMIT);
    }

    private record GenerationResult(
            int savedCount,
            int skippedCount
    ) {
    }


    private GenerateWikipediaTextsResponse toResponse(AudioTextBatch batch) {
        GenerateWikipediaTextsResponse response = new GenerateWikipediaTextsResponse();

        response.setBatchId(batch.getId());
        response.setStatus(batch.getStatus());
        response.setRequestedCount(batch.getRequestedCount());
        response.setSavedCount(batch.getSavedCount());
        response.setSkippedCount(batch.getSkippedCount());
        response.setStartedAt(batch.getStartedAt());
        response.setFinishedAt(batch.getFinishedAt());
        response.setErrorMessage(batch.getErrorMessage());

        return response;
    }
}
