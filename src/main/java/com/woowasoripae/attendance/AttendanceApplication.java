package com.woowasoripae.attendance;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@ConfigurationPropertiesScan
@SpringBootApplication
public class AttendanceApplication {

	/**
	 * 서버(EC2)의 기본 타임존이 UTC라 LocalDateTime.now()가 한국시간보다 9시간 느리게
	 * 저장·표시되던 문제 방지. 앱 전역에서 한국시간(KST)을 쓰도록 고정한다.
	 * (예: 제출 시각이 실제보다 9시간 밀려 보이거나, 자정 근처 제출 시 날짜가 어긋나던 문제)
	 */
	@PostConstruct
	public void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(AttendanceApplication.class, args);
	}

}
