package com.woowasoripae.attendance.domain.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * FineCalculator는 외부 의존성이 없는 순수 로직이라 목(mock) 없이 바로 생성해서 테스트할 수 있다.
 * 이런 클래스가 단위 테스트를 시작하기 가장 쉬운 대상이다.
 */
class FineCalculatorTest {

    // 실제 application.yml 값과 다를 수 있지만, 테스트에서는 "이 정책값이 주어졌을 때"의 동작만 검증하면 된다.
    private final AttendancePolicyProperties policy = new AttendancePolicyProperties(
            LocalTime.of(19, 0),
            LocalTime.of(21, 0),
            100,
            6000,
            60
    );

    private final FineCalculator fineCalculator = new FineCalculator(policy);

    @Nested
    @DisplayName("evaluateLateMinutes")
    class EvaluateLateMinutes {

        @Test
        @DisplayName("지각이 0분이면 PRESENT, 벌금 없음")
        void zeroLateMinutes_isPresent() {
            FineCalculator.Evaluation result = fineCalculator.evaluateLateMinutes(0);

            assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(result.lateMinutes()).isZero();
            assertThat(result.fineAmount()).isZero();
        }

        @ParameterizedTest(name = "{0}분 지각 -> 벌금 {1}원")
        @CsvSource({
                "1, 100",
                "30, 3000",
                "59, 5900",
        })
        @DisplayName("1~59분 지각이면 LATE, 분당 벌금이 누적된다")
        void underThreshold_isLateWithProratedFine(int lateMinutes, int expectedFine) {
            FineCalculator.Evaluation result = fineCalculator.evaluateLateMinutes(lateMinutes);

            assertThat(result.status()).isEqualTo(AttendanceStatus.LATE);
            assertThat(result.fineAmount()).isEqualTo(expectedFine);
        }

        @Test
        @DisplayName("60분(기준치) 지각이면 ABSENT, 정액 벌금")
        void exactlyThreshold_isAbsentWithFlatFine() {
            FineCalculator.Evaluation result = fineCalculator.evaluateLateMinutes(60);

            assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(result.fineAmount()).isEqualTo(6000);
        }

        @Test
        @DisplayName("기준치를 초과해도 ABSENT 정액 벌금은 동일하다")
        void wellOverThreshold_isStillFlatAbsentFine() {
            FineCalculator.Evaluation result = fineCalculator.evaluateLateMinutes(120);

            assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(result.fineAmount()).isEqualTo(6000);
        }

        @Test
        @DisplayName("음수 지각 시간은 잘못된 입력으로 거부한다")
        void negativeLateMinutes_throws() {
            assertThatThrownBy(() -> fineCalculator.evaluateLateMinutes(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("evaluateAbsent")
    class EvaluateAbsent {

        @Test
        @DisplayName("무단 결석은 지각 시간과 무관하게 ABSENT 정액 벌금")
        void noShow_isAbsentWithFlatFine() {
            FineCalculator.Evaluation result = fineCalculator.evaluateAbsent();

            assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(result.lateMinutes()).isZero();
            assertThat(result.fineAmount()).isEqualTo(6000);
        }
    }
}
