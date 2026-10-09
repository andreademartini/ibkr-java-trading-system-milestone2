# CAPITAL SKIPPED SIGNALS — SHADOW ANALYSIS

Solo PATTERN_V2, ricerca storica. Il checkbox **Enable Capital Skip Shadow Analysis** è in Filters / Configuration; default OFF. Il parametro JSON è capitalSkipShadowAnalysisEnabled e viene preservato da tutti i builder, configurazioni, preset e Run History. Quando ON, il modulo lavora dopo il backtest, senza modificarne il portfolio.

La tab **Capital Skips** mostra Overview, Executed vs Shadow, By Symbol, By Period, Shadow Trades e contesti. **ANALYZE SAVED CAPITAL SKIPS** calcola l’analisi separatamente per una nuova run con audit registrati. L’azione esplicita funziona anche se l’analisi automatica era OFF; la configurazione della run resta immutata.

API: POST /api/backtest/pattern-v2/runs/{id}/capital-skips-analysis. Salva in data/pattern-v2/shadow-analysis/{id}.json senza riscrivere il file della run. GET della run e export Excel includono l’analisi associata. Una run legacy senza audit richiede il replay sul suo dataset salvato; il contesto storico non viene ricostruito arbitrariamente.

Nuove run OFF conservano gli audit separati ma non simulano shadow. Le vecchie configurazioni usano default false; vecchie run senza audit mostrano N/D. I normali UUID delle nuove simulazioni restano casuali. Il confronto di regressione utilizza un’identità fissata interna per poter verificare anche gli ID dei trade.

La baseline del primo test usa STOP_FIRST; altre configurazioni ereditano la propria SameBarPolicy. Configurazione, range/close filters, costi e regole di trading non vengono ottimizzati.

Independent theoretical observations, potentially correlated with executed trades. Closed/completed outcomes only; periods by signal date. Net R denominator = initial price risk × quantity. Winner/loser R are NET R. No shadow portfolio CAGR or equity curve. Competition lists are pending entry candidates, including candidates subsequently rejected for other reasons. Aggregate open risk = sum of initial price risks, excluding costs. Invested capital = gross market value at decision open.

Net Expectancy R è media di Net PnL / rischio monetario iniziale, distinto dal R di prezzo precedente. PF senza perdite è N/D. Dati incompleti/non eseguibili sono separati dai completati. Equity alla chiusura del segnale e contesto all’open sono distinti. I periodi sono cohort per Signal Date. Il giudizio è descrittivo: almeno 30 osservazioni per gruppo e PF netto, Net Expectancy R e win rate concordi; altrimenti Inconclusive. Non dimostra significatività, equivalenza o superiorità causale.

Excel: CAPITAL_SKIPS_SUMMARY, SHADOW_TRADES, CAPITAL_SKIPS_BY_SYMBOL, CAPITAL_SKIPS_BY_PERIOD, CAPITAL_SKIPS_CONTEXT si aggiungono quando l’analisi è disponibile; i fogli e le colonne precedenti sono preservati. CONFIGURATION include sempre CapitalSkipShadowAnalysisEnabled.

Esperimento locale opt-in: Maven -Dtest=CapitalSkipShadowExperimentTest -DcapitalShadowExperiment=true test. Usa il dataset della baseline archiviata in outputs/capital-shadow-evidence/baseline-before.json, verifica OFF/ON e parità precedente, salva una nuova run ON ed export. Non parte nella suite ordinaria.

[Risultati completi e limiti](../outputs/capital-shadow-evidence/REPORT.md).
