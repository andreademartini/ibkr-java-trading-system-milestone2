# Risk Percent modificabile nei backtest V1 e V2

Le pagine V1/V2 includono un campo numerico **Risk Percent %**, inizializzato
dal rischio della versione base (attualmente 4%). Si può impostare 4, 8 o
un'altra percentuale positiva fino a 100. Gli altri parametri rimangono congelati.

Le API `/api/backtest/pattern-v1/run` e `/api/backtest/pattern-v2-stable/run`
accettano soltanto `{"riskPercent":8}` oppure il body precedente vuoto. Chiavi
aggiuntive, valori non numerici, non finiti, nulli, zero o fuori intervallo sono
rifiutati con HTTP 400. `BacktestRiskParameter` centralizza queste validazioni.

`runWithRisk` crea una configurazione per la sola run, mantenendo gli archivi
delle versioni originali. Cambiando il rischio rispetto al default, la versione
della run aggiunge `-risk-test`; hash e snapshot comprendono il rischio effettivo.
Lo storico mostra la percentuale e ogni risultato mostra il rischio applicato.
L'export usa lo snapshot effettivo, conservando audit gap e fogli precedenti.
Nessun risultato storico viene sovrascritto. La scelta è per singola run e non
modifica il default congelato o i parametri PAPER.

La formula esistente destina equity × percentuale al rischio teorico. Con equity
10000, 4% significa 400 e 8% significa 800. Quantità arrotondate, massimo valore
ordine, disponibilità di capitale e limiti di posizione possono impedire il
raddoppio della quantità effettiva. Un gap può produrre perdite superiori al
rischio teorico. Stop, Target R e modello di sizing non vengono modificati.

Test: `BacktestRiskParameterTest` verifica validazione, simulazioni e persistenza
4/8%, crescita della quantità quando non limitata e integrità della run precedente;
`PatternStableHttpIntegrationTest` verifica il parametro attraverso HTTP.
I test frontend verificano l'invio del solo rischio e il blocco dei valori
invalidi, conservando gli altri campi in sola lettura.

Verifiche: `mvn verify`, `npm.cmd test`, `npm.cmd run build`. Log in
`target/editable-risk-*.log`. Non sono stati modificati il PAPER, la V3, le
risorse delle configurazioni congelate o i servizi ordini. Nessun ordine IBKR
reale viene inviato dai test. Il backend operativo non è riavviato automaticamente:
per attivare gli endpoint aggiornati occorre riavviare il backend e ricaricare
la pagina con Ctrl+F5.

Esito finale: Maven verify superato, 641 test complessivi, 624 eseguiti e
17 opzionali esclusi, zero errori/fallimenti. Superati anche 112 test frontend,
build frontend e controllo del diff. Prodotto il JAR aggiornato.
