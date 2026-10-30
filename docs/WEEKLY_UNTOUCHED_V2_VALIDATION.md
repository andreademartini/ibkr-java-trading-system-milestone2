# Verifica dei livelli settimanali storici LONG/SHORT

Verifica del 28 ottobre 2026, Java 21.0.11. Regole e limiti: [WEEKLY_UNTOUCHED_V2.md](WEEKLY_UNTOUCHED_V2.md).

## Test eseguiti

| Suite | Test | Esito |
|---|---:|---|
| WeeklyTteTest — regressione delle regole precedenti | 67 | Tutti passati |
| HistoricalWeeklyTteTest — nuovi livelli, direzioni, capitale e replay | 22 | Tutti passati |
| WeeklyTteHttpIntegrationTest — API, Excel e isolamento dal broker | 2 | Tutti passati |
| HistoricalWeeklyTteLocalTest — CSV reali locali e tre modalità | 1 | Passato |
| Intera suite frontend | 120 | Tutti passati |

I test HTTP confrontano le configurazioni Pattern V1/V2/V3 prima e dopo il workflow e verificano che non siano stati inviati ordini al broker. Il test storico controlla anche che una chiusura della settimana di origine esattamente sul massimo non consumi anticipatamente il proprio livello.

Comandi PowerShell usati con Java 21 e repository Maven locale:

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/De Martini Andrea/.m2/repository' -q '-Dtest=WeeklyTteTest,HistoricalWeeklyTteTest,WeeklyTteHttpIntegrationTest,HistoricalWeeklyTteLocalTest' '-Dweeklytte.historical.local=true' test
node --check src/main/resources/static/weekly-high-tte.js
node --test src/test/js/*.test.js
```

Log: `target/weekly-untouched-v2-tests.log` e `target/weekly-untouched-v2-frontend.log`. Non è stata eseguita l'intera suite Java del repository; è stata compilata l'intera base Java ed eseguita la suite mirata del modulo, inclusi i test HTTP Spring.

## Simulazione offline

Universo: AAPL, MSFT, NVDA; dati CSV locali; periodo operativo predefinito dal 2017 alla data finale configurata, con ricostruzione dei livelli dal 2010. Snapshot e archivio delle run di verifica sono isolati in una directory temporanea del test: non modificano l'archivio operativo dell'utente. Gli Excel e il riepilogo restano disponibili nella cartella seguente.

| Modalità | Trade chiusi | LONG chiusi | SHORT chiusi |
|---|---:|---:|---:|
| Breakout | 649 | 395 | 254 |
| TTE | 99 | 48 | 51 |
| TTE partial | 97 | 46 | 51 |

L'inventario finale contiene 374 livelli attivi e 3.498 raggiunti, senza livelli incerti per lacune nei dati del campione. Il replay della baseline restituisce la stessa curva del capitale e lo stesso numero di trade. I test sintetici verificano inoltre inventario dei livelli e P&L del replay.

Artefatti:

- [Breakout Excel](../outputs/weekly-untouched-v2-validation/WEEKLY_HIGH_BREAKOUT.xlsx)
- [TTE Excel](../outputs/weekly-untouched-v2-validation/WEEKLY_HIGH_TTE.xlsx)
- [TTE partial Excel](../outputs/weekly-untouched-v2-validation/WEEKLY_HIGH_TTE_PARTIAL.xlsx)
- [Riepilogo JSON con parametri identificativi e metriche](../outputs/weekly-untouched-v2-validation/summary.json)

Questi risultati verificano funzionamento, direzioni e riproducibilità; non costituiscono una validazione della redditività. Restano i limiti dichiarati su sequenza intraday, storico adjusted, calendario e simulazione del prestito titoli SHORT. Nessun collegamento PAPER/LIVE è stato abilitato.
