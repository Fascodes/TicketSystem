package dev.fascodes.ticketSystem;

import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class TicketSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
