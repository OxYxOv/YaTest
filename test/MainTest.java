import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @Test
    void readPortUsesDefaultWhenArgumentMissing() {
        assertEquals(8080, Main.readPort(new String[0]));
    }

    @Test
    void readPortReadsProvidedValue() {
        assertEquals(9090, Main.readPort(new String[]{"--port", "9090"}));
    }

    @Test
    void overlapDetectionHandlesTouchingRangesAsNonOverlap() {
        LocalDateTime fromA = LocalDateTime.parse("2026-01-01T10:00:00");
        LocalDateTime toA = LocalDateTime.parse("2026-01-01T11:00:00");
        LocalDateTime fromB = LocalDateTime.parse("2026-01-01T11:00:00");
        LocalDateTime toB = LocalDateTime.parse("2026-01-01T12:00:00");

        assertFalse(Main.isOverlapping(fromA, toA, fromB, toB));
    }

    @Test
    void overlapDetectionHandlesIntersectingRanges() {
        LocalDateTime fromA = LocalDateTime.parse("2026-01-01T10:00:00");
        LocalDateTime toA = LocalDateTime.parse("2026-01-01T11:30:00");
        LocalDateTime fromB = LocalDateTime.parse("2026-01-01T11:00:00");
        LocalDateTime toB = LocalDateTime.parse("2026-01-01T12:00:00");

        assertTrue(Main.isOverlapping(fromA, toA, fromB, toB));
    }
}
