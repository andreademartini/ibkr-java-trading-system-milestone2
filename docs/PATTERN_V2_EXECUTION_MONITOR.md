# Market Data & Execution Monitor

## Integration and scope

Java 21, existing Spring services, H2/JPA `PatternPaperRepository`, IBKR event listeners and Apache POI are reused. No strategy calculations, backtest engine, configuration snapshots or Live safety controls are changed. The monitor has no order submission API.

Modified integration points: `IbkrEventListener`, `IbkrEWrapper` (tickString routing), `MarketDataService` (request/contract association), `PatternV2PaperService` (signal input and submission intent), `FrozenPatternV2Controller` and `PaperDeskExcelExporter` (additional audit sheet), `PatternPaperIbkrBroker` (reuse timestamp parser), `pattern-v2.html`.

Added components: `ExecutionMonitor`, `ExecutionMonitorStore`, `ExecutionMonitorController`, `pattern-v2-monitor.js`, `ExecutionMonitorTest`.

## Important operational limitation

The Paper lifecycle now supports frozen **1.2** with an explicit preparation command that archives prior sessions. See [Paper V2 1.2 setup](PATTERN_V2_PAPER_1_2.md). Session approval is an explicit operator confirmation bound to the connected account and epoch, not an independent IBKR attestation. Write access requires a successful non-executing WhatIf response. Actual realtime subscriptions, USD cash budget and reconciliation remain mandatory. Signal collection is gated on actual session version 1.2. No broker-connected test orders were sent during development.

## Persistence

No schema migration. Append-only records use the existing table `pattern_v1_paper_record` with kinds prefixed `V2MON_`: SIGNAL, QUOTE, INTENT, FILL, ANOMALY, STATUS, ORDER, COMMISSION, CONNECTION, ERROR, SETTINGS. Signal IDs and execution IDs provide immutable signal records and duplicate fill protection respectively. New configuration records never erase historical anomalies. Settings restore on application startup. Current single-instance write serialization is supported; cross-process deduplication relies on the database primary key and has not been load-tested.

## Semantics and known limitations

The pattern close is captured from the actual signal along with the historical OHLC input, complete session open/close, origin and generation time. Streaming market type does not prove the type of historical bars; historical source type remains UNKNOWN. Bar close is not interpreted as feed latency. Receipt time is stored separately.

IBKR Last timestamps are collected but do not qualify Bid/Ask timestamps. Quote comparison remains NOT_COMPARABLE and latency remains UNKNOWN unless timestamp provenance is implemented for the relevant price field. No Last substitution or fixed 15-minute delay assumption is used. The comparison graph is therefore withheld with a temporal warning. FROZEN and DELAYED_FROZEN remain distinct.

Signal deviation is signed, absolute and classified by LONG/SHORT favorability. Market reference slippage is separate; limit/stop comparisons are N/A. Each fill is retained individually; there is no quantity-weighted summary yet. Broker raw timestamps are retained and parsed to UTC only when an explicit timezone exists. Unparseable fill timestamps leave the UTC Excel cell blank.

Execution callbacks currently require a persisted submission intent for account/order correlation. Fills arriving without such an intent, order-ID reuse across sessions, execution corrections and historical backfill of unmatched fills need further implementation. Perm ID is recorded but not yet used as the primary recovery key. The UI does not yet expose acceptance times, commissions or the complete event history. Live/Paper mode is UNKNOWN rather than inferred from an account prefix. Reconciliation remains the responsibility of the existing Paper orchestrator; the monitor does not initiate reconnection or resubscription. A new connection handshake does not certify fresh quotes.

## Settings and access

Protected API: `/api/paper-trading/pattern-v2-monitor`; existing Paper admin session, origin and CSRF controls apply. UI thresholds default to warning 0.50%, critical 1.00%. PUT `/settings` accepts `warning`, `critical`, `blockNewPaperOrdersOnUnreliableMarketData` (default false). The optional block affects only new entries for actual version 1.2; protective operations bypass it. Quote staleness for this guard is 30 seconds. Complete stale-state classification and field-specific latency measurement remain pending.

Audit export: GET `/export`; the additional sheet is also appended to stable V2 and Paper desk exports. Audit rows in the backtest export are session observations, not the backtest's simulated fills, and have no backtest run association. Existing worksheets are retained.

## Paper observation procedure (after the session lifecycle prerequisites)

1. Use the existing approved Paper setup, verify account attestation and reconcile positions/orders/executions. Do not enable Live.
2. Confirm the operational session actually has version 1.2 and the expected frozen configuration hash.
3. Open `pattern-v2.html`, authenticate through the Paper desk, then refresh the monitor. It never starts trading.
4. Allow only normal authorized strategy signals/orders. Verify immutable OHLC close versus later fills; inspect each execution ID for partial fills.
5. Export the audit after multiple sessions. Separate feed type, missing timestamps, spread, market movement, simulated slippage and connection events. Correlation alone does not establish the cause of a deviation.

## Verification

JUnit tests run without TWS and cover type mapping, unknown timestamps/prices, threshold classifications, missing association, disconnect/reconnect, export preservation and observation defaults. This is not yet the complete 15-case acceptance suite requested; see build logs for the actual full-suite outcome.

Final verification (2026-11-12): Java 21 compilation and Maven package with tests skipped succeeded. Focused suite: 17 tests, 0 failures/errors, 1 skipped (including 13 monitor cases). Full suite: 864 tests, 1 failure, 0 errors, 21 skipped. Failure: ResearchFiltersTest.validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes reads data/pattern-v3/current.json and expects null but finds 0.906. The V3 configuration and test were not edited. JavaScript syntax check (`node --check`) passed; browser visual verification was not performed. Logs: monitor-final-tests.log, monitor-build.log, monitor-package.log.
