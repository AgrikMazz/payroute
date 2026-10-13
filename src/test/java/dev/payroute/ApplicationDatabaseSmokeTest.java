package dev.payroute;

import dev.payroute.repository.ProviderConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationDatabaseSmokeTest {
    @Autowired ProviderConfigRepository providers;

    @Test
    void migratesSchemaAndSeedsTheSandboxProviderCatalog() {
        assertThat(providers.findAll()).hasSize(3);
    }
}
