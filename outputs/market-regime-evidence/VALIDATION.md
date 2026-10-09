# Verifiche finali — MARKET REGIME ANALYSIS

- Maven offline `verify`, con `marketRegimeExperiment=true`: 481 test rilevati, 473 eseguiti, zero fallimenti/errori, 8 esclusi; BUILD SUCCESS e JAR Spring Boot prodotto.
- 40 casi del nuovo MarketRegimeTest: indicatori, reference ADX/DI TA-Lib, EMA analitiche, warm-up, classificazioni, direzione trade, fasce, periodi, metriche nette, aggregazioni, Excel, flag e legacy, equivalenza ON/OFF e V1 immutata.
- Test HTTP di integrazione: flag salvato e ricaricato, Run History ON, Excel analitico, asset web accessibile, nessuna chiamata `IbkrApiClient.placeOrder` e nessuna scrittura ai risultati V1.
- Esperimento offline sulla run salvata con maxHoldingBars=11: equivalenza esatta dell'intero portfolio, Execution, costi e analisi preesistente a pari Run ID; nuova run ON persistita con summary ed equity identiche; sorgente e configurazione corrente non riscritti.
- Frontend: 69 test superati, zero fallimenti. Build e verifica sintattica superate; filtro dei cinque campi, checkbox, stato OFF, rendering testuale, grafici e Run History verificati.
- Preservazione V1: 59 hash SHA-256 invariati, inclusi motore congelato, snapshot/versioni, pagine V1 e modulo PAPER. Nessuna modifica al motore del portfolio V2, ai detector condivisi o ai parametri di trading.
- Nessuna connessione IBKR e nessun ordine trasmesso durante sviluppo o test.

Gli 8 esclusi sono validazioni locali o esperimenti esistenti che richiedono attivazione separata: PatternStablePromotionTest (2), PatternV1LocalValidationTest (1) e cinque esperimenti V2 per Bollinger ATR, Capital Shadow, CloseStrength, Pattern Range e Capital Limits. Non sono dichiarati superati in questa esecuzione. Il nuovo esperimento Market Regime è invece stato eseguito.

I log iniziali di compilazione/test hanno permesso di correggere la compatibilità del nuovo campo OFF con il formato V1 e con i payload HTTP legacy, senza modificare V1. Il log finale `maven-tests.log` riporta l'esito dopo queste correzioni. Il Browser non è disponibile nella sessione: nessuna verifica visuale viene dichiarata, oltre ai controlli HTTP/markup/comportamento frontend.

Tolleranze: riferimento DI pubblicato e arrotondato 1e-4, ADX 0,002; serie analitiche EMA e altri valori deterministici 1e-10/1e-12; classificazione del PnL zero 1e-8. Il confronto economico ON/OFF è esatto a pari identità della run. Non sono state modificate soglie, trade o risultati per ottenere la parità.

Artefatti: `V2_MARKET_REGIME_ON.xlsx`, `V2_MARKET_REGIME_ON.json`, `regime-summary.json`, `parity.json`, `REPORT.md`, `changed-files.txt`, `v1-preservation.json`, log Maven/frontend. I primi 199 dati di ogni simbolo possono non consentire EMA200: il regime principale resta INSUFFICIENT_DATA senza perdere i valori parziali o eliminare trade. Non sono state scaricate barre anteriori alla run né ottimizzate soglie.
