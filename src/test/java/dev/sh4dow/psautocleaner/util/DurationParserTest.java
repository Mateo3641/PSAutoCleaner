package dev.sh4dow.psautocleaner.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    @Test
    void testParseBasicUnits() {
        assertThat(DurationParser.parse("45s")).isEqualTo(Duration.ofSeconds(45));
        assertThat(DurationParser.parse("3m")).isEqualTo(Duration.ofMinutes(3));
        assertThat(DurationParser.parse("5h")).isEqualTo(Duration.ofHours(5));
        assertThat(DurationParser.parse("30d")).isEqualTo(Duration.ofDays(30));
        assertThat(DurationParser.parse("2w")).isEqualTo(Duration.ofDays(14));
        assertThat(DurationParser.parse("1mo")).isEqualTo(Duration.ofDays(30));
        assertThat(DurationParser.parse("1y")).isEqualTo(Duration.ofDays(365));
    }

    @Test
    void testParseBareIntegerDefaultsToDays() {
        assertThat(DurationParser.parse("30")).isEqualTo(Duration.ofDays(30));
        assertThat(DurationParser.parse("7")).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void testParseCompoundDurations() {
        assertThat(DurationParser.parse("1d 12h 30m"))
                .isEqualTo(Duration.ofDays(1).plusHours(12).plusMinutes(30));
        assertThat(DurationParser.parse("5m 20s"))
                .isEqualTo(Duration.ofMinutes(5).plusSeconds(20));
    }

    @Test
    void testParseCaseInsensitiveAndWhitespace() {
        assertThat(DurationParser.parse("  3M  ")).isEqualTo(Duration.ofMinutes(3));
        assertThat(DurationParser.parse("10D")).isEqualTo(Duration.ofDays(10));
        assertThat(DurationParser.parse("2 hours")).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void testParseInvalidInputs() {
        assertThatThrownBy(() -> DurationParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurationParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurationParser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurationParser.parse("abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurationParser.parse("30xyz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFormatShort() {
        assertThat(DurationParser.formatShort(Duration.ZERO)).isEqualTo("0s");
        assertThat(DurationParser.formatShort(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(DurationParser.formatShort(Duration.ofMinutes(3))).isEqualTo("3m");
        assertThat(DurationParser.formatShort(Duration.ofMinutes(3).plusSeconds(15))).isEqualTo("3m 15s");
        assertThat(DurationParser.formatShort(Duration.ofDays(30))).isEqualTo("30d");
        assertThat(DurationParser.formatShort(Duration.ofDays(2).plusHours(5))).isEqualTo("2d 5h");
    }

    @Test
    void testFormatVerbose() {
        assertThat(DurationParser.formatVerbose(Duration.ZERO)).isEqualTo("0 seconds");
        assertThat(DurationParser.formatVerbose(Duration.ofMinutes(3))).isEqualTo("3 minutes");
        assertThat(DurationParser.formatVerbose(Duration.ofDays(1).plusHours(1))).isEqualTo("1 day, 1 hour");
        assertThat(DurationParser.formatVerbose(Duration.ofDays(2).plusHours(4))).isEqualTo("2 days, 4 hours");
    }
}
