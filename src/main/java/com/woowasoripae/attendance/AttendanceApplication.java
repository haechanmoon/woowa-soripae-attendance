package com.woowasoripae.attendance;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@ConfigurationPropertiesScan
@SpringBootApplication
public class AttendanceApplication {

	public static void main(String[] args) {
		// 서버(EC2) OS가 UTC라 기본 JVM 타임존이 UTC다. 한국시간(KST)으로 고정한다.
		// 반드시 SpringApplication.run 이전에 설정해야 한다 — 그래야 JDBC 드라이버(Connector/J)가
		// 초기화될 때 KST를 잡아, LocalDate(practice_date 등)가 저장 시 하루 밀리는 문제가 없다.
		// (@PostConstruct로 늦게 설정했더니 커넥터가 이미 UTC를 잡아 practice_date가 -1일 저장됐다.)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(AttendanceApplication.class, args);
	}

}
