# Gestione gap condivisa V1, V2 e V3

Intervento del 16 ottobre 2026. Riguarda esclusivamente backtest DAILY,
diagnostica ed export. V1 Stable 2.2 e V2 Stable 1.1 gestivano già correttamente
i gap: il loro comportamento è stato conservato. V3 usa ora lo stesso motore
nelle nuove simulazioni, mantenendo configurazione e funzionalità di ricerca.

## Architettura e percorso di esecuzione

I file Java elencati sono relativi a
`src/main/java/it/andreademartini/trading/backtest/`.

| Responsabilità | Classi e metodi |
|---|---|
| Avvio V1 Stable | `patternstable.PatternStableService`, `patternstable.engine.StableSimulation.simulate` |
| Avvio V2 Stable | `patternv2stable.FrozenPatternV2Service`, `patternv2.PatternV2Service.simulate` |
| Avvio V3 Research | `patternv3.PatternV3Service.simulate`, `runOnSnapshot`, replay e analisi abbinate |
| Portafoglio e cronologia degli eventi | `PatternPortfolioSimulator` nei tre package |
| Decisione di uscita condivisa | `execution.BarExecution.exit` |
| Escursioni fino all'esecuzione | `execution.BarExecution.Excursions.measure` |
| Slippage per lato | `execution.Slippage.perShare` |
| Compatibilità delle interfacce esistenti | I tre `PatternV2BarRules` / `PatternV3BarRules` delegano al componente comune |
| Proiezione indipendente dalla configurazione | `execution.ExecutionRunView` |
| Audit e aggregati condivisi | `patternv2.GapExecutionAudit.capture`, `GapExitAnalysis.groups` |
| Excel | `PatternV2ExcelExporter`, `PatternV3ExcelExporter`, generatore comune `GapAuditExcel.append` |

Si è estratta la regola verificata della V2 senza cambiarne condizioni o formule.
Le vecchie firme senza politica esplicita mantengono LEVEL_ONLY: questo evita
di cambiare indirettamente i chiamanti PAPER e la riproduzione storica.

Per una posizione già aperta, il simulatore chiama la regola con OPEN_FIRST.
`BarExecution.exit` verifica OPEN e restituisce subito la decisione gap, senza
usare HIGH/LOW per scegliere una seconda uscita o marcare ambiguità. In assenza
di gap valuta stop/target intrabar con STOP_FIRST, quindi il timeout al CLOSE.
I motivi interni restano `STOP`, `TARGET`, `TIMEOUT`; la classificazione audit è
`GAP_STOP`, `GAP_TARGET`, `NONE`.

| Posizione | Condizione OPEN | Prezzo base | Audit |
|---|---|---|---|
| LONG | OPEN <= stop | OPEN | GAP_STOP |
| LONG | OPEN >= target | OPEN | GAP_TARGET |
| SHORT | OPEN >= stop | OPEN | GAP_STOP |
| SHORT | OPEN <= target | OPEN | GAP_TARGET |

L'uguaglianza sul livello è inclusa, con distanza gap zero. Sulla barra di
ingresso si applica invece LEVEL_ONLY dopo l'apertura effettiva: entrata
NEXT_BAR_OPEN, slippage, rischio e sizing, attivazione dei livelli, controllo
intrabar. Le distanze entry-stop non valide sono rifiutate dalle validazioni
esistenti. Nessun filtro sui segnali o sull'ampiezza del gap è stato aggiunto.

BUY usa base × (1 + rate), SELL base × (1 - rate), una sola volta per lato.
LONG esce SELL, SHORT esce BUY, anche per timeout. Le perdite oltre 5R e i
guadagni oltre target sono consentiti. Il rischio percentuale resta il rischio
teorico del sizing; non limita la perdita realizzata. V3 OPEN_FIRST mantiene
la perdita effettiva anche quando produce capitale negativo, come V2, senza
eliminare i controlli di fattibilità dei nuovi ingressi.

## Audit, Excel e compatibilità storica

Tutti e tre gli export contengono in TRADES:
`ExitBarOpen`, `GapExitType`, `GapSizePercent`, `GapSizeR`, `ExitBasePrice`,
`ExitExecutionPrice`, `ExitSlippageAmount`, `InitialStopPrice`, `TriggerLevel`,
`GapExitFlag`, `GapAuditIssue`.

Le formule sono quelle della V2: distanza direzionale OPEN-livello in percentuale
del valore assoluto del livello; distanza assoluta OPEN-livello in R iniziali,
con denominatore `abs(entry dopo slippage - stop iniziale)`. Per NONE le due
distanze sono vuote. Denominatori non validi non producono valori non finiti.
Il gap percentuale non è la variazione dal CLOSE precedente.

`GAP_EXIT_ANALYSIS` usa lo stesso generatore e contiene conteggi LONG/SHORT,
PnL netto per tipo, statistiche delle distanze, superamenti di -1R/Target R,
costi e sezioni per simbolo, anno e periodi 2010–2013, 2014–2017, 2018–2021,
2022–ultima data disponibile. L'attribuzione temporale segue la data di uscita.
Il PnL dei trade con gap non viene presentato come beneficio incrementale del gap.

V3 registra `executionModelVersion` e audit nella run, fuori dalla configurazione
strategica: nuove simulazioni `OPEN_FIRST/1`, modello precedente `LEVEL_ONLY/1`.
Il replay conserva il modello della run originale. Una vecchia run senza campo
è riprodotta con LEVEL_ONLY e mostra metadati non disponibili, senza attribuirle
retroattivamente il nuovo audit. I confronti V3 rifiutano modelli diversi;
analisi dei filtri, regime, periodi e capital skip ereditano il modello della run.
Run History mostra la versione del modello. Nessuna run esistente è riscritta.

## Regressione sullo stesso dataset

Dataset congelato:
`190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`.
Prima del refactoring sono stati acquisiti tre risultati completi e gli hash
dei file protetti. Per V3 non esisteva una run archiviata: il prima è una
simulazione eseguita sul codice precedente, non un risultato ricostruito.
Configurazione, dati, intervallo, capitale e costi sono identici prima/dopo.

| Metrica V1 | Prima | Dopo | Differenza |
|---|---:|---:|---:|
| Final Equity | 57076.4777241303 | 57076.4777241303 | 0 |
| CAGR % | 11.009807464 | 11.009807464 | 0 |
| Max Drawdown % | 17.142735509 | 17.142735509 | 0 |
| Profit Factor Net | 1.430911713 | 1.430911713 | 0 |
| Win Rate % | 43.000717875 | 43.000717875 | 0 |
| Closed Trades | 1393 | 1393 | 0 |
| Total Costs | 9185.7229188697 | 9185.7229188697 | 0 |
| Gap Stop Count | 103 | 103 | 0 |
| Gap Target Count | 64 | 64 | 0 |
| Capital Skips | 52 | 52 | 0 |

V2: parità esatta di portafoglio, trade, date, direzioni, quantità, prezzi,
esecuzioni, costi, audit e aggregati, normalizzando soltanto l'UUID.
Equity 63596.2058626152; CAGR 11.732154291%; DD 16.663199844%; PF netto
1.512242249; 1108 chiusi; 77 gap stop, 54 gap target. Anche V1 supera la parità
integrale. Non sono state usate tolleranze per nascondere differenze finanziarie.

| Metrica V3 | Prima LEVEL_ONLY | Dopo OPEN_FIRST | Differenza |
|---|---:|---:|---:|
| Final Equity | 76243.9396085769 | 63596.2058626152 | -12647.7337459617 |
| CAGR % | 12.954075112 | 11.732154291 | -1.221920821 pp |
| Max Drawdown % | 14.737469138 | 16.663199844 | +1.925730706 pp |
| Profit Factor Net | 1.614865647 | 1.512242249 | -0.102623398 |
| Win Rate % | 43.953068592 | 43.862815884 | -0.090252708 pp |
| Closed Trades | 1108 | 1108 | 0 |
| Total Costs | 9115.5733424231 | 8378.1733893848 | -737.3999530383 |
| Gap Stop Count | N/D | 77 | N/D |
| Gap Target Count | N/D | 54 | N/D |
| Capital Skips | 20 | 23 | +3 |

V3: 129 uscite comuni cambiano direttamente per il prezzo OPEN; 1079 trade
comuni cambiano complessivamente e 1074 cambiano quantità, a causa dell'equity
disponibile nel sizing successivo. Sette operazioni sono presenti solo prima
e sette solo dopo. I 131 gap includono due aperture esattamente sul livello,
che non cambiano il prezzo base. Il dettaglio delle operazioni confrontate è
in `outputs/shared-gap-evidence/comparison.json`. Il vecchio modello V3 è stato
rieseguito separatamente e riproduce esattamente il portafoglio pre-intervento.

## Split e limiti conservati

I test 2:1 e 4:1 confrontano tutte e tre le strategie: nessun falso gap su OHLC
coerenti e nessun doppio aggiustamento delle commissioni SPLIT_AWARE_V1.
Non si riscrivono OHLC, stop, target o quantità del dataset.
I campioni AAPL, NVDA e TSLA già verificati sono in
`outputs/gap-audit-evidence/split-price-samples.json`; dettagli e provenienza
sono in `docs/GAP_EXECUTION_AUDIT.md`.

Restano limiti espliciti del modello V2, mantenuto invariato:

- Il catalogo dei fattori di costo congelato copre NVDA, non tutti gli split
  di AAPL/TSLA. Non si certificano le loro commissioni storiche equivalenti.
- La provenienza RAW/ADJUSTED non basta a ricostruire prezzi storici realmente
  negoziabili o separare con certezza gli aggiustamenti da dividendi.
- Il modello applica slippage sfavorevole anche ai target. Non riproduce
  esattamente il vincolo di prezzo degli ordini limit previsti in PAPER.
  Un OPEN favorevole non garantisce un'esecuzione reale a quel prezzo.
- Il tetto percentuale della commissione usa il prezzo base, come prima.

Questi problemi richiedono interventi separati e non sono stati corretti
cambiando silenziosamente V2 o il modulo PAPER.

## File dell'intervento e verifiche

Nuovi componenti: `execution/{BarExecution,Slippage,ExecutionModel,ExecutionRunView}`.
Modificati i tre adattatori BarRules; i servizi di simulazione e simulatori per
delegare slippage/politica; i tre componenti audit/Excel condivisi della V2;
V3 Run, Service, CapitalSkipShadowAnalysis, RunComparison, CapitalComparison,
Controller ed ExcelExporter; `static/pattern-v3.js` e `pattern-v3.html`.
I parametri strategici e i relativi hash sono invariati.

Test nuovi: `execution.SharedGapBaselineTest`, `SharedGapRegressionTest`,
`patternv2.SharedGapStrategiesTest`. Estesi `GapExitRulesTest` e `GapAuditSplitTest`.
Copertura: quattro gap e priorità OPEN, intrabar/STOP_FIRST, uguaglianza sui
livelli, ingresso e rischio non valido, timeout, slippage e commissioni,
perdite/profitti oltre 5R, split, persistenza e vecchie run, Excel e aggregati,
parità V2 e integrità delle configurazioni e dei file PAPER/ordini.

```powershell
# Acquisizione iniziale, solo prima delle modifiche, non sovrascrive la baseline
mvn '-Dtest=SharedGapBaselineTest' -DsharedGapBaseline=true test
# Regressione e suite completa dopo le modifiche
mvn -DsharedGapRegression=true verify
npm.cmd test
npm.cmd run build
```

Esito finale: Maven `verify` superato, **616 test complessivi: 602 eseguiti,
14 opzionali esclusi, zero errori e fallimenti**. La regressione storica è
inclusa in questa esecuzione. Passano anche tutti i **109 test frontend**, il
build frontend e `git diff --check`. Il JAR aggiornato è stato prodotto.
Log: `target/shared-gap-verify.log`, `target/shared-gap-tests.log`,
`target/shared-gap-frontend.log`, `target/shared-gap-frontend-build.log`.

La regressione usa store temporanei, senza broker, e controlla gli SHA-256 dei
file PAPER/ordini e delle configurazioni contro il manifest iniziale. Le suite
HTTP esistenti sono isolate per il test e gli invii usano mock. Nessun ordine
IBKR reale PAPER/LIVE è stato trasmesso. Nessun scheduler o backend operativo è
stato riavviato. Il workspace conteneva già modifiche PAPER precedenti: i loro
hash sono invariati rispetto all'inizio di questo intervento.

Artefatti: tre Excel completi, confronto dettagliato e manifest in
`outputs/shared-gap-evidence/`. Per usare il codice nell'app occorre il normale
riavvio del backend aggiornato e una nuova run; i replay storici conservano
il proprio modello.
