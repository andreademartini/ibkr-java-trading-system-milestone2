# Stable: correzione delle uscite sui gap

Nuove versioni: **PATTERN_V1_STABLE 2.2** e **PATTERN_V2_STABLE 1.1**.
Un'apertura oltre stop o target chiude il trade all'OPEN, prima dei controlli
intraday. Commissioni e slippage vengono applicati al prezzo effettivo. I gap
valgono sia per le perdite sia per i profitti, su LONG e SHORT.

## Confronto sul dataset congelato

Capitale iniziale: 10.000 USD. Periodo: 2010-01-01 / 2026-09-08.

| Strategia | Versione precedente | Equity precedente USD | Versione corretta | Equity corretta USD | Trade prima / dopo | Uscite diverse fra segnali comuni |
|---|---|---:|---|---:|---:|---:|
| V1 Stable | 2.1 | 72.226,36 | 2.2 | 57.076,48 | 1.403 / 1.393 | 164 |
| V2 Stable | 1.0 | 76.243,94 | 1.1 | 63.596,21 | 1.108 / 1.108 | 129 |

Il confronto mantiene identici dataset e parametri, cambiando solo la politica
di esecuzione. L'equity diversa modifica anche quantità e disponibilità di
capitale dei trade successivi. Il numero delle uscite diverse si riferisce ai
segnali presenti in entrambe le simulazioni, non a tutti i trade modificati.

`comparison.json` contiene riepiloghi, commissioni/slippage e dettaglio delle
uscite cambiate. Gli Excel riportano i risultati corretti e la provenienza:

- [V1 Stable 2.2](PATTERN_V1_STABLE_2_2.xlsx)
- [V2 Stable 1.1](PATTERN_V2_STABLE_1_1.xlsx)

## Integrità e comportamento

Verifiche completate: `mvn verify` (552 test, zero errori/fallimenti, 12 test
opzionali esclusi), 109 test frontend, build frontend e confronto storico
opzionale `GapStableDatasetComparisonTest`, tutti superati.

La vecchia V2 1.0 è stata confrontata integralmente con il portafoglio del
riferimento, normalizzando soltanto l'UUID. Il test verifica che i riferimenti
e la configurazione Research originale non siano stati modificati. Le nuove
simulazioni di verifica vengono archiviate in directory temporanee di test.

Gli snapshot storici V1 2.0/2.1 e V2 1.0 mantengono la vecchia politica, gli hash
e la leggibilità. PAPER resta su V1 2.1 e V2 1.0. La verifica non avvia ordini o
connessioni al broker e non riavvia il backend operativo.

Le pagine mostrano le nuove versioni dopo la distribuzione/il riavvio del backend
aggiornato. Le run già salvate conservano i loro risultati: per ottenere i nuovi
risultati occorre eseguire una nuova run.

Regole, compatibilità e comandi di verifica sono descritti in
[STABLE_GAP_EXECUTION.md](../../docs/STABLE_GAP_EXECUTION.md).
