package it.andreademartini.trading.orders;

import it.andreademartini.trading.config.ProgressiveStopMoveTarget;
import it.andreademartini.trading.config.TradingSystemProperties;
import it.andreademartini.trading.strategy.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ProgressiveStopLossService {

    private static final Logger log =
            LoggerFactory.getLogger(ProgressiveStopLossService.class);
    private static final String INCREASE_RISK_REJECTION =
            "NEW STOP WOULD INCREASE RISK";

    private final TradingSystemProperties properties;

    public ProgressiveStopLossService(TradingSystemProperties properties) {
        this.properties = properties;
    }

    public StopAdjustmentResult onTakeProfitExecution(
            TradeStopState trade,
            TakeProfitLevel takeProfitLevel,
            BigDecimal filledQuantity,
            boolean takeProfitFullyFilled) {
        validate(trade, takeProfitLevel);
        long newStopQuantity = Math.max(0, trade.remainingQuantity());

        if (newStopQuantity == 0) {
            StopAdjustmentResult result = StopAdjustmentResult.cancelled(
                    trade.tradeId(),
                    trade.currentStopPrice(),
                    trade.currentStopQuantity(),
                    takeProfitLevel);
            logAdjustment(trade, result, filledQuantity, takeProfitFullyFilled);
            return result;
        }

        TradingSystemProperties.ProgressiveStopProperties config =
                properties.getRisk().getProgressiveStop();
        if (!config.isEnabled()
                || (config.isMoveOnlyAfterFullTpFill() && !takeProfitFullyFilled)) {
            StopAdjustmentResult result = quantityOnlyResult(
                    trade,
                    takeProfitLevel,
                    newStopQuantity);
            logAdjustment(trade, result, filledQuantity, takeProfitFullyFilled);
            return result;
        }

        ProgressiveStopMoveTarget moveTarget = moveTarget(config, takeProfitLevel);
        BigDecimal candidateStop = stopPriceForTarget(trade, moveTarget);
        if (candidateStop == null) {
            StopAdjustmentResult result = quantityOnlyResult(
                    trade,
                    takeProfitLevel,
                    newStopQuantity);
            logAdjustment(trade, result, filledQuantity, takeProfitFullyFilled);
            return result;
        }

        StopLossAdjustmentReason reason = reason(takeProfitLevel);
        if (!improvesRisk(trade.direction(), trade.currentStopPrice(), candidateStop)) {
            StopAdjustmentResult result = StopAdjustmentResult.rejected(
                    trade.tradeId(),
                    trade.currentStopPrice(),
                    candidateStop,
                    trade.currentStopQuantity(),
                    newStopQuantity,
                    reason,
                    takeProfitLevel,
                    INCREASE_RISK_REJECTION);
            logRejection(trade, result, candidateStop);
            logAdjustment(trade, result, filledQuantity, takeProfitFullyFilled);
            return result;
        }

        StopAdjustmentResult result = StopAdjustmentResult.changed(
                trade.tradeId(),
                trade.currentStopPrice(),
                candidateStop,
                trade.currentStopQuantity(),
                newStopQuantity,
                reason,
                takeProfitLevel);
        logAdjustment(trade, result, filledQuantity, takeProfitFullyFilled);
        return result;
    }

    public boolean improvesRisk(
            TradeDirection direction,
            BigDecimal currentStopPrice,
            BigDecimal newStopPrice) {
        if (currentStopPrice == null || newStopPrice == null) {
            return false;
        }
        return direction == TradeDirection.LONG
                ? newStopPrice.compareTo(currentStopPrice) >= 0
                : newStopPrice.compareTo(currentStopPrice) <= 0;
    }

    private StopAdjustmentResult quantityOnlyResult(
            TradeStopState trade,
            TakeProfitLevel takeProfitLevel,
            long newStopQuantity) {
        if (trade.currentStopQuantity() == newStopQuantity) {
            return StopAdjustmentResult.noChange(
                    trade.tradeId(),
                    trade.currentStopPrice(),
                    trade.currentStopQuantity(),
                    takeProfitLevel);
        }
        return StopAdjustmentResult.changed(
                trade.tradeId(),
                trade.currentStopPrice(),
                trade.currentStopPrice(),
                trade.currentStopQuantity(),
                newStopQuantity,
                StopLossAdjustmentReason.QUANTITY_UPDATE_AFTER_PARTIAL_FILL,
                takeProfitLevel);
    }

    private ProgressiveStopMoveTarget moveTarget(
            TradingSystemProperties.ProgressiveStopProperties config,
            TakeProfitLevel level) {
        return switch (level) {
            case TP1 -> config.getAfterTp1().getMoveTo();
            case TP2 -> config.getAfterTp2().getMoveTo();
            case TP3 -> config.getAfterTp3().getMoveTo();
        };
    }

    private BigDecimal stopPriceForTarget(
            TradeStopState trade,
            ProgressiveStopMoveTarget target) {
        return switch (target) {
            case ENTRY -> trade.entryPrice();
            case TP1 -> trade.takeProfitPrice(TakeProfitLevel.TP1).orElse(null);
            case TP2 -> trade.takeProfitPrice(TakeProfitLevel.TP2).orElse(null);
        };
    }

    private StopLossAdjustmentReason reason(TakeProfitLevel level) {
        return switch (level) {
            case TP1 -> StopLossAdjustmentReason.TP1_TO_BREAK_EVEN;
            case TP2 -> StopLossAdjustmentReason.TP2_TO_TP1;
            case TP3 -> StopLossAdjustmentReason.TP3_TO_TP2;
        };
    }

    private void validate(TradeStopState trade, TakeProfitLevel takeProfitLevel) {
        if (trade == null) {
            throw new IllegalArgumentException("trade non puo' essere null");
        }
        if (trade.direction() == null) {
            throw new IllegalArgumentException("TradeDirection obbligatoria");
        }
        if (takeProfitLevel == null) {
            throw new IllegalArgumentException("TakeProfitLevel obbligatorio");
        }
        if (trade.initialQuantity() < 0 || trade.remainingQuantity() < 0
                || trade.currentStopQuantity() < 0) {
            throw new IllegalArgumentException("Quantita' trade non valide");
        }
        if (trade.currentStopQuantity() > trade.initialQuantity()) {
            throw new IllegalArgumentException(
                    "Stop quantity superiore alla quantita' iniziale");
        }
    }

    private void logAdjustment(
            TradeStopState trade,
            StopAdjustmentResult result,
            BigDecimal filledQuantity,
            boolean takeProfitFullyFilled) {
        if (!result.hasPersistableAdjustment()) {
            return;
        }
        log.info("""
                ========== STOP ADJUSTMENT ==========
                Trade: {}
                Direction: {}
                Reason: {}
                Old Stop: {}
                New Stop: {}
                Old Quantity: {}
                New Quantity: {}
                {}: {} {}
                =====================================
                """,
                trade.tradeId(),
                trade.direction(),
                result.reason(),
                result.oldStopPrice(),
                result.newStopPrice(),
                result.oldQuantity(),
                result.newQuantity(),
                result.takeProfitLevel(),
                filledQuantity,
                takeProfitFullyFilled ? TakeProfitStatus.FILLED : TakeProfitStatus.PARTIALLY_FILLED);
    }

    private void logRejection(
            TradeStopState trade,
            StopAdjustmentResult result,
            BigDecimal rejectedStopPrice) {
        log.warn("""
                ========== STOP UPDATE REJECTED ==========
                Trade: {}
                Direction: {}
                Reason: {}
                Current Stop: {}
                Rejected Stop: {}
                Old Quantity: {}
                New Quantity: {}
                ==========================================
                """,
                trade.tradeId(),
                trade.direction(),
                result.rejectionReason(),
                result.oldStopPrice(),
                rejectedStopPrice,
                result.oldQuantity(),
                result.newQuantity());
    }
}
