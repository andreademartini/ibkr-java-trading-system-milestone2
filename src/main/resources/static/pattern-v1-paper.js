(function(){
    'use strict';
    const $=id=>document.getElementById(id);
    const base='/api/pattern-v1/paper';
    let csrf, timer, busy=false, chart, series, latest;
    const number=value=>value==null?'—':Number(value).toLocaleString('it-IT',{maximumFractionDigits:3});
    const date=value=>value?new Date(value).toLocaleString('it-IT'):'—';
    const labels={PAPER_ONLY:'Modalità PAPER del backend',CONNECTION:'Connessione API',HEARTBEAT:'Heartbeat',ACCOUNT_ALLOWLIST:'Account autorizzato',PAPER_IDENTITY:'Identità simulata verificata',WRITE_CAPABILITY:'Permesso invio ordini',REALTIME_DATA:'Dati realtime',FROZEN_CONFIGURATION:'Configurazione congelata',KILL_SWITCH:'Kill switch',RECONCILIATION:'Ordini, posizioni e protezioni',OPERATIONAL_LIMITS:'Limiti operativi',MARKET_CALENDARS:'Calendari di borsa',CAPITAL:'Capitale virtuale',DAILY_LOSS:'Limite perdita giornaliera',DRAWDOWN:'Limite drawdown',MAX_POSITIONS:'Limite posizioni',MAX_DAILY_ORDERS:'Limite ordini giornalieri',AGGREGATE_EXPOSURE:'Esposizione aggregata',PRODUCTION_VALIDATION:'Validazione operativa'};
    function message(text){$('paper-message').textContent=text;}
    async function api(path,body){
        const options={credentials:'same-origin',headers:{}};
        if(body!==undefined){options.method='POST';options.headers['Content-Type']='application/json';if(csrf)options.headers['X-Paper-CSRF']=csrf;options.body=JSON.stringify(body);}
        const response=await fetch(base+path,options);
        if(!response.ok){let error;try{error=await response.json();}catch(_){error={message:'Errore HTTP '+response.status};}if(response.status===401&&path!=='/login'){csrf=null;clearInterval(timer);$('paper-login').hidden=false;$('paper-controls').hidden=true;}throw new Error(error.message||'Operazione bloccata');}
        return response.status===204?null:response.json();
    }
    function table(id,columns,rows,eventKey){
        const host=$(id);host.replaceChildren();const table=document.createElement('table'),head=document.createElement('thead'),header=document.createElement('tr');
        columns.forEach(([label])=>{const th=document.createElement('th');th.textContent=label;header.append(th);});head.append(header);table.append(head);const body=document.createElement('tbody');
        rows.forEach(row=>{const tr=document.createElement('tr');if(eventKey)tr.dataset.event=row[eventKey];columns.forEach(([,get])=>{const cell=document.createElement('td');cell.textContent=get(row)??'—';tr.append(cell);});body.append(tr);});table.append(body);host.append(table);
        if(!rows.length){const p=document.createElement('p');p.textContent='Nessuna operazione registrata.';host.append(p);}
    }
    function metrics(id,values){const host=$(id);host.replaceChildren();values.forEach(([label,value])=>{const div=document.createElement('div');div.className='paper-metric';const span=document.createElement('span'),strong=document.createElement('strong');span.textContent=label;strong.textContent=number(value);div.append(span,strong);host.append(div);});}
    function render(data){
        latest=data;const s=data.session,c=data.connection,p=data.capital,m=data.performance;
        $('paper-mode').textContent=s.mode;$('paper-mode').dataset.mode=s.mode;message(s.reason);
        const details=[['IB Gateway',c.connected?'Collegato':'Disconnesso'],['Host / porta',c.host+':'+c.port],['Conto autorizzato',c.account],['Identità PAPER',c.paperIdentityVerified?'Verificata':'NON VERIFICATA'],['Ultima connessione',date(c.connectedAt)],['Heartbeat',date(c.heartbeat)],['Riconciliazione',date(s.lastReconciliation)],['Versione',s.strategyVersion],['Hash',s.configurationHash],['Timeout',s.frozen.configuration.global.maxHoldingBars+' barre'],['Ultimo segnale',date(s.lastSignalAt)],['Prossime aperture',Object.entries(data.nextSessions).map(([symbol,time])=>symbol+' '+date(time)).join(' · ')||'Calendario non disponibile']];
        const dl=$('paper-connection');dl.replaceChildren();details.forEach(([label,value])=>{const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=value||'—';dl.append(dt,dd);});
        const checks=$('paper-checks');checks.replaceChildren();data.checks.forEach(c=>{const li=document.createElement('li');li.dataset.pass=c.passed;li.textContent=(labels[c.name]||c.name)+': '+(c.passed?'OK':c.reason);checks.append(li);});
        metrics('paper-capital',[['Capitale iniziale',s.initialCapital],['Equity V1',p.equity],['Disponibile',p.available],['Impegnato',p.committed],['Esposizione LONG',p.longExposure],['Esposizione SHORT',p.shortExposure],['PnL realizzato',p.realized],['PnL non realizzato',p.unrealized],['PnL totale netto',Number(p.equity)-Number(s.initialCapital)],['Drawdown corrente %',p.drawdownPercent]]);
        metrics('paper-performance',[['Rendimento cumulato %',m.cumulativeReturnPercent],['PF netto',m.profitFactorNet],['Win Rate %',m.winRate==null?null:Number(m.winRate)*100],['Expectancy R',m.expectancyR],['Max drawdown %',m.maximumDrawdownPercent],['Trade chiusi',m.closedTrades],['Posizioni aperte',m.openPositions],['Commissioni IBKR',p.commissions]]);
        if(m.shadow)metrics('paper-shadow-performance',[['Trade SHADOW',m.shadow.closedTrades],['PF netto SHADOW',m.shadow.profitFactorNet],['Win Rate SHADOW %',m.shadow.winRate==null?null:Number(m.shadow.winRate)*100],['Rendimento SHADOW %',m.shadow.cumulativeReturnPercent]]);
        $('paper-pending-fees').textContent=m.commissionsPending?'Commissioni non ancora ricevute per '+m.commissionsPending+' esecuzioni: il PnL è provvisorio.':'Commissioni aggiornate dalle esecuzioni IBKR.';
        const tradeColumns=[['Simbolo',t=>t.symbol],['Direzione',t=>t.direction],['Pattern',t=>t.pattern],['Quantità residua',t=>number(Number(t.entered)-Number(t.exited))],['Entry reale',t=>number(Number(t.entryValue)/Number(t.entered))],['Stop',t=>number(t.stop)],['Target',t=>number(t.target)],['Holding bars',t=>t.holdingBars],['Uscita',t=>t.exitReason],['Commissioni',t=>number(t.commission)],['PnL realizzato netto',t=>number(Number(t.realized)-Number(t.commission))]];
        table('paper-positions',tradeColumns,s.trades.filter(t=>Number(t.entered)>Number(t.exited)));table('paper-closed',tradeColumns,s.trades.filter(t=>Number(t.entered)>0&&Number(t.entered)===Number(t.exited)));
        table('paper-orders',[['ID',o=>o.orderId],['Perm ID',o=>o.permId||'—'],['Simbolo',o=>o.symbol],['Ruolo',o=>o.role],['Lato',o=>o.action],['Quantità',o=>o.quantity],['Filled',o=>number(o.filled)],['Stato',o=>o.status],['Stop',o=>number(o.stopPrice)],['Limit',o=>number(o.limitPrice)]],s.orders);
        table('paper-signals',[['Data',r=>r.signal.detection.signalDate],['Simbolo',r=>r.symbol],['Pattern',r=>r.signal.detection.patternType],['Direzione',r=>r.signal.detection.direction],['Modalità',r=>r.mode],['Entry prevista',r=>r.entrySession],['Stato',r=>r.status],['Motivo',r=>r.reason]],s.signals);
        table('paper-comparison',[['Segnale',t=>t.signalDate+' '+t.symbol],['Entry teorica',t=>number(t.expectedEntry)],['Entry PAPER',t=>number(Number(t.entryValue)/Number(t.entered))],['Quantità teorica',t=>t.expectedQuantity],['Quantità eseguita',t=>number(t.entered)],['Slippage previsto %',t=>number(t.expectedSlippage)],['Differenza entry',t=>number(Number(t.entryValue)/Number(t.entered)-Number(t.expectedEntry))],['Exit teorica',t=>number(t.expectedExit)],['Exit PAPER',t=>Number(t.exited)>0?number(Number(t.exitValue)/Number(t.exited)):'—'],['Motivo exit',t=>t.exitReason]],s.trades);
        table('paper-events',[['Data / ora',e=>date(e.time)],['Evento',e=>e.type],['Simbolo',e=>e.symbol],['Ordine',e=>e.orderId],['Messaggio',e=>e.message]],data.events.slice().reverse(),'type');
        const limits=$('paper-limitations');limits.replaceChildren();data.limitations.forEach(text=>{const li=document.createElement('li');li.textContent=text;limits.append(li);});
        $('paper-frozen').textContent=JSON.stringify(s.frozen,null,2);
        if(!document.activeElement||!$('paper-limits').contains(document.activeElement))Object.entries(s.limits).forEach(([key,value])=>{const input=$('paper-limits').elements.namedItem(key);if(input)input.value=value??'';});
        $('paper-reset').disabled=busy||!s.killSwitch;
        if(window.LightweightCharts){
            chart ||= LightweightCharts.createChart($('paper-equity'),{autoSize:true,height:300});series ||= chart.addSeries(LightweightCharts.LineSeries,{color:'#167b46',title:'Equity PAPER'});
            const unique=new Map(s.equityCurve.map(point=>[Math.floor(new Date(point.time).getTime()/1000),Number(point.equity)]));series.setData([...unique].sort((a,b)=>a[0]-b[0]).map(([time,value])=>({time,value})));chart.timeScale().fitContent();
        }
    }
    async function status(){if(!csrf||busy)return;try{render(await api('/status'));}catch(e){message(e.message);}}
    function authenticated(){ $('paper-login').hidden=true;$('paper-controls').hidden=false;clearInterval(timer);timer=setInterval(status,5000);status();}
    async function command(path,extra={}){if(busy)return;busy=true;document.querySelectorAll('.paper-actions button').forEach(b=>b.disabled=true);try{render(await api(path,{commandId:crypto.randomUUID(),...extra}));}catch(e){message(e.message);}finally{busy=false;document.querySelectorAll('.paper-actions button').forEach(b=>b.disabled=false);if(latest)$('paper-reset').disabled=!latest.session.killSwitch;}}
    $('paper-login').addEventListener('submit',async event=>{event.preventDefault();const token=$('paper-token').value;$('paper-token').value='';try{const result=await api('/login',{token});csrf=result.csrf;authenticated();}catch(e){message(e.message);}});
    for(const [id,path] of [['paper-start','/start'],['paper-stop','/stop'],['paper-refresh','/refresh'],['paper-connect','/connect'],['paper-shadow','/shadow']])$(id).addEventListener('click',()=>command(path));
    $('paper-kill').addEventListener('click',()=>{const confirmation=prompt('Blocca nuovi ingressi e annulla solo le aperture non eseguite. Gli stop restano attivi. Digita ATTIVA KILL SWITCH');if(confirmation==='ATTIVA KILL SWITCH')command('/kill',{confirmation});});
    $('paper-reset').addEventListener('click',()=>{const confirmation=prompt('Il reset richiede riconciliazione e non avvia il trading. Digita RESET KILL SWITCH');if(confirmation==='RESET KILL SWITCH')command('/reset',{confirmation});});
    $('paper-limits').addEventListener('submit',event=>{event.preventDefault();const limits={};for(const key of ['dailyLoss','portfolioDrawdownPercent','maxOpenPositions','maxNewOrdersPerDay','maxAggregateExposurePercent'])limits[key]=Number($('paper-limits').elements.namedItem(key).value);command('/settings',{limits});});
    $('paper-logout').addEventListener('click',async()=>{try{await api('/logout',{});}catch(e){message(e.message);}csrf=null;clearInterval(timer);$('paper-login').hidden=false;$('paper-controls').hidden=true;message('Accesso terminato. Lo stato operativo del backend rimane invariato.');});
    function tab(paper){$('paper-panel').hidden=!paper;$('v1-backtest-panel').hidden=paper;$('paper-tab-paper').classList.toggle('selected',paper);$('paper-tab-backtest').classList.toggle('selected',!paper);if(paper)status();}
    $('paper-tab-paper').addEventListener('click',()=>tab(true));$('paper-tab-backtest').addEventListener('click',()=>tab(false));
    api('/access').then(info=>{$('paper-access-info').textContent=info.adminConfigured?'Controlli protetti da sessione amministrativa.':'Accesso non configurato: imposta una volta PATTERN_PAPER_ADMIN_TOKEN nell’ambiente del backend (almeno 32 caratteri).';}).catch(e=>message(e.message));
})();
