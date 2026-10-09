# PATTERN_V2 — manual research lab

PATTERN_V1 remains the frozen benchmark. The only V1 edit in this delivery is
the link to `/pattern-v2.html`. Its Java source, YAML mapping, export, frontend
JavaScript and existing results are unchanged from the start of this task.

## Architecture and isolation

V2 uses its own immutable `PatternV2Configuration`, controller, service,
repository, run envelope and Excel exporter. The configuration owns global
parameters and independent LONG/SHORT rows; immutable copies are made when the
V1 baseline is loaded. There is no shared mutable configuration or position state.

`ConfiguredPatternStrategy` invokes the existing injected Research detectors.
The menu is derived from those detector beans, including their direction, rather
than hardcoding the V1 selection. Entry/stop choices list the existing algorithms
NEXT_BAR_OPEN and PATTERN_EXTREME; no new pattern algorithm was invented.

`PatternPortfolioSimulator` is the configurable V2 simulation kernel. Because
V1 cannot be refactored under the frozen constraint, the accounting orchestration
is a controlled port of the frozen kernel. It preserves entry/exit order, short
collateral, closed-bar detection, marked equity and cash reconciliation, while
parameterizing holding periods, buffer, timeframe, same-bar policy and sizing.
V1 is deliberately not routed through this new kernel. Parity tests compare the
entire trade payload (apart from ID/strategy), equity curve, cash and metrics.

The stateless `PatternV1PositionSizing`, `PositionSizingService`,
`PatternV1Commission` and `PatternV1MetricsCalculator` are reused without edits.
The V1-named records are immutable shared data contracts, not V1 configuration or
runtime state. V2 trade and portfolio identifiers are PATTERN_V2, and the V2-owned
run also stores execution prices/cost breakdowns, the exact configuration and analyses.

## Persistence and APIs

All V2 writes go to `data/pattern-v2`:

- `default.json`: independent baseline captured on first initialization.
- `current.json`: current V2 configuration.
- `configurations/<UUID>.json`: named saved configurations and timestamps.
- `runs/<UUID>.json`: run configuration, results, timestamp and optional V1 snapshot.

File names are UUIDs, never configuration names. Writes use temporary files and
replacement; invalid configurations are rejected before saving/execution.

API prefix: `/api/backtest/pattern-v2`.

| Action | Endpoint |
| --- | --- |
| Read/update current | GET/PUT `/configuration` |
| Supported patterns | GET `/patterns` |
| Copy current frozen V1 settings into V2 | POST `/baseline` |
| Restore independent V2 default | POST `/reset` |
| Named configurations | GET/POST `/configurations`, GET `/configurations/{id}` |
| Simulate | POST `/run` with complete configuration |
| History / result | GET `/runs`, GET `/runs/{id}` |
| Read-only V1 comparison | POST `/runs/{id}/benchmark` |
| Target analysis filters | GET `/runs/{id}/analysis?minTarget=3` or `exactTarget=4` |
| Excel | GET `/runs/{id}/export/excel` |

Benchmark execution calls the frozen engine directly with disposable inputs and
never invokes V1 persistence. Only the V2 run gains the benchmark snapshot. V1
uses DAILY, frozen mapping/settings, and the V2 run's initial capital and requested
period. Both actual coverages are displayed; intraday V2 is compared against DAILY V1.

## Parameters and semantics

Initial values copy the effective current V1 defaults: capital/risk/notional
limits from the risk configuration, CORE mapping from V1, maximum quantity
1,000,000, max order 60% of initial capital, IBKR_US_FIXED commissions and 0.03%
slippage per execution. V2 edits never write those source settings.

Global and optional symbol overrides apply at entry. Symbol Risk %, Max Order %,
Max Quantity and Holding Bars use global values when blank. Max order modes:

- FIXED_INITIAL_CAPITAL_PERCENT: initial capital × percent / 100.
- CURRENT_EQUITY_PERCENT: equity known at that entry's open × percent / 100.

A nonblank global absolute Max Order Value replaces the global percentage with
a fixed amount. A symbol percentage override takes precedence over that absolute
amount. Other notional/position/quantity/cash limits still constrain the order.

Commission and slippage can be disabled independently. IBKR_US_FIXED uses the
user-specified formula `min(max(qty × perShare, minimum), notional × maxPercent / 100)`
on each execution. SHORT execution slippage lowers the entry fill and raises the
exit fill; LONG does the reverse. For parity with V1, theoretical fills determine
price R/gross PnL, while slippage is deducted from net PnL as a separate cost.
Open positions pay entry costs only. Prices after slippage and monetary cost
breakdowns are displayed and exported.

Daily, hourly and 5-minute intervals are supported when their historical data
are available. Today's incomplete data are excluded. Intraday marks use exact
timestamps and next-bar execution; time in market represents available bar
sessions in that case. Entry Mode and Stop Mode only expose implemented choices.

Analysis filters do not change configuration or simulate an optimized portfolio.
Equity/dashboard/period stability remain the full run. Period returns use the
continuous portfolio, local peaks for drawdown and trades assigned by exit date
for PF/expectancy/win rate. Group drawdowns are marked PnL attribution, not
independently funded symbol portfolios. Contribution uses total net PnL; it is
undefined when total PnL is zero. PF is undefined/null when there are no losses.

## Page description

`/pattern-v2.html` has a violet RESEARCH header, an immediately visible frozen V1
notice and nine section links. Global parameters, named configuration controls
and the editable symbol table precede a live simulation preview and RUN button.
The results section contains the metric dashboard and V1 comparison, sortable
symbol/target/pattern tables, high-R filters, stability periods/yearly selection,
violet V2 equity with an optional teal V1 overlay, a separate drawdown chart,
execution details, and run history with multi-run comparison.

The Excel file is `PATTERN_V2_<runId>.xlsx` with SUMMARY, SYMBOL_RESULTS, TRADES,
EQUITY_CURVE, CONFIGURATION, RESULTS_BY_TARGET_R, RESULTS_BY_PATTERN,
RESULTS_BY_SYMBOL, PERIOD_ANALYSIS and RUN_INFO. CONFIGURATION flattens every
global parameter and every symbol/direction/override from the actual run snapshot.

## Validation evidence

Full Maven suite: 258 tests, zero failures/errors, one pre-existing optional
local-validation test skipped. Frontend suite: 37 passing tests. Both backend
package and frontend build completed successfully. The final backend package
also includes the last frontend sorting adjustment.

The freeze audit compared 103 V1 files against the snapshot captured before this
delivery. Only `pattern-v1.html` changed, for the navigation link. Existing V1
results, Java sources, tests and configuration retain their original hashes.
Evidence is recorded in `outputs/pattern-v2-evidence/frozen-after.json` and
`outputs/pattern-v2-evidence/test-summary.json`. Earlier uncommitted V1 work from
previous requests was preserved as the frozen starting point.

The parity suite covers STOP, TARGET, TIMEOUT and OPEN for LONG and SHORT.
For example, the synthetic AAPL three-bar STOP fixture with 10,000 initial
capital, 60% cap, 2% risk, IBKR fees and 0.03% slippage gives both V1 and V2:

- 60 shares, theoretical entry 100 and stop 98.
- Gross PnL −120; commissions 2; slippage 3.564; total costs 5.564.
- Final cash/equity 9,874.436; net PnL −125.564.

Changing V2 Target R from 3 to 4 changes its theoretical target from 106 to 108;
the V1 configuration remains byte-for-byte equal in the test. Disabling AAPL only
removes V2 trades. Initial-capital mode keeps the second order at 60 shares;
current-equity mode permits 62 after the first profitable trade. HTTP tests verify
saved configuration, result/history, export, invalid requests, reset and that V1
store.save is never called during comparison.

Additional tests cover directional execution, sizing/holding overrides, minimum
quantity, commission/slippage toggles, unfavorable LONG/SHORT slippage, same-bar
policy, buffer, intraday timestamps, persistence isolation and exact Excel values.
Frontend tests cover cloning, validation, preview, custom targets and numeric sorting.

## Files in this delivery

Created under `src/main/java/it/andreademartini/trading/backtest/patternv2/`:
`PatternV2Configuration.java`, `ConfiguredPatternStrategy.java`,
`PatternPortfolioSimulator.java`, `PatternV2Run.java`, `PatternV2Analysis.java`,
`PatternV2Store.java`, `PatternV2Service.java`, `PatternV2Controller.java`,
`PatternV2ExcelExporter.java`.

Created frontend: `pattern-v2.html`, `pattern-v2.css`, `pattern-v2-model.js`,
`pattern-v2.js` under `src/main/resources/static/`.

Created tests: `PatternV2Test.java`, `PatternV2HttpIntegrationTest.java` under
`src/test/java/it/andreademartini/trading/backtest/patternv2/`, and
`src/test/js/pattern-v2.test.js`.

Modified: `package.json` (V2 syntax checks in frontend build),
`src/main/resources/static/index.html`, `research.html`, `pattern-v1.html`
(navigation links only). This document is new. Local hash/build evidence lives
under `outputs/pattern-v2-evidence/`; those are work artifacts, not application dependencies.

## Split-aware per-share cost correction (NVDA)

V2 now snapshots a `splitCosts` calendar separately from the frozen V1. New
configurations and legacy configurations used for a new run receive the NVDA
calendar by default. Existing saved results remain unchanged and are labelled
LEGACY when they have no split-cost snapshot. Loading them for a new simulation
shows the current calendar, while their original dashboard/export stays historical.

The initial NVDA calendar covers data from 2010 in the share basis of the
2024 split: 4:1 effective 2021-07-20 and 10:1 effective 2024-06-10. Issuer sources:
[2021 announcement](https://nvidianews.nvidia.com/news/nvidia-announces-four-for-one-stock-split-pending-stockholder-approval-at-annual-meeting-set-for-june-3)
and [2024 FAQ](https://investor.nvidia.com/files/doc_downloads/2024/06/nvidia-2024-stock-split_faq_investors.pdf).
The factor is 40 before 2021-07-20, 10 before 2024-06-10, then 1. It is based on
the dataset's adjustment basis, never the requested end date of the backtest.

For each execution, equivalent shares = dataset quantity / dated split factor.
The per-share commission and other fixed per-share costs are divided by the
factor before sizing, cash accounting and reporting. Commission minimum, fixed
per-order commission and the percentage order-value cap are unchanged. Percentage
slippage uses the same notional and is not divided again. Entry and exit can have
different factors when a trade spans a split. AUTO_PROVIDER applies the explicit
calendar to STOOQ or ADJUSTED bars; the local STOOQ import currently labels its
already-adjusted CSV RAW. Explicit RAW bypasses correction for truly raw data.
No price threshold or price-gap heuristic is used.

The UI contains an editable split calendar table: symbol, price basis, supported
start date, dataset adjustment-through date and dated ratios. The initial verified
catalog covers NVDA only. Other symbols require their verified calendars and CSV
basis; changing a calendar never changes imported OHLC or V1. The calendar is
immutable, persisted with configurations/runs and fully exported in CONFIGURATION.
TRADES displays/exports entry/exit factors, equivalent quantities and prices with
the split adjustment reversed. These reconstructed prices still retain any
dividend adjustment in the source. Equivalent quantities may be fractional;
whole-share historical lot rounding is not modeled. Sizing quantities, maxQuantity,
tick-size buffers and prices remain in dataset units.

Example: dataset price 0.405164 and quantity 9,205 in 2010 become an equivalent
price 16.20656 and 230.125 shares for per-share costs. IBKR commission is 1.150625
USD for that execution, with the normal minimum and cap still applied.

An exact OHLC-snapshot replay of user run
`d3ab5b3c-4b72-4888-a86c-010d343a5f95` reproduced its legacy summary before the
correction. NVDA realized results:

| Metric | Legacy | Split-aware V2 |
|---|---:|---:|
| Closed trades | 151 | 150 |
| Gross PnL | 11,685.887780 | 12,546.673855 |
| Commissions | 5,460.52613021 | 381.23884899 |
| Slippage | 443.1128711460 | 490.2496951275 |
| Net PnL | 5,782.2487786440 | 11,675.1853108825 |

The correction is inside the simulation, so cash availability, quantity, trades
and gross PnL can change; it is not a post-processing discount on old trade fees.
Replay evidence and an Excel workbook live in `outputs/pattern-v2-evidence/`.
The original run and frozen V1 were not overwritten. V1 comparisons intentionally
retain the original benchmark cost semantics, including this known limitation.

Additional files: `PatternV2SplitCosts.java` and `PatternV2SplitCostsTest.java`.
Regression tests cover the reported NVDA quantities, minimum/cap/fixed fees, split
effective dates, raw-data bypass, older dataset bases, LONG/SHORT cash reconciliation,
an entry/exit across a split, persisted snapshots, Excel audit columns and legacy
deserialization. Frontend tests cover calendar parsing and invalid dates/ratios.

## Pattern Range Filter experiment

V2 alone owns immutable fields `patternRangeFilterEnabled` (default OFF) and
`minimumPatternRangePct` (default 2.50). Legacy configurations receive these defaults;
legacy results stay historical and show unavailable range research rather than inferred outcomes.

The filter runs after PATTERN_DETECTED and before the pending entry queue, using
(High - Low) / signal Close * 100 over the detector's inclusive pattern window.
Threshold comparison uses exact decimal cross multiplication, without display rounding.
V1 and shared detectors, sizing, cost and metric calculators are unchanged.

An enabled run creates an independent V2_BASELINE on the same normalized configuration
and exact input bars. The pair stores reciprocal IDs and a SHA-256 dataset snapshot
under data/pattern-v2/datasets, including decimal OHLC, timestamps, provider and adjustment basis.
POST /api/backtest/pattern-v2/runs/{id}/replay executes from that snapshot without a data refresh.
The paired baseline never replaces the current V2 configuration or calls V1 persistence.

Accepted/rejected research cohorts use actual baseline trades, including their original
quantity and costs. Signals without a baseline trade have no economic outcome. OPEN outcomes
remain unmeasured. Actual filtered portfolio metrics, symbol rows and periods are separate.
Range buckets can show either baseline or the actual run. Existing price-R expectancy excludes
costs; PF Net includes costs. Period Net PnL is the continuous equity change, not a newly funded backtest.

Excel adds PATTERN_RANGE_ANALYSIS, PATTERN_RANGE_SIGNALS and, for enabled runs,
BASELINE_COMPARISON, along with range columns in TRADES and SYMBOL_RESULTS.
CONFIGURATION exports PatternRangeFilterEnabled and MinimumPatternRangePct.
Run history displays enabled status and threshold, and loading a run restores its configuration.

The first experiment and full 17-metric/four-period comparison are in
[the research report](../outputs/pattern-range-evidence/REPORT.md).
The local opt-in JUnit harness is PatternRangeExperimentTest, enabled by
-DpatternRangeExperiment=true; it consumes captured configuration-before.json,
before-run.json and local CSVs and checks exact OFF parity before running the pair.
Manual future thresholds use normal V2 configuration and history comparison; no optimizer exists.


## CloseStrength (V2 only)

Controlli nella sezione **Filters / Configuration**, accanto a Pattern Range: **Enable Close Strength Filter** (OFF iniziale) e **Maximum Close Strength** (0.80 iniziale, decimali da 0 a 1 inclusi). I nuovi campi JSON sono closeStrengthFilterEnabled e maxCloseStrength; i default vengono applicati anche alle configurazioni precedenti. Preset, configurazione corrente, Run History ed Excel conservano entrambi i valori.

LONG: (PatternClose − PatternLow) / (PatternHigh − PatternLow). SHORT: (PatternHigh − PatternClose) / (PatternHigh − PatternLow). Usa la finestra già riconosciuta dal detector; nessuna modifica a pattern, entry, stop, target, sizing, timeout o costi. L'accettazione inclusiva è verificata con prodotti BigDecimal, evitando che l'arrotondamento della divisione cambi il confronto sulla soglia.

Range resta il primo filtro, indipendente da CloseStrength. Un rifiuto Range ha la priorità e stato CloseStrength SKIPPED_PATTERN_RANGE. Se CloseStrength è OFF, lo stato è DISABLED e non introduce rifiuti. Quando ON, un superamento della soglia produce REJECTED_CLOSE_STRENGTH / CLOSE_STRENGTH_FILTER; range ≤ 0 o close fuori range produce INVALID_CLOSE_STRENGTH_DATA e valore N/D. Nessun ordine viene creato per questi segnali. Lo stato INVALID_DATA è conteggiato separatamente dai rifiuti per soglia.

Signals Evaluated include soltanto ACCEPTED, REJECTED e INVALID_DATA quando il filtro è ON e Range è passato. OFF presenta zero evaluated e acceptance rate N/D: non significa che abbia eliminato segnali. Total Detected Signals comprende tutti i pattern riconosciuti. Signals Accepted conta segnali che passano CloseStrength, non necessariamente trade eseguiti.

Il pannello **CLOSE STRENGTH FILTER ANALYSIS** mostra statistiche, confronto con baseline, fasce, risultati per Symbol + Direction + Pattern + Target R e audit dei segnali. Le fasce hanno limite inferiore incluso/superiore escluso, eccetto l'ultima che include 1.00. Le metriche dei bucket e delle combinazioni usano trade chiusi; Trades Accepted conta anche eventuali trade ancora aperti. Wins/losses e PF usano PnL netto; Expectancy R e Average Winner/Loser R restano il R di prezzo già utilizzato dal progetto, esclusi i costi. I bucket della simulazione filtrata sono separati dai trade reali della baseline senza filtri. Per rejected senza trade chiuso baseline non si attribuisce un risultato.

**MANUAL FILTER EXPERIMENTS**: selezionare una baseline senza filtri con dataset salvato, poi A / B / C / D per copiare l'intera configurazione e cambiare solo nome/filtri. SAVE PRESET salva senza modificare la configurazione corrente; RUN ON BASELINE DATA riesegue manualmente sullo stesso snapshot. Nessuna modalità avvia automaticamente un optimizer. I preset C e D richiesti sono salvati; D non è stato eseguito.

API aggiunte:
- POST /api/backtest/pattern-v2/configurations/preset: salva un preset senza cambiare current.json.
- POST /api/backtest/pattern-v2/runs/{id}/replay: body facoltativo con configurazione. Con body, permette soltanto modifiche a nome/filtri, usando il dataset dello snapshot e la baseline compatibile senza filtri. Senza body conserva il replay precedente.
- POST /api/backtest/pattern-v2/runs/compare: body {"runIds":["idA","idB",...]}. Rifiuta ID duplicati, dataset mancanti/diversi e parametri di trading diversi. Restituisce 17 metriche e quattro periodi per run; l'interfaccia permette il confronto A/B/C/D dalla Run History.

Excel mantiene i fogli esistenti, aggiunge le colonne CloseStrength in TRADES / CONFIGURATION / SYMBOL_RESULTS e i fogli CLOSE_STRENGTH_ANALYSIS e FILTERED_SIGNALS. I risultati legacy restano leggibili senza riscrittura; le informazioni CloseStrength non registrate sono N/D.

Verifiche e risultati del primo test: [report](../outputs/close-strength-evidence/REPORT.md). Il replay storico ha confermato parità con la V2 precedente a filtro OFF, sia con Range OFF sia con Range ON, su tutti i campi dei trade eccetto gli ID generati, equity, costi e audit Range. Hash di 318 file non V2 invariati, inclusi V1/detector/risk/accounting condivisi. Baseline e run Range precedenti conservate byte per byte.

Il test storico opt-in CloseStrengthExperimentTest viene eseguito solo con -DcloseStrengthExperiment=true. Crea il test C sullo snapshot fissato in outputs/close-strength-evidence/baseline-before.json, salva il preset D senza eseguirlo e scrive risultati/export nella stessa directory. Non parte nella suite ordinaria.
