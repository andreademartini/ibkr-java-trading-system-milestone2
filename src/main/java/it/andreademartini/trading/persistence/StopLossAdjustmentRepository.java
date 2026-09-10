package it.andreademartini.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StopLossAdjustmentRepository
        extends JpaRepository<StopLossAdjustmentEntity, Long> {

    List<StopLossAdjustmentEntity> findByTradeIdOrderByCreatedAtAsc(String tradeId);

    List<StopLossAdjustmentEntity> findTop100ByOrderByCreatedAtDesc();
}
