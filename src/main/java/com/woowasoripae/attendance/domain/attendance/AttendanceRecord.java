package com.woowasoripae.attendance.domain.attendance;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One member's attendance outcome for one day (a member + a date).
 * 곡이 몇 개든, 몇 시간을 하든 하루 한 번 인증하면 출석으로 인정한다.
 * scheduledStartTime은 지각을 몇 분으로 볼지 판정하는 기준 시각이다.
 * PHOTO records start life PENDING and are finalized by an officer via approve()/reject().
 * FACE_TO_FACE records are finalized immediately by decideFaceToFace().
 */
@Getter
@Entity
@Table(
        name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "practice_date"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "practice_date", nullable = false)
    private LocalDate practiceDate;

    /** 지각 판정 기준 시각. 그날 등록한 스케줄의 시작 시각이고, 등록이 없으면 코어타임이다. */
    @Column(name = "scheduled_start_time", nullable = false)
    private LocalTime scheduledStartTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(nullable = false)
    private int lateMinutes;

    @Column(nullable = false)
    private int fineAmount;

    /** Required for PHOTO, unused for FACE_TO_FACE. */
    @Column(length = 500)
    private String photoUrl;

    /** When the member uploaded the photo / when the officer face-checked them in. Attendance is judged off this. */
    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime decidedAt;

    public static AttendanceRecord createPendingPhotoSubmission(
            Member member, LocalDate practiceDate, LocalTime scheduledStartTime,
            String photoUrl, LocalDateTime submittedAt
    ) {
        AttendanceRecord record = new AttendanceRecord();
        record.member = member;
        record.practiceDate = practiceDate;
        record.scheduledStartTime = scheduledStartTime;
        record.method = AttendanceMethod.PHOTO;
        record.status = AttendanceStatus.PENDING;
        record.photoUrl = photoUrl;
        record.submittedAt = submittedAt;
        record.lateMinutes = 0;
        record.fineAmount = 0;
        return record;
    }

    public static AttendanceRecord createFaceToFaceDecision(
            Member member, LocalDate practiceDate, LocalTime scheduledStartTime,
            FineCalculator.Evaluation evaluation, LocalDateTime decidedAt
    ) {
        AttendanceRecord record = new AttendanceRecord();
        record.member = member;
        record.practiceDate = practiceDate;
        record.scheduledStartTime = scheduledStartTime;
        record.method = AttendanceMethod.FACE_TO_FACE;
        record.submittedAt = decidedAt;
        record.applyDecision(evaluation, decidedAt);
        return record;
    }

    public void applyDecision(FineCalculator.Evaluation evaluation, LocalDateTime decidedAt) {
        this.status = evaluation.status();
        this.lateMinutes = evaluation.lateMinutes();
        this.fineAmount = evaluation.fineAmount();
        this.decidedAt = decidedAt;
    }

    public void reject(LocalDateTime decidedAt) {
        if (this.method != AttendanceMethod.PHOTO) {
            throw new IllegalStateException("Only PHOTO submissions can be rejected");
        }
        this.status = AttendanceStatus.REJECTED;
        this.lateMinutes = 0;
        this.fineAmount = 0;
        this.decidedAt = decidedAt;
    }

    public boolean isPending() {
        return this.status == AttendanceStatus.PENDING;
    }
}
