package it.andreademartini.trading.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradingStockRepository extends JpaRepository<TradingStockEntity, String> {

    @EntityGraph(attributePaths = "strategies")
    List<TradingStockEntity> findAllByOrderBySymbolAsc();
}
