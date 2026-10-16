# PATTERN_V3 RESEARCH — CloseStrength, Pattern Range e RVOL

Intervento del 16 ottobre 2026. Modifiche limitate alla V3, ai suoi test,
all'interfaccia e agli export di ricerca. Nessuna modifica ai componenti o alle
configurazioni V1/V2 STABLE, al PAPER, ai servizi ordini o allo storico OHLCV.
Il workspace conteneva già modifiche degli interventi precedenti.

## Audit preliminare e componenti riutilizzati

Tutti i package Java sotto indicati sono relativi a
`it.andreademartini.trading`.

| Responsabilità | Classi/metodi analizzati | Esito |
|---|---|---|
| Configurazione, validazione e hash | `backtest.patternv3.PatternV3Configuration`, `PatternV3Store`, `PatternStableStore.hash` | Estesa la configurazione immutabile; archivio e hash riutilizzati |
| Pattern | `ConfiguredPatternStrategy.onClosedBar`, `PatternDetector.detect`, detector candlestick esistenti | Algoritmi, direzioni, Target R ed estremi del pattern invariati |
| CloseStrength | `CloseStrength.value`, `accepts`, `CloseStrengthAnalysis.audit` | Formula invariata; aggiunto minimo esplicito conservando il massimo storico |
| Range | `PatternRange.percent`, `reaches`, `PatternRangeAnalysis` | Formula e finestra multicandela invariati; aggiunto massimo opzionale |
| Dati/volumi | `HistoricalBar`, `PatternV3Dataset.Bar`, `StooqCsvParser.parseBar` | Il volume del CSV è copiato; nessun metadato certifica la sua base split-adjusted |
| WEEKLY | `SignalBars.of`, `ResearchSessionCalendar` | Aggregatore e calendario invariati; RVOL riceve solo settimane complete |
| Indicatori/ADX | `BollingerAtr`, `MarketRegimeIndicators`, `MarketRegimeFilter.audit` | Formule e soglie invariate; valutazioni raccolte anche quando un altro filtro rifiuta |
| Simulazione | `PatternV3Service.simulate`, `runOnSnapshot`, `PatternPortfolioSimulator.run` | Estesa solo la fase dei segnali e la diagnostica |
| Ingresso e gestione | `ConfiguredPatternStrategy.onNextOpen`, `PatternV3BarRules`, `execution.BarExecution.exit` | NEXT_BAR_OPEN e gestione DAILY invariati |
| Gap/costi | `GapExecutionAudit`, `GapAuditExcel`, `PatternV3SplitCosts`, sizing e slippage condivisi | Riutilizzati senza modifiche economiche |
| Export/storico | `PatternV3ExcelExporter.export`, `PatternV3Controller.history`, `PatternV3RunComparison` | Aggiunti audit, statistiche e metadati dei filtri |

## Formule e compatibilità

### CloseStrength

Si usano **High massimo e Low minimo dell'intero pattern riconosciuto** e il
Close della sua candela di conferma, nel timeframe dei segnali.

```text
LONG  = (Close - PatternLow) / (PatternHigh - PatternLow)
SHORT = (PatternHigh - Close) / (PatternHigh - PatternLow)
```

Valore 1: chiusura all'estremo favorevole della direzione del segnale, quindi
High per LONG e Low per SHORT. Valore 0: chiusura all'estremo opposto. Range zero
o Close fuori range producono N/D, mai un valore inventato pari a zero.
Il calcolo coincide con quello già presente nella V3 e nel componente V2
analizzato; nessuna modifica agli export o alle formule delle strategie stabili.

La V3 precedente usava `maxCloseStrength`, con condizione **<= massimo**.
Le configurazioni vecchie, prive di `closeStrengthMin`, conservano quella regola
`LEGACY_MAX_LE`. Quando il nuovo minimo è presente, la regola esplicita è
`MIN_GE`, **>= closeStrengthMin**; il vecchio massimo resta salvato ma inattivo.
Le due condizioni non vengono applicate simultaneamente. Confronti alle soglie
inclusivi, con moltiplicazioni esatte per evitare errori di arrotondamento.

L'interfaccia contiene un solo campo soglia e un selettore della regola. I report
indicano la regola e la soglia effettive. I campi storici `MaxCloseStrength`
rimangono per compatibilità e sono segnalati come inattivi in modalità MIN_GE.
**0,906 è una soglia candidata dei preset A/B, non un nuovo default globale.**

### Pattern Range %

```text
PatternRangePct = (PatternHigh - PatternLow) / SignalClose × 100
```

È il range dell'intero pattern, non soltanto della candela finale. Le unità sono
punti percentuali: 1,38 significa 1,38%, non 138% né 0,0138%.
La condizione usa minimo e, se presente, massimo inclusivi. Il massimo assente
resta nullo/blank, indicato come NONE nell'interfaccia. Minimi negativi, massimi
negativi o inferiori al minimo sono rifiutati anche prima del salvataggio/run.

Il campo storico `minimumPatternRangePct` è conservato. Il nuovo
`patternRangeMinPct`, se presente, è il minimo effettivo; le copie immutabili e
l'interfaccia sincronizzano i valori quando li modificano. Le vecchie
configurazioni continuano a usare il minimo storico e non hanno limite massimo.

### Relative Volume

```text
RVOL(N) = SignalVolume / mean(Volume delle N barre precedenti complete)
```

`RelativeVolume.onBar` calcola il rapporto **prima** di inserire il volume corrente
nella coda. Il denominatore esclude quindi la barra del segnale. Non si saltano
barre con volumi mancanti per sostituirle con dati più vecchi. Warm-up incompleto,
volumi correnti o precedenti non positivi, provenienza mista e base non verificata
producono N/D con motivazione. La divisione usa BigDecimal e non può produrre
NaN o Infinity. Il periodo deve essere intero positivo, anche nelle API Jackson
2/3: i decimali non vengono troncati silenziosamente.

Default della nuova funzione: filtro OFF, periodo effettivo 20, minimo effettivo
1,20, massimo assente. I valori vengono conservati quando si disabilita il filtro.
Un filtro RVOL attivo rifiuta i segnali con rapporto non disponibile.

In DAILY la coda contiene barre DAILY. In WEEKLY contiene barre WEEKLY concluse,
con volume pari alla somma delle sessioni DAILY già aggregata da `SignalBars`.
Le settimane festive hanno meno sessioni: il totale non viene normalizzato al
numero di sedute, quindi può essere inferiore per ragioni di calendario.
Non viene usato il volume di una settimana in formazione.

## Volumi, split e limite riscontrato

Il dataset conserva `volume`, provider e convenzione di aggiustamento dei prezzi,
ma **non una convenzione di aggiustamento dei volumi**. `SPLIT_AWARE_V1` riguarda
commissioni e quantità equivalenti, non certifica né corregge i volumi. Non è
corretto applicarne automaticamente il fattore a RVOL.

È stato quindi aggiunto `relativeVolumeBasis`:

- `UNVERIFIED`: default. Il rapporto resta N/D; dopo il warm-up la diagnostica
  è `UNVERIFIED_VOLUME_ADJUSTMENT`. SignalVolume e media osservata restano
  tracciabili, senza presentarli come un RVOL verificato.
- `CONSISTENT_DATASET_UNITS`: dichiarazione esplicita, selezionabile nella UI
  soltanto dopo verifica esterna della fonte. Consente il calcolo sulle unità del
  dataset, senza correzioni automatiche. È salvata nell'hash e nella run; non è
  una certificazione automatica del provider o una ricostruzione corporate actions.

Provenienze diverse all'interno della finestra restano indisponibili anche con
base dichiarata coerente. Non sono stati modificati CSV, OHLC, volumi, fattori
split, tick, quantità o commissioni. La prova storica RVOL non forza una
dichiarazione di coerenza per ottenere artificialmente trade.

## Valutazione completa e cronologia

La fase dei segnali ora:

1. Riconosce il pattern sulla barra chiusa del timeframe selezionato.
2. Calcola i valori disponibili: CloseStrength, Range, RVOL, Bollinger/ATR, ADX.
3. Valuta ogni predicato indipendentemente, con priorità deterministica dei
   rifiuti: CloseStrength, Range, RVOL, ADX/Regime, Bollinger/ATR.
4. Registra tutti i motivi applicabili e poi decide l'ammissione complessiva.
5. Solo per i segnali ammessi pianifica l'ingresso DAILY successivo.

Le metriche OFF possono essere diagnostiche, ma non rifiutano segnali. Il loro
predicato è logicamente passato; un indicatore mancante non diventa zero.
Quando manca un indicatore richiesto da un filtro ON, il passato può essere
nullo e la decisione finale è negativa con una motivazione esplicita.

La disposizione viene completata dopo il ciclo del portafoglio usando eventi e
trade effettivi. `OrderPlanned` distingue la pianificazione dall'ammissione;
`OrderAttempted` indica la valutazione simulata dell'ingresso, non l'invio di un
ordine broker. Si distinguono mancanza della barra successiva, posizione già
aperta, rischio iniziale non valido, vincoli di capitale/size e apertura riuscita.
Un trade ancora OPEN è comunque `TradeExecuted=true`.

`SignalId` identifica lo stesso pattern sullo stesso dataset anche fra preset;
RunId e ConfigurationHash identificano la simulazione. Non si attribuiscono
risultati economici ipotetici ai segnali rifiutati.

Entrata, stop, target, gap OPEN_FIRST, STOP_FIRST, timeout, slippage, commissioni,
sizing e movimenti di capitale restano nel ciclo DAILY esistente. L'aggiunta
diagnostica non anticipa un ingresso né sposta le uscite su barre WEEKLY.

## UI, preset, persistenza e confronto

La sezione RESEARCH FILTERS contiene ON/OFF, soglia CloseStrength e regola,
minimo/massimo Range e periodo/minimo/massimo RVOL, oltre alla base dei volumi.
Nessun controllo duplicato. Il nuovo SIGNAL FILTER AUDIT visualizza ogni pattern,
valori, rifiuti e disposizione dell'ingresso. Lo storico mostra anche i nuovi
filtri, i parametri e la modalità CloseStrength. Le altre funzionalità V3 restano
disponibili, compresi i vecchi preset, ora indicati come legacy.

| Preset | CloseStrength | Range | RVOL |
|---|---|---|---|
| BASELINE | OFF | OFF | OFF |
| TEST A — MODERATO | MIN_GE 0,906 | minimo 1,38%, massimo assente | OFF |
| TEST B — RESTRITTIVO | MIN_GE 0,906 | minimo 3,63%, massimo assente | OFF |

I preset modificano soltanto i tre filtri, senza cambiare nome, ADX, pattern,
simboli, Target R, timeframe o parametri economici, e non avviano run.
La modifica manuale e il salvataggio usano i valori effettivi dei controlli.

Nuovi campi opzionali nella configurazione mantengono gli hash delle vecchie
run quando assenti/default. Tutti i metodi di copia li conservano. Le nuove
scelte entrano nel Configuration Hash e nello snapshot della run.
`filterModelVersion=V3_RESEARCH_FILTERS/1` identifica il nuovo audit; il modello
di esecuzione resta quello già salvato. Le vecchie run senza audit conservano
i risultati e mostrano N/D per le nuove informazioni, senza ricostruirle.

Il confronto esistente permette di variare i filtri mantenendo fissi dataset,
parametri economici, date e modello di esecuzione. Le baseline dei filtri
disattivano anche RVOL. I replay e le analisi successive conservano il timeframe
dei segnali e la politica di esecuzione della run sorgente.

L'endpoint GET `/api/backtest/pattern-v3/research-filter-capabilities` permette
alla nuova UI di verificare il supporto del backend prima di qualsiasi modifica
o avvio. Con un backend precedente la UI chiede di riavviarlo e non invia una
richiesta che potrebbe ignorare silenziosamente i nuovi parametri. La lettura
delle vecchie run continua a funzionare. Nessuna attivazione operativa è eseguita.

## Excel

Tutti i fogli e le colonne precedenti sono conservati, incluso GAP_EXIT_ANALYSIS.
Si aggiungono:

- **SIGNAL_FILTER_AUDIT**: una riga per pattern grezzo, anche rifiutato da ADX;
  tutti i valori/soglie/ON-OFF, diagnostica volume, passato dei predicati, tutti
  i rifiuti, ammissione finale, pianificazione, tentativo, trade e motivo di
  mancata esecuzione.
- **FILTER_ANALYSIS**: `DESCRIPTIVE_ONLY — NOT A PORTFOLIO BACKTEST`. Solo trade
  realmente eseguiti e chiusi; quartili CloseStrength/Range, classi RVOL e
  combinazioni delle soglie configurate, anche quando OFF. Conteggi, osservazioni,
  mancanti, win rate netto, PF netto, expectancy Net R, PnL, peggior perdita Net R
  e GAP_STOP; separazioni per LONG/SHORT, simbolo, pattern e periodo di uscita.

Quartili con interpolazione lineare sulle osservazioni disponibili della run;
le parità restano nello stesso gruppo. Le soglie sono riportate nel foglio.
Campioni vuoti hanno conteggi zero e statistiche economiche non definite vuote,
senza PF fittizio. Net R segue la convenzione preesistente:
`netPnL / (initialRiskPerShare × quantity)`. Non sono curve equity simulate di
sottogruppi e non dimostrano l'effetto causale di un filtro.

TRADES aggiunge metriche al segnale, SignalVolume, media/RVOL, flag dei filtri,
problemi volume, regola e soglia CloseStrength effettive, limiti Range/RVOL e
base dichiarata. Le metriche non vengono ricalcolate alla data di uscita.
I fogli legacy CloseStrength conservano i vecchi campi e specificano quando il
massimo è inattivo; il nuovo audit completo è il riferimento per tutti i rifiuti.
RUN_INFO e CONFIGURATION preservano RunId, versione, hash, dataset/fingerprint,
timeframe, date, filtri, costi, rischio e modello di esecuzione.

## Risultati e regressione

Prima delle modifiche sono state acquisite due run complete DAILY/WEEKLY con i
tre filtri OFF e un manifest di hash dei file protetti. La regressione sullo
stesso dataset verifica **uguaglianza esatta del portafoglio completo**, inclusi
trade, quantità, date, PnL per trade, curva equity ed eventi; verifica anche
esecuzioni/costi e audit gap, normalizzando soltanto l'UUID. L'hash di quella
configurazione precedente resta identico.

I backtest esplorativi usano la configurazione V3 attualmente salvata: rischio
4%, capitale 10000, ordine 30% equity, ADX OFF, nessuna leva, 13 sessioni DAILY,
slippage 0,03% per lato, IBKR_US_FIXED. Tali parametri non sono stati cambiati
da questo intervento. Intervallo 2010-01-01–2026-09-08, stesso dataset per tutti.

| DAILY | Baseline | Test A | Test B |
|---|---:|---:|---:|
| Equity finale | 62846,72 | 26961,66 | 18578,43 |
| CAGR % | 11,6528 | 6,1280 | 3,7842 |
| Max DD % | 20,3086 | 16,0960 | 15,8988 |
| PF netto | 1,4584 | 1,8999 | 2,0987 |
| Trade chiusi | 1398 | 289 | 96 |

| WEEKLY | Baseline | Test A | Test B |
|---|---:|---:|---:|
| Equity finale | 22837,76 | 12641,38 | 11958,53 |
| CAGR % | 5,0768 | 1,4155 | 1,0783 |
| PF netto | 1,4688 | 1,3810 | 1,3451 |
| Trade chiusi | 363 | 115 | 85 |

La prova RVOL separata, base volumi non verificata, ammette zero trade e lascia
equity 10000: **non è una validazione economica di RVOL**. Evidenzia la mancanza
di informazioni necessarie sulla fonte. Nessuna coerenza è stata dichiarata
automaticamente per forzare un risultato.

Il [rapporto quantitativo](../outputs/research-filter-evidence/REPORT.md) include
tutte le metriche richieste, i quattro periodi storici e gli otto Excel, oltre
al JSON completo di configurazioni, RunId, hash e diagnostiche. I risultati sono
esplorativi: le soglie erano state scelte su dati già osservati. Non è stato
selezionato un vincitore né modificata una strategia stabile. Un futuro studio
walk-forward deve scegliere le soglie solo nel training e verificarle nei
periodi successivi; un holdout già visto non diventa incontaminato cambiandone
il nome. La conferma richiede dati futuri e un periodo PAPER successivo separato,
che questo intervento non attiva.

## File dell'intervento e test

Nuove classi sotto `backtest/patternv3/`: `RelativeVolume`, `ResearchFilterJson`,
`SignalFilterAudit`, `ResearchFilterAnalysis`, `ResearchFilterExcel`.
Estese: `PatternV3Configuration`, `PatternPortfolioSimulator`, `CloseStrength`,
`CloseStrengthAnalysis`, `PatternRangeAnalysis`, `PatternV3Service`,
`PatternV3Run`, `PatternV3Controller`, `PatternV3RunComparison`,
`PatternV3ExcelExporter`. Frontend: `pattern-v3.html`, `pattern-v3.js`,
`pattern-v3-model.js`.

Test nuovi: `patternv3.ResearchFiltersTest`,
`execution.ResearchFilterBaselineTest`, `execution.ResearchFilterHistoricalTest`,
`js/pattern-v3-research-filters.test.js`. Aggiornati i mock dei nuovi controlli
nei test JS V3 e l'integrazione delle route V3 nel file condiviso
`PatternV2HttpIntegrationTest`; nessuna modifica alla logica V2.

La copertura include ON/OFF, LONG/SHORT, soglie/limiti, zero range, N/D, warm-up,
esclusione del volume corrente, scala non verificata e provenienza mista,
settimane festive concluse, rifiuti simultanei, ingresso non eseguibile,
persistenza/hash/copie, vecchie run, preset isolati, versioni del backend,
coerenza Excel/aggregati, scansione dei valori non finiti e regressione economica.
I test gap, split, accounting, DAILY/WEEKLY e isolamento già presenti continuano
a passare.

```powershell
# Acquisizione prima delle modifiche; non sovrascrive la baseline
mvn '-Dtest=ResearchFilterBaselineTest' -DresearchFilterBaseline=true test
# Regressione e otto export sul dataset congelato
mvn -DresearchFilterHistorical=true verify
# Suite finale, dopo l'aggiornamento delle route di compatibilità
mvn verify
npm.cmd test
npm.cmd run build
```

Esiti: verify storico superato; verify finale **651 test, 632 eseguiti,
19 opzionali esclusi, zero errori/fallimenti**. **116 test frontend superati**,
build frontend, JAR aggiornato e controllo del diff superati. Il verify storico
include anche il test opzionale di regressione, passato; gli export non sono
stati inventati da metriche predefinite.
Log: `target/research-filters-historical-verify.log`,
`target/research-filters-verify.log`, `target/research-filters-frontend.log`,
`target/research-filters-frontend-build.log`.

Gli hash di tutti i file V1/V2/PAPER/ordini protetti, delle risorse congelate e
delle configurazioni operative considerate sono invariati. Le simulazioni e le
persistenze di test usano store temporanei; le suite HTTP sono BACKTEST con
gateway/ordini disabilitati e mock. **Nessun ordine IBKR PAPER/LIVE reale è
stato inviato.** Nessun backend operativo o scheduler è stato riavviato.

Il controllo finale ha confrontato 114 file protetti: zero differenze.

## Limiti residui e uso nell'app

La base dei volumi del dataset disponibile non è certificata; RVOL richiede
verifica esterna prima della dichiarazione di coerenza. Restano inoltre i limiti
già documentati per dati dividend-adjusted, copertura del catalogo split,
calendario WEEKLY 2010–2028 e slippage sui target rispetto a fill limit reali.
Nessuno di questi limiti è stato nascosto mediante conversioni o ottimizzazioni.

Per usare la nuova sezione, riavviare il backend aggiornato e ricaricare V3 con
Ctrl+F5. Per i valori indicati dall'utente, selezionare **TEST A — MODERATO**:
CloseStrength >=0,906 e Range >=1,38%, nessun massimo, RVOL OFF. Il backtest
parte soltanto con un avvio esplicito. I default globali e le vecchie run non
sono stati riscritti.
