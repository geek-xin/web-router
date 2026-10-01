package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseVersionComparatorTest {

    @Test
    void comparesNumericSegmentsInsteadOfLexicographicOrder() {
        assertThat(ReleaseVersionComparator.isNewer("1.10.0", "1.9.0")).isTrue();
        assertThat(ReleaseVersionComparator.isNewer("1.9.0", "1.10.0")).isFalse();
        assertThat(ReleaseVersionComparator.isNewer("2.0.0", "1.99.99")).isTrue();
        assertThat(ReleaseVersionComparator.isNewer("1.3", "1.3.0")).isFalse();
        assertThat(ReleaseVersionComparator.isNewer("1.3.1", "1.3")).isTrue();
    }

    @Test
    void ignoresVersionPrefixWhenComparing() {
        assertThat(ReleaseVersionComparator.isNewer("v1.4.0", "1.3.0")).isTrue();
        assertThat(ReleaseVersionComparator.isNewer("V1.4.0", "v1.3.0")).isTrue();
        assertThat(ReleaseVersionComparator.normalize(" v1.4.0 ")).isEqualTo("1.4.0");
        assertThat(ReleaseVersionComparator.compare("v1.3.0", "1.3.0")).isZero();
    }

    @Test
    void treatsPrereleaseAsOlderThanTheMatchingStableRelease() {
        assertThat(ReleaseVersionComparator.isNewer("1.4.0", "1.4.0-rc.1")).isTrue();
        assertThat(ReleaseVersionComparator.isNewer("1.4.0-rc.1", "1.4.0")).isFalse();
        assertThat(ReleaseVersionComparator.isNewer("1.4.0-rc.2", "1.4.0-rc.1")).isTrue();
    }

    @Test
    void unknownOrMalformedVersionsNeverBeatAConcreteVersion() {
        assertThat(ReleaseVersionComparator.isNewer("unknown", "1.3.0")).isFalse();
        assertThat(ReleaseVersionComparator.isNewer("", "1.3.0")).isFalse();
        assertThat(ReleaseVersionComparator.isNewer("1.3.0", "unknown")).isTrue();
    }
}
