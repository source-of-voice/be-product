package org.example.sourceofvoice.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TextMetricsServiceTest {

    private final TextMetricsService service = new TextMetricsService();

    @Test
    void cleanText_shouldRemoveWikipediaReferencesAndNormalizeWhitespace() {
        String result = service.cleanText("  To jest tekst[12]   z   Wikipedii.  ");

        assertEquals("To jest tekst z Wikipedii.", result);
    }

    @Test
    void countWords_shouldReturnZeroForBlankText() {
        assertEquals(0, service.countWords("   "));
        assertEquals(0, service.countWords(null));
    }

    @Test
    void countWords_shouldCountWordsSeparatedByWhitespace() {
        assertEquals(4, service.countWords("To jest prosty tekst"));
    }

    @Test
    void estimateReadingSeconds_shouldUseWordsPerMinute() {
        assertEquals(60, service.estimateReadingSeconds(140));
        assertEquals(30, service.estimateReadingSeconds(70));
    }

    @Test
    void calculateBasePrice_shouldUseWordCountDifficultyAndRate() {
        BigDecimal result = service.calculateBasePrice(
                10,
                1.5,
                new BigDecimal("0.20")
        );

        assertEquals(new BigDecimal("3.00"), result);
    }

    @Test
    void difficultyMatches_shouldRespectMinAndMaxBounds() {
        assertTrue(service.difficultyMatches(1.5, 1.0, 2.0));
        assertFalse(service.difficultyMatches(0.9, 1.0, 2.0));
        assertFalse(service.difficultyMatches(2.1, 1.0, 2.0));
    }
}
