package br.com.smartvan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SmartvanBackendApplicationTests {

	@Test
	void contextLoads() {
		// sobe o contexto inteiro: Flyway roda as migrations no H2 e o Hibernate valida o mapeamento
	}
}
