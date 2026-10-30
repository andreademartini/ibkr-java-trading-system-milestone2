# Portfolio Summary condiviso

## Componenti e integrazione

- reporting/PortfolioSummaryDto: quaranta metriche numeriche, formato di presentazione, motivo di indisponibilità e contesto della run.
- reporting/PortfolioSummaryMapper: adattatori puramente di reporting per PATTERN, Trend Rebound, Weekly e BacktestExportRun del workbench.
- reporting/PortfolioSummaryAdvice: aggiunge portfolioSummary alle risposte di run sotto /api/backtest; conserva oggetti, precisione e proprietà esistenti. Non modifica le run persistite.
- reporting/PortfolioSummaryExcel: aggiunge PORTFOLIO_SUMMARY agli exporter esistenti senza eliminare fogli.
- reporting/PortfolioSummaryController: riepilogo del risultato salvato nel registro del workbench, GET /api/backtest/{id}/portfolio-summary.
- static/portfolio-summary-definitions.json: unica sequenza canonica delle 40 etichette, chiavi e formati.
- static/portfolio-summary.js e .css: unico renderer e griglia 8/4/2/1 colonne. Riprende padding, caratteri, bordi e angoli del pannello V1.

Integrati i renderer di PATTERN V1 stabile e legacy, PATTERN V2 stabile e research storico, PATTERN V3 research, Weekly High TTE storico e attuale, Trend Pullback Rebound V1/V2/V3 e il workbench per Moving Average Crossover, Joe Ross 123 TTE e alias storico Ross Hook TTE. MANUAL_ONLY non produce una simulazione di trading.

WEEKLY_LEVEL_MOMENTUM_RESEARCH non è registrata in questa versione: non è stata creata una nuova strategia.

Gli unici interventi backend esterni al package reporting sono chiamate agli exporter e la sostituzione del riepilogo Excel nella busta metadata della V1 stabile. Nessun motore, segnale, fill, sizing o costo è stato modificato.

## Fonti e convenzioni

PATTERN usa direttamente PatternV1Result e Metrics del motore comune: cash libero/contabile, passività SHORT, drawdown, Profit Factor lordo/netto/R e convenzioni della curva. Win Rate è convertito da frazione a punti percentuali; è Wins / (Wins + Losses), breakeven esclusi.

Trend Rebound usa la curva equity, i segnali e i trade della run. Profit Factor lordo usa prezzi base e quantità; quello netto usa netPnL; quello in R usa netR. Le medie R sono sui soli chiusi. Costi già incorporati nei prezzi non vengono sottratti due volte.

Weekly usa anche gli effettivi fill delle uscite parziali. La commissione di ingresso viene ripartita sulla quantità uscita per separare realizzato netto e non realizzato netto. Nel motore storico SHORT il cash salvato è già libero, perché all'ingresso viene sottratto il nozionale a garanzia: il cash contabile esposto è cash libero + 2 × nozionale iniziale riservato. Long Market Value e Short Market Value restano separati; l'equity non viene aumentata dai proventi SHORT.

CAGR usa le date effettive della curva quando il motore non lo fornisce. Drawdown monetario e percentuale vengono ricostruiti dalla stessa curva, partendo dal capitale iniziale. Esposizione lorda media e sessioni in posizione hanno calcoli separati; per research la convenzione inclusiva considera le date di ingresso/uscita, comprese le operazioni intraday.

Il workbench usa il medesimo BacktestExportRun dell'Excel. Non ricostruisce un cash ledger o una curva equity non presenti nel registro. La metrica storica exposurePercent del workbench misura il tempo in posizione, quindi non viene riutilizzata come esposizione lorda media.

Il contesto mostra configurazione della run, ID/versione quando disponibili, dataset, copertura richiesta/effettiva, rischio, commissioni, slippage e leva. Per la V1 stabile si risolve anche il metadata persistito della specifica run, senza leggere la configurazione corrente del form.

## Storico, dati mancanti e confronto

N/A non viene convertito in zero. Profit Factor senza perdite, media senza osservazioni e CAGR con copertura nulla restano indefiniti. Same Bar Ambiguities rimane N/A dove non registrato; V2 usa tradeAudit, V3 i flag diagnostici, Weekly il flag intrabarAmbiguous.

Le run storiche vengono adattate al caricamento, senza riscriverne i risultati. Lo storico del workbench dipende dal registro esistente, limitato a 25 run e non persistente: se la run non esiste più viene mostrato un messaggio di indisponibilità.

Compare Runs delle pagine research riutilizza lo stesso renderer per le 40 righe e segnala differenze o assenza di informazioni su dataset, copertura, capitale, universo, commissioni, slippage, leva e rischio. Le convenzioni R possono differire fra famiglie: la fonte è dichiarata nel pannello.

## Estensione

Una nuova strategia con il formato run comune viene arricchita automaticamente dall'advice. Per un formato nuovo aggiungere un adattatore nel mapper, senza implementare metriche nel frontend. Una pagina può chiamare PortfolioSummary.render(host,run), oppure usare un host data-portfolio-summary e inviare l'evento portfolio-run-selected con la run in detail.

Il renderer non mantiene una cache delle run. PortfolioSummary.load protegge contro risposte fuori ordine tramite una generazione per host. PortfolioSummary.clear serve a gestire caricamento, cambi di selezione e risultati vuoti.

## Verifiche

Test frontend: ordine e numero delle schede, numeri italiani, dati salvati contro form corrente, dati storici assenti, cambio run, risposta asincrona tardiva, CSS responsive, integrazione delle pagine e confronto.

JUnit: contabilità LONG e SHORT, drawdown, CAGR effettivo, Profit Factor distinti, R, aperti/chiusi, segnali, identità dei conteggi, indisponibilità, busta V2, API additive ed equivalenza numerica con il foglio Excel. Le regressioni esistenti continuano a verificare segnali, ordini simulati, sizing, gap e risultati.

Lo screenshot citato nel prompt non era disponibile come allegato: il riferimento visivo usato è il pannello PATTERN V1 presente nel progetto, con le 40 etichette e i breakpoint richiesti.

Per l'applicazione già avviata è necessario riavviare il backend per registrare il nuovo advice/controller, poi ricaricare la pagina. I backtest e i test restano offline; nessun ordine PAPER/LIVE viene trasmesso.

## Esito finale

- npm test: 147 test passati, nessun fallimento (portfolio-frontend-final.log).
- npm run build: riuscita; controllo sintattico incluso per il nuovo componente (portfolio-build-final.log).
- Suite Maven completa: 826 test, 804 passati, 21 saltati, 1 fallimento, 0 errori (portfolio-complete-tests.log).
- I 12 nuovi test reporting sono passati. Dopo gli ultimi dettagli di mapping del workbench sono passati anche tutti i 17 test mirati reporting/Excel workbench (portfolio-reporting-final.log).
- Regressioni API PATTERN V2: 8/8 passate, inclusa identità della configurazione originale e presenza di strategyId. Passate regressioni dei motori PATTERN, Trend Rebound V1/V2/V3, Weekly e workbench, oltre a contabilità LONG/SHORT e gap.
- Gli exporter conservano tutti i fogli precedenti. Le aspettative di conteggio fogli nei test V1/V2 sono state aggiornate per PORTFOLIO_SUMMARY.

Unico fallimento residuo: ResearchFiltersTest.validationAndCopiesPreserveExplicitBoundsAndLegacyConfigurationHashes, riga 64. Il test legge data/pattern-v3/current.json e presume closeStrengthMin nullo, mentre il file locale contiene 0.906. Era già presente prima di questo intervento. Configurazione locale e motore PATTERN V3 non sono stati alterati per farlo passare.

L'Excel locale V3 Research esistente è stato rigenerato dai test anche con PORTFOLIO_SUMMARY: outputs/trend-pullback-rebound-v3-validation/TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx. Rimangono i limiti dei dati già documentati per quella run.

Metriche che possono restare N/A: cash ledger/available cash e drawdown monetario nei vecchi export workbench senza ledger o curva; ambiguità non registrate nelle vecchie run; versione/dataset non salvati nello storico; Profit Factor senza perdite e medie di gruppi vuoti. Il pannello non trasforma queste assenze in zero.
