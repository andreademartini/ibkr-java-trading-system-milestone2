# Validazione Trend Pullback Rebound V1 — 29 ottobre 2026

## Controlli eseguiti

- Nuovo motore: 23 test JUnit su ATR e warm-up, quattro chiusure, causalità/next open, stop nella seduta di ingresso, gap, recupero, priorità e assenza di doppie uscite, cinque sedute esatte, capitale, concorrenti, cassa disponibile, costi, dati mancanti, split, replay, persistenza ed Excel.
- Nuova integrazione HTTP: catalogo, menu, configurazione, risultati, storico, replay ed Excel; confronto delle configurazioni V1/V2/V3 prima e dopo; Mockito verifica zero `placeOrder` sul client IBKR.
- Navigazione HTTP: tutte le pagine restituiscono i tre dropdown, includono la nuova strategia e mantengono la protezione dalle vecchie pagine in cache.
- Regressioni: PatternV1PortfolioEngineTest, PatternV1AccountingTest, PatternV2Test, PatternStableTest, WeeklyTteTest, HistoricalWeeklyTteTest; V3 SignalTimeframeTest e sette metodi mirati ResearchFiltersTest (indicatori, filtri, audit, export e calendario settimanale).
- Totale test Java eseguiti con successo: **188**, nessun fallimento introdotto. Un ulteriore test FrozenV2RegressionTest risulta **saltato** perché richiede la proprietà opzionale `reorganizationRegression=true` e una run archivio di riferimento; non è dichiarato superato.
- Test JavaScript: **131 superati**, compresi cinque della nuova pagina e quelli della navigazione uniforme. Nessuna connessione IBKR o chiamata operativa durante i test della nuova strategia.

Sono controlli mirati; non viene dichiarata l'esecuzione dell'intera suite del repository. La nuova strategia non entra nell'enum operativo `StrategyType`, né nei router PAPER.

## Backtest iniziale effettivamente eseguito

Run `7cabcb2f-1d3e-4cca-ad80-1cf4c2d57bc5`, universo configurato 30 simboli, **28 strumenti utilizzabili**. BA/BAC esclusi per dataset prezzo/volume identico; la scelta è riportata nell'audit, senza attribuire arbitrariamente il dataset all'uno o all'altro simbolo.

Periodo richiesto 2017-01-01 → 2026-09-02; periodo effettivo 2017-01-03 → 2026-09-02. La fine è l'ultima data comune ai CSV disponibili: non è stata scelta in base al rendimento. Nessuna ottimizzazione dei parametri. SMA 200/5, ATR Wilder 14, stop 1,5 ATR, tre chiusure discendenti, durata cinque sedute, rischio 0,5%, posizione 30%, esposizione 100%, commissioni IBKR e slippage 0,03% per lato.

| Misura | Valore |
| --- | ---: |
| Capitale iniziale | 10.000,00 USD |
| Equity finale inclusi aperti | 13.187,51 USD |
| Trade chiusi LONG | 2.238 |
| Posizioni aperte | 6 |
| PnL netto trade chiusi | 3.223,27 USD |
| PnL netto non realizzato aperti | −35,75 USD |
| Variazione equity complessiva | 3.187,51 USD |
| Win rate trade chiusi | 58,00% |
| Profit factor netto | 1,10257 |
| CAGR sulla curva osservata | 2,90508% |
| Max drawdown DAILY | 13,43509% |
| Commissioni | 4.416,15 USD |
| Slippage già nei prezzi | 2.079,65 USD |

La riconciliazione capitale iniziale + trade chiusi + mark-to-market aperti = equity finale è superata. Gli aperti sono censurati normalmente alla fine dello snapshot; nella run a copertura comune non ci sono posizioni con `OPEN_POSITION_DATA_GAP`.

Configurazione SHA-256: `26c44808c6bad0959a4343a2ed11bc7d6627db98d49885b3623ce9ea42d6a910`.
Il fingerprint del dataset e l'elenco completo degli avvisi sono nel JSON di validazione e nel workbook.

Artifact: `outputs/trend-pullback-rebound-validation/TREND_PULLBACK_REBOUND_V1_7cabcb2f-1d3e-4cca-ad80-1cf4c2d57bc5.xlsx`, circa 1,7 MB; riaperto con Apache POI. Run/snapshot salvati nello storage della nuova strategia e visibili nella sua Run History.

È conservata anche la run precedente `17f6a4fa-7fe4-4870-b364-de75fc3ee1ed`, fino al 2026-09-08. Alcuni CSV terminavano al 2026-09-02: cinque posizioni risultavano con percorso incompleto. Tale run è segnalata come parziale e non è usata come riferimento di questa tabella.

## Limiti

Risultati descrittivi sul dataset locale, non evidenza di redditività futura o validazione scientifica. Resta il potenziale survivorship bias dell'universo attuale, la qualità delle rettifiche/prezzi/dividendi non certificata, il catalogo split limitato e l'assenza di dati intraday. I dati mancanti durante una posizione impediscono di dimostrare stop o uscita alla quinta seduta: la posizione viene segnalata come incompleta e non chiusa artificialmente. La strategia non ha facoltà di inviare ordini al broker.
