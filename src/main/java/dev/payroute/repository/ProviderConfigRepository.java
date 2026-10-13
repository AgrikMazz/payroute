package dev.payroute.repository;

import dev.payroute.domain.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, String> {}
