package com.woowasoripae.attendance.domain.attendance;

import org.springframework.stereotype.Component;

/**
 * Central place for the club's late/absence fine policy so the rule lives in exactly one spot:
 * - lateMinutes >= absentThresholdMinutes(60)  -> ABSENT, flat absentFine(6,000원)
 * - 0 < lateMinutes < absentThresholdMinutes    -> LATE, lateMinutes * lateFinePerMinute(100원)
 * - lateMinutes <= 0                            -> PRESENT, no fine
 */
@Component
public class FineCalculator {

    private final AttendancePolicyProperties policy;

    public FineCalculator(AttendancePolicyProperties policy) {
        this.policy = policy;
    }

    public Evaluation evaluateLateMinutes(int lateMinutes) {
        if (lateMinutes < 0) {
            throw new IllegalArgumentException("lateMinutes must not be negative");
        }
        if (lateMinutes >= policy.absentThresholdMinutes()) {
            return new Evaluation(AttendanceStatus.ABSENT, lateMinutes, policy.absentFine());
        }
        if (lateMinutes > 0) {
            return new Evaluation(AttendanceStatus.LATE, lateMinutes, lateMinutes * policy.lateFinePerMinute());
        }
        return new Evaluation(AttendanceStatus.PRESENT, 0, 0);
    }

    public Evaluation evaluateAbsent() {
        return new Evaluation(AttendanceStatus.ABSENT, 0, policy.absentFine());
    }

    public record Evaluation(AttendanceStatus status, int lateMinutes, int fineAmount) {
    }
}
