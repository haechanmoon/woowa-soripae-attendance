-- Baseline snapshot of the schema as it existed before Flyway was introduced.
-- Existing databases are marked at this version via spring.flyway.baseline-on-migrate
-- (see application.properties) and never actually execute this file.
-- A brand-new database (e.g. a fresh local setup) runs this to bootstrap the schema.

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `position` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `part` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `practice_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `end_time` time NOT NULL,
  `practice_date` date NOT NULL,
  `start_time` time NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt6i5ilcwx3ii9vo1cou0p7bd0` (`member_id`,`practice_date`,`start_time`),
  CONSTRAINT `FKen5s7p90e2w79n3llsbk0pnbv` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `attendance_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `fine_amount` int NOT NULL,
  `late_minutes` int NOT NULL,
  `method` enum('FACE_TO_FACE','PHOTO') COLLATE utf8mb4_unicode_ci NOT NULL,
  `photo_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `practice_date` date NOT NULL,
  `scheduled_end_time` time NOT NULL,
  `scheduled_start_time` time NOT NULL,
  `status` enum('ABSENT','LATE','PENDING','PRESENT','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi98klvmmi0frjh99o57i6aisu` (`member_id`,`practice_date`,`scheduled_start_time`),
  CONSTRAINT `FK4sgtenhqtqrb4ot3v91km3xes` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
