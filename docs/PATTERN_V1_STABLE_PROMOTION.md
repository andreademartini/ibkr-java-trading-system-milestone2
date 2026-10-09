Aggiornamento 2026-10-09: la versione ufficiale corrente e 2.1, con timeout a 13 barre. La 2.0 descritta qui resta congelata a 20 barre e conserva la propria equivalenza con V2. Dettagli in [PATTERN_V1_STABLE_2.1.md](PATTERN_V1_STABLE_2.1.md).

La configurazione ufficiale V1 è PATTERN_V1_STABLE, versione 2.0, promossa dalla run V2 `10aae805-4987-4a29-a634-b3819cddc427`. La promozione non attiva alcun servizio operativo.

Architettura verificata prima dell'intervento:

- La vecchia V1 ha un motore autonomo (`PatternV1PortfolioEngine`), proprietà `pattern-v1`, API e archivio `data/pattern-v1/backtests`. I risultati contengono configurazione, impostazioni applicate e serie prezzi.
- V2 ha configurazioni immutabili, un motore configurabile, un archivio indipendente `data/pattern-v2`, snapshot dei dati identificati da SHA-256 e analisi/export dedicati.
- `StrategyType.PATTERN_V1` è escluso dall'esecuzione operativa sia da `StrategyEngine` sia da `TradingWorkbenchController`. Questa esclusione resta in vigore. Non sono stati aggiunti identificativi operativi o autorizzazioni SHORT.
- Il riferimento comprende costi IBKR US Fixed e base di costo NVDA corretta per gli split. Non sono stati usati i costi predefiniti della vecchia V1 in loro sostituzione.

La nuova API ufficiale è `/api/backtest/pattern-v1`. `/configuration` restituisce versione, snapshot, hash e quattro flag di esecuzione tutti false; `/run` accetta soltanto un corpo vuoto. Una modifica a capitale, date, costi, simboli o altri parametri viene rifiutata. I parametri richiedono una nuova versione esplicita. `/runs` distingue le versioni; `/{id}/metadata` espone tutti i metadati delle nuove run.

Il percorso originale resta disponibile sotto `/api/backtest/pattern-v1/legacy`, con la stessa logica finanziaria. Gli URL storici `/{id}` e `/{id}/export/excel` sul percorso ufficiale continuano a risolvere gli identificativi legacy. Nessuna run viene rinominata. La pagina `pattern-v1-legacy.html` usa l'API legacy.

Il nuovo archivio append-only `data/pattern-v1-stable` contiene:

- `versions/PATTERN_V1_LEGACY.json`: snapshot della configurazione precedente e del rischio globale, con etichetta legacy e identificativo storico PATTERN_V1.
- `versions/2.0.json`: configurazione completa e metadati della nuova versione ufficiale.
- `references/10aae805-4987-4a29-a634-b3819cddc427.json`: copia della run di riferimento.
- `datasets/190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd.json`: copia dei dati esatti usati dalla run.
- `runs/<UUID>.json`: nuovi risultati, senza sovrascrittura. Il contenitore identifica PATTERN_V1_STABLE 2.0; il portafoglio e i trade identificano PATTERN_V1.

I vecchi file, gli Excel e gli identificativi rimangono al loro posto. La verifica SHA-256 dei file persistiti esistenti è in `outputs/v1-promotion-evidence/preservation.json`. I risultati storici più vecchi non vengono ricalcolati o convertiti retroattivamente: conservano il loro schema originale. Non si attribuisce loro una nuova versione del motore.

Il motore finanziario 2.0 è congelato sotto `backtest/patternstable/engine`: calendario del portafoglio, ordine sequenziale, riconoscimento dei cinque pattern, ingresso, uscita, sizing, prezzi, limiti e trattamento degli split sono snapshot del percorso validato. La duplicazione di questo percorso è intenzionale: le future modifiche sperimentali V2 non devono alterare una versione stabile. Restano riutilizzati i tipi di risultato e gli strumenti di analisi/export; i componenti stateless originali V1 per commissioni e metriche non sono stati modificati. Qualsiasi cambiamento finanziario richiede un nuovo snapshot del motore e una nuova versione. Nessun servizio broker è una dipendenza di questo percorso.

Configurazione ufficiale:

| Parametro | Valore |
|---|---|
| Initial Capital | 10000 |
| Risk Percent | 2 |
| Max Order | CURRENT_EQUITY_PERCENT, 30% |
| Max Position / LONG / SHORT | CURRENT_EQUITY_PERCENT, 100% ciascuno, per trade |
| Importi legacy mantenuti | 10000 ciascuno, non applicati in modalità percentuale |
| Max Quantity | 1000000, esattamente dalla run di riferimento |
| Leverage | false |
| Min Quantity / Min Order Value | 1 / 0 |
| Small Quantity Warning | 4 |
| Intervallo | 2010-01-01 – 2026-09-08, DAILY |
| Entry / Stop | NEXT_BAR_OPEN / PATTERN_EXTREME |
| Stop Buffer / Tick | 0 / 0.01 |
| Holding / Same Bar | 20 / STOP_FIRST; ingresso conta come barra 1 |
| Commission Enabled | true |
| Commission Model | IBKR_US_FIXED |
| Fixed / Per Share / Minimum / Maximum | 0 / 0.005 / 1 / 1% valore ordine |
| Slippage | attivo, 0.03% per lato |
| Transaction Costs Per Share | 0 |
| PatternRange / CloseStrength / Bollinger ATR | OFF / OFF / OFF |
| Capital Skip Shadow Analysis | ON, conservata dal riferimento; nessun impatto sul portafoglio |
| Allow Long / Short | true / true nel backtest; nessun permesso operativo aggiunto |

| Simbolo | LONG | Target R | SHORT | Target R |
|---|---|---:|---|---:|
| AAPL | BULLISH_ENGULFING | 3 | OFF | — |
| GOOGL | BULLISH_HARAMI | 1.5 | OFF | — |
| NFLX | HAMMER | 2 | OFF | — |
| NVDA | PIERCING_PATTERN | 3 | OFF | — |
| QQQ | HAMMER | 3 | OFF | — |
| SPY | BULLISH_HARAMI | 3 | OFF | — |
| TSLA | BULLISH_ENGULFING | 1 | SHOOTING_STAR | 4 |
| XLK | HAMMER | 4 | OFF | — |
| XLY | BULLISH_ENGULFING | 1.5 | OFF | — |

Gli override dei singoli simboli sono null. La regola di costo NVDA è AUTO_PROVIDER dal 2010-01-01, adjustedThrough 2024-06-10, split 4 il 2021-07-20 e 10 il 2024-06-10. Tutti i valori, anche i parametri dei filtri disattivati, sono nel file `src/main/resources/pattern-stable/2.0.json` e nell'export CONFIGURATION.

Il confronto è stato eseguito con i dati archiviati, senza chiamare un provider o IBKR. È stato confrontato ogni campo del portafoglio, incluse tutte le operazioni, gli eventi, i prezzi e ogni punto della curva equity; soltanto l'UUID e l'etichetta strategia sono normalizzati. I valori monetari coincidono esattamente. Anche gli aggregati e i costi coincidono esattamente; l'asserzione sul capitale finale di riferimento usa una tolleranza assoluta di 1e-8.

| Metrica | V2 riferimento | Nuova V1 | Differenza |
|---|---:|---:|---:|
| Final Equity | 75595.0234224412 | 75595.0234224412 | 0 |
| CAGR % | 12.89619488160836 | 12.89619488160836 | 0 |
| Max Drawdown % | 20.687698967115157 | 20.687698967115157 | 0 |
| PF netto | 1.5961825411606758 | 1.5961825411606758 | 0 |
| Expectancy R, definizione originale | 0.314159686349026 | 0.314159686349026 | 0 |
| Net PnL | 65595.0234224412 | 65595.0234224412 | 0 |
| Trade chiusi | 1344 | 1344 | 0 |
| Capital Skips | 61 | 61 | 0 |
| Commissioni | 3801.2080819 | 3801.2080819 | 0 |
| Slippage | 5297.0275596588 | 5297.0275596588 | 0 |

Evidenze e workbook sono in `outputs/v1-promotion-evidence`. `PATTERN_V1_STABLE.json` contiene l'UUID della nuova run; `equivalence.json` descrive le verifiche; il workbook conserva le analisi V2 e aggiunge STRATEGY_VERSION con identificativi e hash.

La pagina ufficiale mostra STABLE / FROZEN / PAPER VALIDATION, campi non modificabili, configurazione completa, mapping, risultati, equity, Run History e export. La pagina V2 resta modificabile e mostra RESEARCH / EXPERIMENTAL.

La versione 2.0 esegue inizialmente soltanto il periodo e lo snapshot storici congelati. Un aggiornamento del periodo/dataset richiede una nuova versione esplicita. PAPER e LIVE rimangono disabilitati per questa strategia; l'adattatore operativo PAPER non viene introdotto dalla promozione. La futura attivazione dovrà verificare account PAPER, kill switch e capacità SHORT tramite i controlli esistenti. Il replay storico non sostituisce quella validazione.

Comandi di verifica: `mvn -o test`, `mvn -o -Dtest=PatternStableTest,PatternStablePromotionTest -DpatternStablePromotion=true test`, `mvn -o -DskipTests package` con JDK 21; `npm.cmd test`, `npm.cmd run build`. I log e i conteggi sono allegati; i test di esperimenti storici opzionali non abilitati nella suite ordinaria restano esplicitamente skipped. Il test storico di promozione è stato eseguito separatamente e superato.
