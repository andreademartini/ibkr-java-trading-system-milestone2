# PATTERN_V2 - Bollinger + ATR

Filtro implementato esclusivamente per ricerca V2. Checkbox Enable Bollinger + ATR Filter nella sezione Filters / Configuration, default OFF, parametri salvabili e ricaricabili. Solo DAILY quando ON. Nessuna modifica a pattern, entry, stop, Target R, sizing, costi, allocazione sequenziale o flussi IBKR/PAPER/LIVE.

## Convenzioni

Bollinger CLOSE con deviazione standard della popolazione, inclusa la signal bar. Calcolatore esistente riutilizzato senza modificarlo. ATR Wilder: media dei primi N True Range completi che richiedono PreviousClose; prima disponibilita' dopo N+1 barre, poi (ATR precedente * (N-1) + TR) / N. Nessun dato pre-start aggiunto allo snapshot. Dati insufficienti o ATR non positivo: BOLLINGER_ATR_INSUFFICIENT_DATA; diagnostico NON_POSITIVE_ATR per quest'ultimo caso.

LONG: (PatternLow - Lower) / ATR. SHORT: (Upper - PatternHigh) / ATR. Distanza signed, <= soglia, oltrepassamenti accettati. Estremo su tutte le barre del pattern; timestamp e prezzo registrati, parita' risolta scegliendo la prima barra. Precedenza Range, CloseStrength, Bollinger; ciascuno indipendente. Segnali bloccati dai primi due distinti da rejected Bollinger.

Filtro OFF non scarta segnali, neppure senza indicatori; audita i dati disponibili senza aggiungere eventi al portafoglio precedente. Le configurazioni legacy non sono riscritte.

## Esperimento riproducibile

Snapshot 190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd. Periodo 2010-01-01 / 2026-09-08. Equity iniziale 10.000, rischio 2%, ordine 30% CURRENT_EQUITY_PERCENT, tre limiti dinamici 100%, leva OFF. maxQuantity 1.000.000 come nella baseline salvata. Range e CloseStrength OFF in entrambe; Shadow Analysis invariata. Tutti i parametri identici salvo nome e checkbox Bollinger; periodi 20/14, stdDev 2.0, soglia 0.50 identici. Configurazione corrente non sostituita.

Baseline be10003f-0c48-4bec-9767-5ecf23f4db8a; filtro 7c6ff11b-7d6f-4baf-b164-20bab8d0d5f7. Run e preset salvati. Nessun auto-optimizer.

| Metrica | Baseline | Bollinger + ATR | Differenza |
|---|---:|---:|---:|
| Final Equity | 75.595,02342 | 31.378,75324 | -44.216,27018 |
| Net PnL | 65.595,02342 | 21.378,75324 | -44.216,27018 |
| CAGR | 12,89619 | 7,09792 | -5,79828 |
| Max Drawdown % | 20,6877 | 16,40815 | -4,27955 |
| CAGR / Max Drawdown | 0,62338 | 0,43258 | -0,19079 |
| Trades | 1344 | 402 | -942 |
| Win Rate | 41,22024 | 45,27363 | 4,05339 |
| PF Net | 1,59618 | 1,62327 | 0,02709 |
| Expectancy R | 0,31416 | 0,41383 | 0,09967 |
| Average Winner R | 2,16037 | 2,10301 | -0,05736 |
| Average Loser R | -0,98053 | -0,98359 | -0,00307 |
| Average Actual Risk % | 0,60054 | 0,82314 | 0,2226 |
| Exposure % | 39,80219 | 15,1917 | -24,61048 |
| Capital Skips | 61 | 5 | -56 |
| Total Commission | 3801,20808 | 1340,10642 | -2461,10166 |
| Total Slippage | 5297,02756 | 1345,48794 | -3951,53962 |
| Total Costs | 9098,23564 | 2685,59436 | -6412,64128 |
| Expectancy Net R | 0,14868 | 0,32466 | 0,17598 |

Expectancy R richiesta e' il R di prezzo preesistente, esclusi i costi. Expectancy Net R viene mostrata separatamente. Vincite/perdite su NetPnL; PF netto dopo costi. Gli esiti appartengono ai soli trade chiusi reali della run indicata; nessun PnL teorico attribuito ai segnali rifiutati.

## Statistiche filtro

- enabled: true
- maxDistanceAtr: 0,5
- totalDetectedSignals: 1775
- signalsEvaluated: 1775
- signalsAccepted: 453
- signalsRejected: 1322
- insufficientData: 7
- blockedByOtherFilters: 0
- unavailableIndicators: 7
- acceptanceRate: 25,52113

I 1.322 scarti includono 7 segnali con indicatori insufficienti e 1.315 scarti per distanza. Le fasce sono esclusive, con 0.50 nel bucket [0.25,0.50] e 1.00 nel bucket (0.50,1.00]. Segnali senza distanza non vengono inseriti in una fascia artificiale.

## Stabilita' temporale

| Periodo | CAGR baseline / filtro % | Max DD baseline / filtro % | PF Net baseline / filtro | Expectancy R baseline / filtro | Trade baseline / filtro |
|---|---:|---:|---:|---:|---:|
| 2010–2013 | 1,65512 / 9,2553 | 15,75084 / 7,02783 | 1,05113 / 1,95485 | 0,29861 / 0,54897 | 296 / 89 |
| 2014–2017 | 12,82836 / 5,34767 | 15,87892 / 16,40815 | 1,50464 / 1,53602 | 0,23347 / 0,32492 | 358 / 103 |
| 2018–2021 | 17,37833 / 8,31937 | 15,71138 / 10,07245 | 1,62073 / 1,79703 | 0,36757 / 0,48499 | 304 / 93 |
| 2022–2026 | 19,49626 / 5,76969 | 20,6877 / 14,74547 | 1,71389 / 1,47838 | 0,35886 / 0,33273 | 386 / 117 |

Periodi del portafoglio continuo, senza reinizializzazione del capitale. CAGR ed equity includono le posizioni valorizzate; drawdown con picco locale, esiti dei trade attribuiti alla data di uscita.

Il filtro aumenta PF/expectancy/win rate complessivi, riduce esposizione, drawdown totale e costi, ma riduce nettamente CAGR, PnL e CAGR/Drawdown. CAGR migliora solo nel 2010-2013; nel 2014-2017 il drawdown peggiora e nel 2022-end PF/expectancy peggiorano. Il miglioramento non e' uniforme nei periodi. Questo e' un singolo test storico, senza significativita' statistica dimostrata o scelta automatica della soglia.

## Test e build

Maven package/JUnit: 379 casi, 0 fallimenti, 0 errori, 6 esperimenti esclusi dalla suite ordinaria. Esperimento Bollinger eseguito separatamente e passato. Frontend 54/54, build passata. Hash invariati per 65 file V1, rischio e JoeRoss, comprese le classi riutilizzate; regressioni V1 e altre strategie passate. Nessun ordine reale nell'esperimento, privo di dipendenze broker e provider storico.

Verificati: Bollinger popolazione, ATR14 seed/Wilder, LONG/SHORT e soglia inclusiva, distanze negative, pattern una/due barre e timestamp estremo, no look-ahead, indicatori mancanti/ATR zero, indipendenza filtri, persistenza/API/Run History/Excel. L'intero portafoglio della baseline precedente e' identico con filtro OFF, inclusi cash, eventi, equity, costi e metriche.

## Export

TRADES conserva tutte le colonne e aggiunge bande, ATR, distanza, accepted, timestamp/prezzo estremo. ATR14 indica l'ATR configurato, periodo predefinito 14, documentato in CONFIGURATION. BOLLINGER_ATR_ANALYSIS include riepilogo, fasce, simbolo/direzione/pattern/Target R e periodi. BOLLINGER_ATR_SIGNALS include ogni segnale con stato e motivo; nessun esito teorico inventato. Tutti i fogli preesistenti conservati.

## File creati/modificati

- [PatternV2Configuration.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Configuration.java>)
- [PatternV2Run.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Run.java>)
- [PatternPortfolioSimulator.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternPortfolioSimulator.java>)
- [PatternV2Service.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Service.java>)
- [PatternV2Controller.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Controller.java>)
- [PatternV2RunComparison.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2RunComparison.java>)
- [PatternV2ExcelExporter.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2ExcelExporter.java>)
- [BollingerAtr.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/BollingerAtr.java>)
- [BollingerAtrAnalysis.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/BollingerAtrAnalysis.java>)
- [pattern-v2-model.js](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/resources/static/pattern-v2-model.js>)
- [pattern-v2.js](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/resources/static/pattern-v2.js>)
- [pattern-v2.html](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/resources/static/pattern-v2.html>)
- [BollingerAtrTest.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/BollingerAtrTest.java>)
- [BollingerAtrExperimentTest.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/BollingerAtrExperimentTest.java>)
- [PatternV2Test.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2Test.java>)
- [PatternV2HttpIntegrationTest.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2HttpIntegrationTest.java>)
- [pattern-v2.test.js](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/js/pattern-v2.test.js>)

Artefatti: V2_BASELINE_DYNAMIC_30.xlsx, V2_BOLLINGER_ATR_05.xlsx, comparison.json, parity.json, test-results.json e frozen-verification.json.
