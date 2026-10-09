# PATTERN_V2 - Limiti dinamici: implementazione e verifica

Implementata esclusivamente in V2. I tre limiti restano per singolo trade; nessun vincolo aggregato nuovo. Configurazioni legacy: FIXED_AMOUNT, percentuali predefinite 100, importi conservati. Il sizing condiviso non cambia. Equity non positiva o limiti effettivi invalidi impediscono nuovi ingressi.

## Interfaccia

CAPITAL LIMITS nella configurazione PATTERN_V2: Mode, Fixed Amount, Equity Percent per posizione, LONG e SHORT. Il valore inattivo resta salvato. Preview sull'equity iniziale e tabella dei limiti effettivi all'ingresso. Preset e replay sono manuali; confronto capitale dedicato nella Run History.

## Esperimento

Periodo: 2010-01-01 / 2026-09-08. Snapshot: 190bb01c973849fb6cb45b61da6156e8197845a92d8a3b2d5747133a8b0cf3fd. Capitale iniziale 10.000; rischio 2%; ordine 30% CURRENT_EQUITY_PERCENT; maxQuantity 1.000.000 come la baseline salvata; leva OFF; Range e CloseStrength OFF; Shadow Analysis ON in entrambe. Tutti gli altri parametri e l'ordine sequenziale sono identici. Configurazione corrente non sostituita.

Run fissa: 81c213eb-9e61-47d0-b35c-b5eb42361365; run dinamica: 4fc5cbdd-3f55-48b3-8a8f-535e3082952f. Entrambe e i rispettivi preset sono salvati nella Run History/configurazioni V2.

| Metrica | Fissa | Dinamica | Differenza dinamica - fissa |
|---|---:|---:|---:|
| Final Equity | 66.898,647543 | 75.595,023422 | 8696,375879 |
| CAGR % | 12,071861 | 12,896195 | 0,824334 |
| Max Drawdown % | 20,480015 | 20,687699 | 0,207684 |
| PF Net | 1,593266 | 1,596183 | 0,002917 |
| Expectancy Net R | 0,155907 | 0,148681 | -0,007226 |
| Trade Count | 1348 | 1344 | -4 |
| Win Rate % | 41,246291 | 41,220238 | -0,026053 |
| Capital Skips | 55 | 61 | 6 |
| Average Actual Risk % | 0,568683 | 0,600542 | 0,031859 |
| Average Order Value | 5694,926967 | 6547,324432 | 852,397466 |
| Average Gross Exposure % | 37,973846 | 39,802186 | 1,82834 |
| CAGR / Max Drawdown | 0,589446 | 0,623375 | 0,033929 |
| Net PnL | 56.898,647543 | 65.595,023422 | 8696,375879 |

CAGR ed equity finale aumentano; PF netto quasi invariato. Expectancy Net R e win rate scendono, drawdown ed esposizione crescono, capital skips aumentano di 6. Il risultato storico e' misto: non dimostra che la configurazione dinamica sia automaticamente migliore.

Expectancy Net R = media NetPnL / ActualRiskAmount sui soli trade chiusi; denominatore rischio prezzo iniziale per la quantita' effettiva. Costi inclusi una volta sola. Non e' l'expectancy prezzo preesistente. Ordini piu' grandi possono produrre maggiori costi totali pur mantenendo invariato il modello commissioni/slippage.

## Audit ed export

TRADES include tutti i limiti effettivi e nominali/quantita' desiderati e finali. CONFIGURATION conserva mode, percentuali e importi legacy. CAPITAL_LIMITS_AUDIT registra le quantita' prima/dopo ciascun vincolo nominale. SUMMARY distingue riduzioni successive e vincoli alla pari. Le tracce nominali precedono costi/cash/minimi; questi controlli restano nella logica condivisa. Gli audit mancanti nelle run legacy sono N/D, senza riscrivere i risultati.

Conteggi (riduzioni sovrapposte, non sommabili):

| Controllo | Fissa | Dinamica |
|---|---:|---:|
| Max Position - riduzioni | 303 | 0 |
| Max LONG - riduzioni aggiuntive | 0 | 0 |
| Max SHORT - riduzioni aggiuntive | 0 | 0 |
| Cash - riduzioni | 179 | 204 |
| Max LONG - vincolante alla fase nominale | 287 | 0 |
| Max SHORT - vincolante alla fase nominale | 28 | 0 |

## Verifiche

- Maven/JUnit: 363 casi, 0 fallimenti, 0 errori, 5 esperimenti esclusi dalla suite ordinaria. Maven package passato con JDK 21.
- Esperimento capitale eseguito separatamente: 1 test passato. Intero portafoglio fisso identico alla run precedente, inclusi trade, eventi, cash, equity curve, costi e metriche.
- Frontend: 52 test passati; build passata.
- Hash invariati per 27 file produttivi V1 e di rischio condiviso; regressioni V1 e altre strategie passate. Nessuna modifica al flusso IBKR/PAPER/LIVE, nessun ordine reale inviato dall'esperimento.
- Test HTTP: salvataggio, replay capitale, confronto dedicato, rifiuto del confronto filtri su parametri capitale diversi.
- Test sizing: esempi crescenti/decrescenti, LONG/SHORT, no look-ahead, dati equity invalidi, cash e costi, cinque segnali simultanei e allocazione sequenziale.

## File creati/modificati

- [PatternV2Configuration.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Configuration.java>)
- [PatternPortfolioSimulator.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternPortfolioSimulator.java>)
- [PatternV2Run.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Run.java>)
- [PatternV2Service.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Service.java>)
- [PatternV2Controller.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2Controller.java>)
- [PatternV2ExcelExporter.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2ExcelExporter.java>)
- [PatternV2CapitalLimits.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2CapitalLimits.java>)
- [PatternV2CapitalComparison.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/java/it/andreademartini/trading/backtest/patternv2/PatternV2CapitalComparison.java>)
- [pattern-v2-model.js](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/resources/static/pattern-v2-model.js>)
- [pattern-v2.js](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/resources/static/pattern-v2.js>)
- [pattern-v2.html](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/main/resources/static/pattern-v2.html>)
- [PatternV2CapitalLimitsTest.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2CapitalLimitsTest.java>)
- [PatternV2CapitalLimitsExperimentTest.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2CapitalLimitsExperimentTest.java>)
- [PatternV2Test.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2Test.java>)
- [PatternV2HttpIntegrationTest.java](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/java/it/andreademartini/trading/backtest/patternv2/PatternV2HttpIntegrationTest.java>)
- [pattern-v2.test.js](<C:/Users/De Martini Andrea/IdeaProjects/ibkr-java-trading-system-milestone2/src/test/js/pattern-v2.test.js>)

## Artefatti

- V2_BASELINE_FIXED_LIMITS.xlsx
- V2_DYNAMIC_LIMITS.xlsx
- CAPITAL_LIMITS_COMPARISON.xlsx
- comparison.json, parity.json, frozen-verification.json, test-results.json, limit-statistics.json e log delle verifiche.
