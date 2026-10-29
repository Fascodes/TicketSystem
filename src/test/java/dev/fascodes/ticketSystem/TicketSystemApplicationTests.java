package dev.fascodes.ticketSystem;

import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.config.TestJwtKeysConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, TestJwtKeysConfig.class})
class TicketSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
