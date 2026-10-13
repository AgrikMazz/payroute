package dev.payroute.repository;

import dev.payroute.domain.FeeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FeeRuleRepository extends JpaRepository<FeeRule, UUID> {}
