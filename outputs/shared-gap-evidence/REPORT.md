# Risultati della gestione gap condivisa

Il rapporto tecnico completo è in [SHARED_GAP_EXECUTION.md](../../docs/SHARED_GAP_EXECUTION.md).

| Strategia | Equity prima | Equity dopo | Verifica |
|---|---:|---:|---|
| V1 Stable 2.2 | 57076.4777241303 | 57076.4777241303 | Parità integrale |
| V2 Stable 1.1 | 63596.2058626152 | 63596.2058626152 | Parità integrale |
| V3 Research | 76243.9396085769 | 63596.2058626152 | LEVEL_ONLY → OPEN_FIRST |

V3: il prima è una simulazione acquisita sul codice precedente, sullo stesso
dataset e configurazione; nessuna run archiviata era disponibile. Il replay
esplicito LEVEL_ONLY conserva esattamente il portafoglio precedente.

- [Excel V1](PATTERN_V1_STABLE_2_2.xlsx): 1393 trade, 103 gap stop e 64 gap target.
- [Excel V2](PATTERN_V2_STABLE_1_1.xlsx): 1108 trade, 77 gap stop e 54 gap target.
- [Excel V3](PATTERN_V3_OPEN_FIRST.xlsx): 1108 trade, 77 gap stop e 54 gap target.
- [Confronto completo e operazioni cambiate](comparison.json).
- [Manifest iniziale, dataset e hash protetti](baseline-manifest.json).

Nei tre workbook sono verificate le colonne audit, il foglio GAP_EXIT_ANALYSIS,
la corrispondenza alle esecuzioni e l'assenza di valori non finiti/errori Excel.
I file sono nuovi artefatti: non sostituiscono lo storico delle run.

Maven verify: 602 test eseguiti, 14 opzionali esclusi, nessun fallimento/errore;
109 test frontend passati e build frontend superato. Configurazioni, PAPER e
servizi ordini invariati rispetto al manifest. Nessun ordine IBKR reale inviato.
Il backend operativo non è stato riavviato.
