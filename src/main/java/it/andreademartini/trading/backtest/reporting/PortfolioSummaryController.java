package it.andreademartini.trading.backtest.reporting;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import it.andreademartini.trading.backtest.export.BacktestRunRegistry;
/** The workbench uses the same saved registry result as Excel, never the current form. */
@RestController
public class PortfolioSummaryController {
 private final BacktestRunRegistry runs;
 public PortfolioSummaryController(BacktestRunRegistry runs){this.runs=runs;}
 @GetMapping("/api/backtest/{id}/portfolio-summary")
 public PortfolioSummaryDto summary(@PathVariable String id){return PortfolioSummaryMapper.from(runs.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Run unavailable")));}
}
