# Audit di esecuzione storica: gap, costi e split

Audit del 16 ottobre 2026, sulle configurazioni congelate V1 Stable 2.2 e
V2 Stable 1.1. L'intervento aggiunge osservazioni persistite ed export, senza
cambiare prezzi eseguiti, pattern, target, filtri, sizing o configurazioni.

## Percorso di esecuzione verificato

| Responsabilità | V1 Stable | V2 Stable / componente comune |
|---|---|---|
| Avvio storico | `patternstable.PatternStableService.run` | `patternv2stable.FrozenPatternV2Service.run` |
| Simulazione | `patternstable.engine.StableSimulation.simulate` | `patternv2.PatternV2Service.simulate` |
| Ledger e ordine delle barre | `patternstable.engine.PatternPortfolioSimulator.run` | `patternv2.PatternPortfolioSimulator.run` |
| Segnale e livelli al prossimo OPEN | `patternstable.engine.ConfiguredPatternStrategy` | `patternv2.ConfiguredPatternStrategy.onNextOpen` |
| Decisione di chiusura | `patternstable.engine.PatternV2BarRules.exit` | `patternv2.PatternV2BarRules.exit` |
| Dimensionamento e costi | Copie congelate di sizing/pricing | `PatternV1PositionSizing`, `PatternV1Commission`, `PatternV2SplitCosts` |
| Export Stable | `PatternStableController.export` | `FrozenPatternV2Controller.export`, entrambi tramite `PatternV2ExcelExporter.export` |

I nomi dei package nella tabella sono relativi a
`it.andreademartini.trading.backtest`. Il vecchio `patternv1.PatternV1PortfolioEngine`
è il percorso legacy, distinto dalla V1 Stable corrente; non è stato migrato alla
politica OPEN_FIRST. Research V2 senza politica esplicita e V3 conservano la loro
semantica. L'audit viene agganciato soltanto nei due servizi Stable dopo la
simulazione: non cambia i motori o il lifecycle operativo.

Le serie sono DAILY, ordinate e filtrate per intervallo. I simulatori verificano
duplicati e coerenza OHLC. Alla chiusura della barra N vengono riconosciuti i
pattern; l'ingresso è candidato all'OPEN N+1. Il piano deriva stop dagli estremi
del pattern, rischio dall'OPEN e target da OPEN ± rischio × Target R. Un rischio
nullo/negativo produce `SIGNAL_SKIPPED_INVALID_RISK` con
`INVALID_RISK_DISTANCE`, prima di creare una posizione.

Il sizing vede OPEN noti e chiusure precedenti, non il Close futuro della seduta.
L'acquisto/collaterale e i costi d'ingresso vengono contabilizzati prima di valutare
stop e target. La barra d'ingresso è holding bar 1. Per i parametri congelati,
stop e target sono strettamente ai lati dell'OPEN: non possono attivare una
chiusura retroattiva all'OPEN. I test LONG/SHORT mostrano che stop e target toccati
nella barra d'ingresso sono esecuzioni intraday, `GapExitType=NONE`.

## OPEN, HIGH/LOW e timeout

`PatternV2BarRules.exit` controlla OPEN e restituisce immediatamente una decisione
quando è già oltre un livello. Solo senza questa decisione controlla HIGH/LOW.
Questa logica era già corretta e non è stata riscritta.

| Posizione già aperta | Condizione | Prezzo base | Motivo interno / audit |
|---|---|---|---|
| LONG | OPEN <= stop | OPEN | STOP / GAP_STOP |
| LONG | OPEN >= target | OPEN | TARGET / GAP_TARGET |
| SHORT | OPEN >= stop | OPEN | STOP / GAP_STOP |
| SHORT | OPEN <= target | OPEN | TARGET / GAP_TARGET |

STOP significa STOP_LOSS; TARGET significa TAKE_PROFIT. I nomi pubblici esistenti
restano compatibili. Uguaglianza al livello: uscita all'apertura, gap di grandezza
zero. Nessun movimento HIGH/LOW successivo può trasformare il risultato in un
altro tipo di uscita o in un'ambiguità. Gli estremi grezzi della candela restano
separati dalle escursioni eseguibili.

Quando OPEN è interno ai livelli e HIGH/LOW li raggiungono entrambi, si applica
STOP_FIRST con `sameBarAmbiguous=true`. Se nessun livello è raggiunto, TIMEOUT
chiude al Close della tredicesima barra, contando quella d'ingresso. Anche nella
tredicesima barra i livelli hanno priorità sul timeout. I trade ancora aperti
alla fine del dataset non vengono trasformati artificialmente in trade chiusi.

## Slippage, commissioni, R e casi estremi

Lo slippage configurato resta 0,03% per lato: BUY = base × 1,0003;
SELL = base × 0,9997. LONG entra BUY ed esce SELL; SHORT entra SELL ed esce BUY.
La contabilità usa prezzi base, sottraendo il costo di slippage una volta per
esecuzione. Gli export espongono anche i prezzi dopo slippage; questi sono una
rappresentazione alternativa della stessa contabilità, non un secondo costo.

I test verificano l'identità:

```text
PnL netto = PnL da prezzi base - slippage totale - commissioni - altri costi
          = PnL da prezzi dopo slippage - commissioni - altri costi
```

Le commissioni IBKR_US_FIXED simulate sono max(0,005 × quantità equivalente,
1 USD), limitate all'1% del controvalore. L'implementazione usa il prezzo BASE
per il tetto percentuale, anche quando il prezzo dopo slippage è diverso.
Questa convenzione, minimi, cap e sizing sono rimasti invariati. I costi sono
ricalcolati alla data dell'esecuzione, compresi split factor distinti in entrata
e uscita. Nessun importo reale IBKR viene sostituito o certificato da questo audit.

Price R, target e sizing usano la distanza tra ingresso BASE e stop iniziale.
`GapSizeR` invece segue la formula richiesta con ingresso DOPO slippage;
`GapSizeRPriceBasis` permette il confronto sulla base degli R già esistenti.
Le due basi sono esposte esplicitamente. Non è stato cambiato il rischio usato
dal money management.

Sono verificati gap con perdita oltre 5R per entrambe le direzioni, guadagni oltre
5R e oltre Target R, oltre a uno SHORT con OPEN 500 contro ingresso 100 che
produce equity negativa. Il 2% è un budget teorico iniziale, non un tetto alla
perdita effettiva. La perdita rimane nel ledger; gli ingressi successivi devono
superare i controlli di equity/cassa già presenti. I test e gli export reali
controllati non contengono NaN, Infinity o errori numerici Excel.

### Divergenza rispetto al PAPER: da risolvere separatamente

Le protezioni PAPER esaminate in `orders.IbkrOrderFactory` e nei servizi PAPER
usano stop `STP` e target `LMT`. Il backtest applica slippage sfavorevole anche
al target: ad esempio, un LONG con target 106 produce prezzo dopo slippage
105,9682. Quel prezzo sarebbe sotto il limite di vendita; uno SHORT mostra la
divergenza speculare sopra il limite di acquisto. È una limitazione verificata
del modello storico, non una garanzia di esecuzione di un limit.

Analogamente, un OPEN favorevole oltre target è assunto come prezzo base con
riempimento completo. Le barre DAILY non provano partecipazione all'asta,
priorità in coda, liquidità, spread, partial fill o il momento esatto di attivazione
delle protezioni. Nessuno di questi comportamenti è stato simulato come verificato.
Scheduler, factory degli ordini, conti, kill switch e PAPER_AUTO non sono stati
modificati da questa attività.

## Verifica split e limiti del dataset

Dataset: `190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd`.
I campioni originali sono in `outputs/gap-audit-evidence/split-price-samples.json`.
Le quattro componenti OHLC sono conservate nella stessa scala del dataset:
non vengono moltiplicate/divise nuovamente all'uscita per gap.

| Campione | Close prima | OPEN dopo | Osservazione |
|---|---:|---:|---|
| AAPL 28/31 agosto 2020 | 121,171 | 123,86 | Nessun salto meccanico 4:1 nella serie |
| NVDA 19/20 luglio 2021 | 18,7441 | 18,6941 | Nessun salto meccanico 4:1 |
| NVDA 7/10 giugno 2024 | 120,86 | 120,342 | Nessun salto meccanico 10:1 |
| TSLA 24/25 agosto 2022 | 297,097 | 302,36 | Nessun salto meccanico 3:1 |

Le date delle prime negoziazioni adjusted sono confermate dagli emittenti:
[Apple, 31 agosto 2020](https://www.apple.com/newsroom/2020/07/apple-reports-third-quarter-results/),
[NVIDIA, 20 luglio 2021](https://nvidianews.nvidia.com/news/nvidia-announces-four-for-one-stock-split-pending-stockholder-approval-at-annual-meeting-set-for-june-3),
[NVIDIA, 10 giugno 2024](https://investor.nvidia.com/files/doc_downloads/2024/06/nvidia-2024-stock-split_faq_investors.pdf),
[Tesla, 25 agosto 2022](https://ir.tesla.com/press-release/tesla-announces-three-one-stock-split).
La continuità osservata, unita alle date ufficiali, è evidenza di una serie già
normalizzata per questi split; non certifica tutti i prezzi dello storico.

Problemi di provenienza e copertura, mantenuti visibili:

- I campioni sono etichettati STOOQ/RAW. Il codice stesso riconosce che questo
  provider può consegnare OHLC adjusted pur con etichetta RAW. Il parser copia i
  valori e la dichiarazione ricevuta: non ricostruisce fattori split/dividendo.
- Il catalogo congelato contiene solo NVDA: fattore 40 prima del 20/07/2021,
  10 fino al 07/06/2024, poi 1. AAPL e TSLA non hanno regole di costo specifiche.
  Le loro quantità storiche equivalenti e commissioni per azione non possono
  essere considerate certificate: aggiungere i calendari cambierebbe i costi e
  richiederebbe un intervento/versionamento separato.
- `SPLIT_AWARE_V1` modifica i costi per azione e la quantità equivalente, non
  riscrive i prezzi o la quantità del dataset. I test 2:1 e 4:1, attraversando
  la data dello split, confermano un solo aggiustamento delle commissioni e
  nessun falso gap quando la scala OHLC è coerente.
- Mancano serie raw negoziabili, fattori dei dividendi e una provenienza più
  precisa di RAW/ADJUSTED. I prezzi moltiplicati per il solo split factor possono
  conservare aggiustamenti da dividendi. Non sono presentati come prova di prezzi
  storici realmente eseguibili; nessuna conversione indiscriminata è stata fatta.

## Audit persistito, Excel e storia

Le nuove run Stable contengono `executionAudit`, versione `GAP_AUDIT/1`.
Il modello finanziario resta `OPEN_FIRST/1` per le versioni correnti. L'audit
usa timestamp e valori BigDecimal del dataset originale, non i double del grafico,
e le decisioni `EXIT_EXECUTION` effettivamente emesse dal simulatore.

`TRADES` contiene le quattro colonne richieste:
`ExitBarOpen`, `GapExitType`, `GapSizePercent`, `GapSizeR`.
Contiene inoltre `ExitBasePrice`, `ExitExecutionPrice`, `InitialStopPrice`,
`TriggerLevel`, `ExitSlippageAmount`, `GapExitFlag`, le due basi iniziali di rischio,
`GapSizeRPriceBasis` e `GapAuditIssue`.

`GapSizePercent` è la distanza direzionale POSITIVA oltre stop/target, divisa per
il valore assoluto del livello, moltiplicata per 100. Non è il gap sul Close
precedente. `GapSizeR` divide la stessa distanza per
abs(EntryExecutionPrice − InitialStopPrice). Per NONE le due misure restano vuote.
L'uguaglianza sul livello produce un gap di misura zero. Denominatori non validi
producono N/D e una motivazione, non valori non finiti.

Il foglio `GAP_EXIT_ANALYSIS` contiene conteggi, copertura dell'audit, tassi,
direzioni, PnL netto, media/mediana/massimo delle distanze, estremi Price R e costi.
Ripete i dati per simbolo, quattro periodi richiesti e anno solare, assegnando
ciascun trade alla data di uscita. Commissioni/slippage sono i costi dell'intero
trade con gap; l'incidenza percentuale ha come denominatore la somma dei valori
assoluti dei PnL LORDI dei trade con gap. Denominatori zero restano vuoti.
Le medie usano misure disponibili e la copertura è esposta. Nessun beneficio
economico controfattuale viene calcolato.

Le vecchie run senza `executionAudit` mantengono i risultati e, anche se possiedono
grafici/eventi, non ricevono audit ricostruito in fase di export. Le nuove colonne
restano vuote; copertura e metadati segnalano N/D. I conteggi di gap non diventano
zero per assenza di dati. I dati sono persistiti prima dell'archiviazione delle
nuove run e conservati dagli helper immutabili `with...`, rendendo l'export dopo
riavvio uguale a quello della simulazione originale.

## Risultati economici e artefatti

| Metrica | V1 Stable 2.2 prima/dopo audit | V2 Stable 1.1 prima/dopo audit |
|---|---:|---:|
| Capitale iniziale USD | 10.000 | 10.000 |
| Equity finale USD | 57.076,4777241303 | 63.596,2058626152 |
| CAGR % | 11,009807463826649 | 11,732154290994657 |
| Drawdown massimo % | 17,142735509237024 | 16,663199844093274 |
| PF Net | 1,4309117131024092 | 1,5122422491276184 |
| Trade chiusi | 1.393 | 1.108 |
| GAP_STOP | 103 | 77 |
| GAP_TARGET | 64 | 54 |
| Tasso uscite gap % | 11,988513998564 | 11,823104693141 |

V2 è confrontata integralmente con la run
`5b3f1c64-d042-415b-b291-682389a0f2e2`, disponibile come JSON originale. Il file
Excel con quel nome non era presente nei percorsi del progetto esaminati. Si
confrontano portafoglio completo, trade, esecuzioni, costi, analisi e curva equity,
normalizzando soltanto l'UUID. V1 confronta tutte le righe SUMMARY e tutte le
colonne TRADES preesistenti del precedente export 2.2, normalizzando il tradeId.
Le differenze economiche sono zero, senza tolleranze per mascherare scostamenti.

Due GAP_STOP V2 sono esattamente sul livello: per questo i GAP_STOP sono 77
mentre i Price R strettamente inferiori a −1 sono 75. In V1 i corrispondenti
conteggi sono 103 e 100. La classificazione non confonde una distanza zero con
un valore mancante.

I nuovi file sono in `outputs/gap-audit-evidence/`: due Excel completi,
`audit-summary.json`, `persisted-audits.json` e `split-price-samples.json`.
SHA-256 di riferimento, dataset e configurazioni sono verificati prima/dopo.
Le simulazioni vengono salvate in store temporanei, senza alterare Run History
o archivi di produzione. Le future run nell'app useranno l'audit dopo il riavvio
del backend aggiornato; nessun servizio operativo è stato riavviato dall'audit.

## File dell'intervento

L'elenco è relativo a questo audit; il workspace conteneva già le modifiche
dell'intervento precedente sui gap.

Tutti i seguenti file Java di produzione sono sotto
`src/main/java/it/andreademartini/trading/backtest/`:

- Nuovi: `patternv2/GapExecutionAudit.java`, `patternv2/GapExitAnalysis.java`,
  `patternv2/GapAuditExcel.java`.
- Estesi: `patternv2/PatternV2Run.java`, `patternv2/PatternV2ExcelExporter.java`.
- Collegamento alle nuove run: `patternstable/PatternStableService.java`,
  `patternv2stable/FrozenPatternV2Service.java`.

Nuovi test sotto `src/test/java/it/andreademartini/trading/backtest/patternv2/`:
`GapAuditTestSupport`, `GapExecutionAuditTest`, `GapAuditExcelTest`,
`GapAuditSplitTest`, `GapAuditRegressionTest`. Estesi anche `GapExitRulesTest`
(stop intrabar isolati) e `PatternV2Test` (nuovo foglio Excel).
Documentazione: questo rapporto
e `outputs/gap-audit-evidence/REPORT.md`, oltre agli artefatti generati.

I metodi finanziari, le risorse congelate e i file PAPER/IBKR non sono stati
modificati da questo audit. Non è stato riscritto ciò che era già corretto.

## Verifiche riproducibili

```powershell
mvn '-Dtest=GapExecutionAuditTest,GapAuditExcelTest,GapAuditSplitTest,GapExitRulesTest,GapPortfolioAccountingTest,PatternV2SplitCostsTest' test
mvn '-Dtest=GapAuditRegressionTest' -DgapAuditRegression=true test
mvn verify
npm.cmd test
```

La regressione opzionale non avvia Spring né costruisce gateway broker. Le suite
HTTP usano BACKTEST, database di test e invio ordini disabilitato; i test operativi
esistenti usano mock. Nessun comando di attivazione PAPER/LIVE, avvio IBKR o invio
ordini è stato eseguito durante questa attività.

### Matrice dei 48 scenari richiesti

Esiti finali: `mvn verify` superato, **589 test complessivi, 576 eseguiti e
13 opzionali esclusi**, zero fallimenti/errori. La regressione offline
`GapAuditRegressionTest` è stata eseguita separatamente e superata. Passano
anche i **109 test frontend**. Log riproducibili:
`target/gap-audit-verify.log`, `target/gap-audit-regression.log`,
`target/gap-audit-frontend.log`. Il JAR aggiornato è stato prodotto da Maven;
nessun backend operativo è stato riavviato.

| Scenari | Copertura |
|---|---|
| 1–8: priorità OPEN nelle quattro combinazioni e movimenti opposti successivi | `GapExitRulesTest`, `GapExecutionAuditTest` nei due motori |
| 9–15: stop/target intrabar, STOP_FIRST, uguaglianza sui livelli | `GapExitRulesTest`, `GapExecutionAuditTest` |
| 16–20: slippage LONG/SHORT, gap stop/target, nessun doppio addebito | `GapExecutionAuditTest`, `GapPortfolioAccountingTest` |
| 21–24: perdite/profitti oltre 5R e PnL completo | `GapExecutionAuditTest`, `GapPortfolioAccountingTest` (anche insolvenza SHORT) |
| 25–30: NEXT_BAR_OPEN, slippage, rischio/target ricalcolati e rifiuti | `GapExecutionAuditTest` |
| 31–32: nessuna uscita precedente all'ingresso; stop/target intraday sulla barra di ingresso | `GapExecutionAuditTest` LONG/SHORT nei due motori |
| 33–36: split 2:1, 4:1, scale coerenti e nessun doppio aggiustamento | `GapAuditSplitTest`, `PatternV2SplitCostsTest`, campioni reali nella regressione |
| 37–42: colonne, formule, riepilogo e assenza di valori non finiti | `GapAuditExcelTest`, scansione completa dei due Excel in `GapAuditRegressionTest` |
| 43–47: configurazioni, filtri, target e sizing invariati | SHA protetti, parità completa di V2 e colonne economiche V1 in `GapAuditRegressionTest`; `GapExecutionAuditTest` verifica l'aggiunta del solo audit |
| 48: nessun ordine IBKR | regressione offline senza broker e isolamento BACKTEST/mock della suite completa |
