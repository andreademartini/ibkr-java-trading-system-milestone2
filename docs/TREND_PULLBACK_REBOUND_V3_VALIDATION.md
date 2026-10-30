# Validazione V3 Research

Implementazione eseguita e report Excel generato offline. Validazione economica V3 ancora incompleta: mancano tick verificati per strumento. Non sono stati utilizzati tick inventati o universali.

## File

Creati in src/main/java/it/andreademartini/trading/backtest/reboundv3:

- TrendReboundV3Configuration.java: parametri e validazione.
- TrendReboundV3Engine.java: portafoglio indipendente e BUY STOP.
- TrendReboundV3Run.java: run e diagnostica dei segnali.
- TrendReboundV3Service.java: caricamento CSV, snapshot, replay e benchmark V1.
- TrendReboundV3Store.java: archivio separato.
- TrendReboundV3Controller.java: API separata.
- TrendReboundV3ExcelExporter.java: fogli esistenti e nuove colonne.
- V3Diagnostics.java: massimo precedente, target teorico, MFE, audit e statistiche.
- V3Comparison.java: confronto fra simulazioni reali.

Creati: pagina e script static/trend-pullback-rebound-v3.html e .js; test reboundv3/TrendReboundV3EngineTest.java e V3LocalReportTest.java; documentazione TREND_PULLBACK_REBOUND_V3_RESEARCH.md e questo report.

Modificati esclusivamente per registrazione/navigazione: WeeklyResearchStrategy.java, ResearchStrategyCatalog.java, static/site-navigation.js. Nessuna modifica alle implementazioni V1/V2, PATTERN, WEEKLY_LEVEL_MOMENTUM o PAPER.

Output: outputs/trend-pullback-rebound-v3-validation/TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx, summary.json e snapshot/configurazioni/run nella sottocartella store.

## Ingresso e uscite

Open quarta barra strettamente inferiore al massimo terza barra; trigger massimo terza barra più tick specifico, valido soltanto nella quarta sessione. Slippage al trigger. ATR del segnale, stop e RECOVERY temporale V1 preservati. Il target analitico non opera sulle uscite.

## Risultati locali

Periodo effettivo 2017-01-03–2026-09-02. Universo e snapshot comuni per V1 e V3; esclusioni e lacune nel foglio DATA_QUALITY.

| Voce | V3 |
|---|---:|
| Segnali rilevati | 3978 |
| Trade eseguiti | 0 |
| Open al massimo precedente o sopra | 502 |
| Tick minimo non disponibile | 3475 |
| Fine dataset prima della quarta barra | 1 |
| Trade con spazio almeno 1.5 ATR | 0 |
| Trade senza spazio sufficiente | 0 |

Nessuna performance dei due gruppi è stimabile: non ci sono trade V3. I valori monetari zero descrivono soltanto un portafoglio rimasto in liquidità; non valutano la strategia BUY STOP. Il numero TradesRejected del confronto include anche i segnali censurati: ENTRY_SIGNAL_AUDIT distingue le ragioni.

V1 ricalcolata sullo stesso snapshot: 3978 segnali; 2244 ingressi, 2238 trade chiusi e 6 posizioni aperte/censurate. Win rate chiusi 57.9982%, profit factor netto 1.10257, CAGR 2.90508%, drawdown massimo 13.4351%. PnL netto chiuso 3223.2665 USD; PnL portafoglio inclusi aperti 3187.5122 USD. Expectancy 0.000525604 R; esposizione media 29.0733%; durata media 3.28788 barre; commissioni più slippage 6495.7951 USD. Questi risultati includono i limiti di qualità/censura riportati, non costituiscono validazione di un filtro.

## Test

JDK 21.0.11, mvn test: 814 test, 792 passati, 21 saltati, 1 fallimento, 0 errori.

Tutti i 14 test nuovi passati: 13 sul motore e 1 report offline. V1 engine 23/23 e V2 engine 29/29 passati, insieme ai relativi test HTTP.

Fallimento estraneo alla V3: ResearchFiltersTest.validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes, riga 64. Legge data/pattern-v3/current.json e presume closeStrengthMin=null; il valore locale è 0.906. Medesimo fallimento osservato nella prima suite (800 test, prima dell'aggiunta dei test V3). Il file e la strategia PATTERN V3 sono stati lasciati intatti. Dettaglio in v3-all-tests.log e target/surefire-reports.

Le 26 richieste di verifica sono coperte da asserzioni distribuite nei test nuovi e dalle regressioni esistenti; non corrispondono a 26 metodi nuovi separati. La verifica dell'assenza di dipendenze broker è strutturale, con simulazioni offline e test HTTP in modalità BACKTEST.

## Limiti e lavoro necessario per la validazione finale

Fornire minimumTickSizes verificati per simbolo, coerenti con il periodo e la base prezzi. Il modello corrente accetta una costante per simbolo, non una griglia storica dinamica. Poi rieseguire tramite interfaccia la V3: Excel e confronto V1 saranno ricalcolati automaticamente.

DAILY non fornisce sequenza intraday: ingresso/stop nella stessa barra esce conservativamente allo stop con flag di ambiguità. MFE nelle barre di stop è un limite superiore, non profitto necessariamente realizzabile. Lacune di dati possono censurare posizioni. Target teorico toccato non implica esecuzione profittevole.

Non è stato inviato alcun ordine IBKR PAPER o LIVE.
