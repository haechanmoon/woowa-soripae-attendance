-- 출석 규칙 변경: "곡이 몇 개든 하루에 한 번 인증하면 출석 인정".
--
-- 1) 종료 시각을 없앤다. 항상 '시작 + 2시간'으로 채워지던 파생값이라 지워도 잃는 정보가 없고,
--    실제로는 합주가 1시간에 끝나기도 3시간 넘게 이어지기도 해서 의미가 없는 값이었다.
-- 2) 하루 한 타임 등록 / 하루 한 건 출석으로 유니크 제약을 좁힌다.
--    제약을 걸기 전에 같은 날 여러 건인 기존 데이터를 가장 이른 시각만 남기고 정리한다.

-- 같은 날 여러 타임을 등록한 부원은 가장 이른 시각만 남긴다.
DELETE ps FROM `practice_schedule` ps
JOIN (
    SELECT `member_id`, `practice_date`, MIN(`start_time`) AS keep_time
    FROM `practice_schedule`
    GROUP BY `member_id`, `practice_date`
) keeper
  ON keeper.`member_id` = ps.`member_id` AND keeper.`practice_date` = ps.`practice_date`
WHERE ps.`start_time` > keeper.keep_time;

-- 같은 날 여러 건의 출석 기록도 가장 이른 것만 남긴다.
DELETE ar FROM `attendance_record` ar
JOIN (
    SELECT `member_id`, `practice_date`, MIN(`scheduled_start_time`) AS keep_time
    FROM `attendance_record`
    GROUP BY `member_id`, `practice_date`
) keeper
  ON keeper.`member_id` = ar.`member_id` AND keeper.`practice_date` = ar.`practice_date`
WHERE ar.`scheduled_start_time` > keeper.keep_time;

ALTER TABLE `practice_schedule` DROP COLUMN `end_time`;
ALTER TABLE `attendance_record` DROP COLUMN `scheduled_end_time`;

-- 유니크 키에서 시각을 빼 (부원, 날짜) 하나로 만든다.
ALTER TABLE `practice_schedule`
    DROP INDEX `UKt6i5ilcwx3ii9vo1cou0p7bd0`,
    ADD UNIQUE KEY `uk_practice_schedule_member_date` (`member_id`, `practice_date`);

ALTER TABLE `attendance_record`
    DROP INDEX `UKi98klvmmi0frjh99o57i6aisu`,
    ADD UNIQUE KEY `uk_attendance_record_member_date` (`member_id`, `practice_date`);
