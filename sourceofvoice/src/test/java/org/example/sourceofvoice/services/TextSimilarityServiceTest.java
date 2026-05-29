package org.example.sourceofvoice.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextSimilarityServiceTest {

    private final TextSimilarityService service = new TextSimilarityService();

    @Test
    void calculateScore_shouldReturnOneHundredForIdenticalTextIgnoringCaseAndPunctuation() {
        double score = service.calculateScore(
                "Ala ma kota!",
                "ala ma kota"
        );

        assertEquals(100.0, score, 0.01);
    }

    @Test
    void calculateScore_shouldReturnZeroWhenExpectedTextIsBlank() {
        double score = service.calculateScore("", "jakis tekst");

        assertEquals(0.0, score, 0.01);
    }

    @Test
    void calculateScore_shouldReturnZeroWhenTranscriptTextIsNull() {
        double score = service.calculateScore("tekst oczekiwany", null);

        assertEquals(0.0, score, 0.01);
    }

    @Test
    void calculateScore_shouldReturnLowerScoreForDifferentText() {
        double score = service.calculateScore(
                "ala ma kota",
                "zupełnie inny tekst"
        );

        assertTrue(score >= 0.0);
        assertTrue(score < 70.0);
    }
}
