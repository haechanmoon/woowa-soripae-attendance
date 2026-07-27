-- 마감(일요일)이 지난 뒤 이번 주 스케줄을 바꾼 흔적을 남긴다.
--
-- 변경 자체는 승인 없이 바로 반영된다. 막아두면 "못 가게 됐어요"가 조용한 회피가 되고,
-- 승인을 기다리게 하면 합주를 다녀와도 인증을 못 하기 때문이다. 대신 사유와 함께 여기 쌓여 임원이 사후에 본다.
--
-- 기존 테이블과 데이터는 건드리지 않는다. 이 파일은 새 테이블을 만들기만 한다.

CREATE TABLE `schedule_change_log` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`           BIGINT       NOT NULL,
    `practice_date`       DATE         NOT NULL,
    -- 바꾸기 전 시각. 뒤늦게 새로 등록한 경우 NULL.
    `previous_start_time` TIME         NULL,
    -- 바꾼 뒤 시각. 등록을 취소한 경우 NULL.
    `new_start_time`      TIME         NULL,
    `reason`              VARCHAR(200) NOT NULL,
    `created_at`          DATETIME(6)  NULL,
    `updated_at`          DATETIME(6)  NULL,
    PRIMARY KEY (`id`),
    KEY `idx_schedule_change_log_practice_date` (`practice_date`),
    CONSTRAINT `fk_schedule_change_log_member`
        FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
