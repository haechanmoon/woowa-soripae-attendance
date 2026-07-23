-- 곡별 합주 시간 조율(when2meet-lite): 보컬이 후보 시간을 올리면 팀원들이 가능/불가능을 표시하고, 보컬이 최종 확정한다.

CREATE TABLE `song` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `artist` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `song_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `song_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_song_member` (`song_id`,`member_id`),
  CONSTRAINT `fk_song_member_song` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`),
  CONSTRAINT `fk_song_member_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `rehearsal_poll` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `song_id` bigint NOT NULL,
  `created_by_member_id` bigint NOT NULL,
  `status` enum('OPEN','CONFIRMED','CANCELED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `confirmed_slot_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_rehearsal_poll_song` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`),
  CONSTRAINT `fk_rehearsal_poll_creator` FOREIGN KEY (`created_by_member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `rehearsal_slot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `poll_id` bigint NOT NULL,
  `start_at` datetime(6) NOT NULL,
  `end_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_rehearsal_slot_poll` FOREIGN KEY (`poll_id`) REFERENCES `rehearsal_poll` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `rehearsal_poll`
  ADD CONSTRAINT `fk_rehearsal_poll_confirmed_slot` FOREIGN KEY (`confirmed_slot_id`) REFERENCES `rehearsal_slot` (`id`);

CREATE TABLE `rehearsal_response` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `slot_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `availability` enum('AVAILABLE','UNAVAILABLE') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rehearsal_response` (`slot_id`,`member_id`),
  CONSTRAINT `fk_rehearsal_response_slot` FOREIGN KEY (`slot_id`) REFERENCES `rehearsal_slot` (`id`),
  CONSTRAINT `fk_rehearsal_response_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
