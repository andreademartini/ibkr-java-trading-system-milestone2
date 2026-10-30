# TREND_PULLBACK_REBOUND_V3_RESEARCH

Aggiornamento tick: la versione 3.1 usa un resolver offline versionato e prevalidazione FAIL_FAST. Per la specifica corrente, la causa della mappa vuota e i limiti degli adjusted vedere [V3_MINIMUM_TICK_RESOLUTION.md](V3_MINIMUM_TICK_RESOLUTION.md). La descrizione della sola mappa riportata sotto appartiene all'implementazione iniziale.

Pagina: /trend-pullback-rebound-v3.html. API e persistenza separate: /api/backtest/trend-pullback-rebound-v3 e data/trend-pullback-rebound-v3-v1.

Il segnale riutilizza gli indicatori V1: tre chiusure strettamente decrescenti (quattro osservazioni), chiusura del segnale strettamente sopra SMA200, ATR Wilder al segnale. I corpi delle candele non sono un filtro.

Solo la sessione successiva può entrare. Open deve essere strettamente sotto High della barra del segnale; High deve raggiungere High segnale + entryBufferTicks × tick specifico. Slippage BUY applicato al trigger. Nessun rinvio alla quinta barra.

minimumTickSizes è una mappa esplicita per simbolo, salvata nella configurazione e nel fingerprint. Nessun tick universale di fallback. I CSV Stooq locali non contengono metadati di tick: senza una fonte verificata viene registrato MINIMUM_TICK_UNAVAILABLE. La mappa deve essere adeguata allo strumento, al periodo e alla base prezzi del dataset; una singola costante per simbolo non modella variazioni storiche delle regole di tick o griglie dipendenti dal prezzo.

Stop = esecuzione meno stopLossAtrMultiplier × ATR al segnale. RECOVERY deciso al close sopra SMA5 ed eseguito al prossimo open; gap stop prioritario. Timeout al close della quinta seduta, ingresso contato come prima. analysisTakeProfitAtrMultiplier modifica solo diagnostica.

Sulla barra di ingresso il LOW potrebbe precedere il BUY STOP. Se il LOW attraversa lo stop viene registrata ambiguità ed eseguito conservativamente lo stop, senza attribuire al LOW una sequenza intraday conosciuta. Nessun GAP_STOP usa l'open precedente all'ingresso.

PrePullbackHigh esclude le tre barre del pullback: ultime N sessioni completate prima della prima barra decrescente. Serve copertura completa; dati mancanti producono PRE_PULLBACK_HIGH_UNAVAILABLE. Parità di massimo: barra più recente.

MFE e tocchi dei target sono diagnostiche retrospettive. Sulle barre di uscita all'open si osserva solo l'open; sulle barre di stop il massimo giornaliero è un limite superiore e non un guadagno necessariamente realizzabile. I campi MFEIsDailyUpperBound e TargetTouchExecutionAmbiguous rendono esplicito questo limite. Non esiste un take profit operativo.

Excel conserva i fogli V1 e aggiunge ENTRY_SIGNAL_AUDIT, PROFIT_SPACE_ANALYSIS, PROFIT_SPACE_BY_SYMBOL e ENTRY_MODE_COMPARISON. Fasce [limite inferiore, limite superiore), più UNAVAILABLE. I sottogruppi sono statistiche descrittive delle operazioni realmente eseguite, non portafogli risimulati. Colonne contenenti 1_5ATR usano il moltiplicatore analitico salvato nella run (default 1.5).

Il servizio ricalcola V1 sul medesimo snapshot e parametri di benchmark; non ricostruisce risultati V1 da quelli V3. Le priorità di capitale restano alfabetiche; proventi intraday non finanziano gli ingressi dello stesso giorno.

Test e report: con JDK21, mvn test. V3LocalReportTest genera outputs/trend-pullback-rebound-v3-validation/TREND_PULLBACK_REBOUND_V3_RESEARCH.xlsx e summary.json usando solo CSV locali, con fine periodo 2026-09-02. Questo report documenta anche le esclusioni dati e l'assenza di tick verificati, senza inventare performance.

Classi V1/V2, strategie PATTERN, WEEKLY_LEVEL_MOMENTUM e PAPER non modificate. Il catalogo contiene un identificatore esclusivamente research. Nessuna dipendenza dal broker nel motore V3.
