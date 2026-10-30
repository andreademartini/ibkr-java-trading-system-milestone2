(function(){'use strict';
const $=id=>document.getElementById(id),base='/api/backtest/pattern-v2-stable';let chart,tradeChart,activeRun,selectedTrade,shownTrades=[],tradeRows=[],ready=false,requestSequence=0;
const n=v=>v==null?'N/D':typeof v==='number'?v.toLocaleString('it-IT',{maximumFractionDigits:3}):String(v);
async function api(path,method='GET',body){const signal=method==='GET'&&typeof AbortSignal!=='undefined'?AbortSignal.timeout(15000):undefined;let response;try{response=await fetch(path,{method,...(signal?{signal}:{}),...(body===undefined?{}:{headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})});}catch(error){throw Error(signal?.aborted?'Timeout nel caricamento: '+path+'. Verifica il backend e ricarica la pagina.':'Backend non raggiungibile: '+path+'. '+error.message);}if(!response.ok){let data={};try{data=await response.json();}catch{}throw Error(data.message||data.detail||'HTTP '+response.status);}return response.json();}
function table(id,cols,rows,onSelect){
const host=$(id);host.replaceChildren();const t=document.createElement('table'),header=document.createElement('tr'),nodes=[];
for(const [title] of cols){const c=document.createElement('th');c.textContent=title;header.append(c);}t.append(header);
for(const r of rows){const row=document.createElement('tr');for(const [,get] of cols){const c=document.createElement('td');c.textContent=n(get(r));row.append(c);}
if(onSelect){row.tabIndex=0;row.onclick=()=>onSelect(r);row.onkeydown=e=>{if(e.key==='Enter')onSelect(r);};}t.append(row);nodes.push(row);}host.append(t);return nodes;
}
const valueAt=(row,path)=>path.split('.').reduce((value,key)=>value?.[key],row);
const columns=definitions=>definitions.map(([label,path,format])=>[label,row=>format?format(valueAt(row,path)):valueAt(row,path)]);
async function action(f){try{$('stable-v2-run').disabled=true;await f();}catch(e){$('stable-v2-status').textContent=e.message;}finally{$('stable-v2-run').disabled=!ready;}}
function show(run,legacy=false){activeRun=run;const p=run.portfolio,m=p.summary;$('stable-v2-status').textContent='Run '+run.runId+' caricata';$('stable-v2-used').textContent=(legacy?'STORICO RESEARCH V2':'PATTERN_V2 STABLE '+(run.strategyVersion||'N/D'))+' | '+p.actualStartDate+' → '+p.actualEndDate+' | Market Regime Filter '+(run.configuration.marketRegimeFilterEnabled?'ON':'OFF');$('stable-v2-snapshot').textContent=JSON.stringify(run.configuration,null,2);
const g=run.configuration.global,fees=g.commission||{};
$('stable-v2-coverage').textContent='Richiesto '+p.startDate+' → '+p.endDate+'. Copertura effettiva '+p.actualStartDate+' ? '+p.actualEndDate+'. CAGR sulla copertura effettiva.';
const orderLimit=g.maxOrderValueMode==='CURRENT_EQUITY_PERCENT'?n(g.maxOrderValuePercent)+'% equity (limite dinamico)':n(g.maxOrderValue);
const feeDescription=fees.model==='IBKR_US_FIXED'?'IBKR_US_FIXED: '+n(fees.perShare)+' USD/azione, minimo '+n(fees.minimumPerOrder)+' USD, massimo '+n(fees.maximumPercentOrderValue)+'% del valore ordine':'Importo fisso: '+n(fees.fixedPerOrder)+' USD/ordine';
$('stable-v2-applied-limits').textContent='Parametri applicati a questo risultato: maxOrderValue = '+orderLimit+'; Risk Percent = '+n(g.riskPercent)+'%; maxQuantity = '+n(g.maxQuantity)+'; commissioni '+feeDescription+'; slippage '+n(g.slippagePercentPerSide)+'% per esecuzione. Rischio salvato nella run; gli altri parametri restano congelati.';
PortfolioSummary.render($('stable-v2-metrics'),run);
  table('stable-v2-directions',columns(directionColumns),Object.entries(p.directionResults||{}).map(([direction,metrics])=>({direction,metrics})));
table('stable-v2-symbols',columns(symbolColumns),p.symbolResults||run.analysis?.symbolResults||[]);
const comparisonRows=(p.symbolResults||[]).filter(row=>row.researchBaseline!=null);
$('stable-v2-comparison').hidden=!comparisonRows.length;
$('stable-v2-comparison-note').textContent=comparisonRows.length?'Il confronto Research usa STOP/TARGET in R sullo stesso periodo. PF monetario e PF in R restano separati.':'Confronto Research non disponibile nella run selezionata.';
table('stable-v2-comparison',columns(comparisonColumns),comparisonRows);
$('stable-v2-trade-symbol').replaceChildren(new Option('Tutti',''));
for(const symbol of [...new Set(p.trades.map(t=>t.symbol))].sort())$('stable-v2-trade-symbol').add(new Option(symbol,symbol));
$('stable-v2-event-filter').replaceChildren(new Option('Tutti',''));
for(const type of [...new Set((p.events||[]).map(e=>e.eventType))].sort())$('stable-v2-event-filter').add(new Option(type,type));
$('stable-v2-warnings').replaceChildren();for(const warning of p.warnings||[]){const item=document.createElement('li');item.textContent=warning;$('stable-v2-warnings').append(item);}
renderTrades();renderEvents();
const periodRows=[...(run.analysis?.periods??[]),...(run.analysis?.yearly??[])];
table('stable-v2-periods',[['Periodo',r=>r.label],['Trades',r=>r.trades],['CAGR',r=>r.cagr],['Max DD %',r=>r.maxDrawdownPercent],['PF Net',r=>r.pfNet],['Net PnL',r=>r.netPnl]],periodRows);
$('stable-v2-periods').hidden=!periodRows.length;$('stable-v2-periods-note').textContent=periodRows.length?'':'Analisi dei periodi non disponibile nella run selezionata.';
PatternV2Regime.render(run);PatternV2Filter.render(run);
$('stable-v2-filter-content').hidden=!run.marketRegimeFilterAnalysis;
if(!run.marketRegimeFilterAnalysis)$('v2-regime-filter-status').textContent='Analisi del filtro non disponibile nella run selezionata. Market Regime Filter '+(run.configuration.marketRegimeFilterEnabled?'ON':'OFF')+'.';
if(!run.marketRegimeAnalysis)$('v2-regime-status').textContent='Market Regime Analysis '+(run.configuration.marketRegimeAnalysisEnabled?'non disponibile nella run selezionata.':'OFF nella run selezionata.');
$('market-regime-filter-analysis').hidden=false;$('market-regime-analysis').hidden=false;
$('stable-v2-results').hidden=false;$('stable-v2-output').hidden=false;
$('stable-v2-export').hidden=false;$('stable-v2-export').href=(legacy?'/api/backtest/pattern-v2':base)+'/runs/'+run.runId+'/export/excel';
if(window.LightweightCharts){chart?.remove();chart=LightweightCharts.createChart($('stable-v2-equity'),{autoSize:true,height:360,layout:{attributionLogo:true},timeScale:{timeVisible:false}});
chart.addSeries(LightweightCharts.LineSeries,{color:'#0f766e',title:'Equity'}).setData(p.equityCurve.map(v=>({time:v.date,value:Number(v.equity)})));
chart.addSeries(LightweightCharts.HistogramSeries,{color:'#dc2626',priceScaleId:'left',title:'Drawdown'}).setData(p.equityCurve.map(v=>({time:v.date,value:-Number(v.drawdown)})));
chart.timeScale().fitContent();$('stable-v2-equity-point').textContent='';const points=new Map(p.equityCurve.map(v=>[v.date,v]));
chart.subscribeCrosshairMove(param=>{const date=typeof param.time==='object'&&param.time?param.time.year+'-'+String(param.time.month).padStart(2,'0')+'-'+String(param.time.day).padStart(2,'0'):param.time;const point=points.get(date);if(point)$('stable-v2-equity-point').textContent=date+' | Equity '+n(point.equity)+' | DD '+n(point.drawdown)+' ('+n(point.drawdownPercent)+'%) | Cash '+n(point.cash)+' | Long MV '+n(point.longMarketValue)+' | Short liability '+n(point.shortMarketValue)+' | Gross Exposure '+n(point.grossExposurePercent)+'% | Open Positions '+point.openPositions;});}}
function percent(value,fraction=false){return value==null?'N/D':n(fraction?value*100:value)+'%';}
const directionColumns=[['Direction','direction'],['Signals','metrics.signalsDetected'],['Trades','metrics.trades'],['Closed','metrics.closedTrades'],['Open','metrics.openTrades'],['Wins','metrics.wins'],['Losses','metrics.losses'],['Breakevens','metrics.breakevens'],['Win Rate','metrics.winRate',v=>percent(v,true)],['PF (PnL)','metrics.profitFactor'],['PF (R)','metrics.profitFactorR'],['Expectancy R','metrics.expectancyR'],['Realized Net PnL','metrics.realizedNetPnl'],['Unrealized Net PnL','metrics.netUnrealizedPnl'],['Max DD','metrics.maxDrawdown'],['PF (net PnL)','metrics.profitFactorNet']];
const symbolColumns=[['Symbol','symbol'],['Direction','direction'],['Pattern','pattern'],['Target R','targetR'],['Signals','metrics.signalsDetected'],['Executed','metrics.trades'],['Skipped Open','metrics.skippedPositionOpen'],['Skipped Capital','metrics.skippedCapital'],['Closed','metrics.closedTrades'],['Open','metrics.openTrades'],['Wins','metrics.wins'],['Losses','metrics.losses'],['Breakevens','metrics.breakevens'],['Timeouts','metrics.timeouts'],['Win Rate','metrics.winRate',v=>percent(v,true)],['PF (PnL)','metrics.profitFactor'],['PF (R)','metrics.profitFactorR'],['Expectancy R','metrics.expectancyR'],['Average R','metrics.averageR'],['Avg Winner R','metrics.averageWinnerR'],['Avg Loser R','metrics.averageLoserR'],['Realized Gross PnL','metrics.realizedGrossPnl'],['Realized Net PnL','metrics.realizedNetPnl'],['Unrealized PnL','metrics.unrealizedPnl'],['Net Unrealized PnL','metrics.netUnrealizedPnl'],['Max DD','maxDrawdown'],['Actual Risk Avg %','metrics.averageActualRiskPercent'],['Average Order Value','metrics.averageOrderValue'],['Executed MFE R','metrics.mfeR'],['Executed MAE R','metrics.maeR'],['PF (net PnL)','metrics.profitFactorNet']];
const comparisonColumns=[['Symbol','symbol'],['Direction','direction'],['Pattern','pattern'],['R','targetR'],['Research occurrences','researchBaseline.occurrences'],['Research PF R','researchBaseline.profitFactor'],['V2 resolved PF R','resolvedProfitFactorR'],['Research Exp. R','researchBaseline.expectancyR'],['V2 resolved Exp. R','resolvedExpectancyR'],['Skipped overlap','metrics.skippedPositionOpen'],['Skipped capital','metrics.skippedCapital'],['Warnings','warnings',v=>(v||[]).join(', ')]];
const tradeColumns=[['ID','number'],['Symbol','symbol'],['Pattern','pattern'],['Direction','direction'],['Target R','targetR'],['Signal','signalDate'],['Entry Date','entryDate'],['Entry','entryPrice'],['Stop','stopPrice'],['Target','takeProfitPrice'],['Desired Qty','desiredQuantity'],['Final Qty','finalQuantity'],['Limiting Reasons','quantityLimitingReasons',v=>v?.join(', ')||'NONE'],['SIZE_CONSTRAINED','sizeConstrained'],['Equity At Entry','equityAtEntry'],['Cash Before','availableCashBeforeEntry'],['Cash After','availableCashAfterEntry'],['Order Value','orderValue'],['Target Risk %','desiredRiskPercent'],['Actual Risk Amount','actualRiskAmount'],['Actual Risk %','actualRiskPercent'],['Exit Date','exitDate'],['Exit','exitPrice'],['Reason','exitReason'],['Closed','closed',v=>v?'Si':'OPEN'],['Remaining Qty','remainingQuantity'],['Realized Net PnL','realizedPnl'],['Unrealized Net PnL','netUnrealizedPnl'],['Mark Date','markDate'],['Mark Price','markPrice'],['Result R','resultR'],['Executed MFE R','executedMfeR'],['Executed MAE R','executedMaeR'],['Raw Bar MFE R','rawBarMfeR'],['Raw Bar MAE R','rawBarMaeR'],['Holding Bars','holdingBars'],['Same Bar Ambiguous','sameBarAmbiguous']];
function clearTradeSelection(){
selectedTrade=null;tradeChart?.destroy();tradeChart=null;
$('stable-v2-chart-section').hidden=true;$('stable-v2-tooltip').hidden=true;
for(const id of ['info','timeline','event-detail','pattern-info'])$('stable-v2-'+id).replaceChildren();
$('stable-v2-event-detail').hidden=true;
}
function resetResults(){
$('stable-v2-output').hidden=true;$('stable-v2-results').hidden=true;
$('market-regime-filter-analysis').hidden=true;$('market-regime-analysis').hidden=true;
$('stable-v2-export').hidden=true;$('stable-v2-export').href='';
activeRun=null;shownTrades=[];tradeRows=[];clearTradeSelection();chart?.remove();chart=null;
}
async function loadRun(path,method='GET',legacy=false,body){
const sequence=++requestSequence;resetResults();$('stable-v2-run').disabled=true;
$('stable-v2-status').textContent=method==='POST'?'Backtest PATTERN V2 in esecuzione...':'Caricamento run selezionata...';
try{const run=await api(path,method,body);if(sequence!==requestSequence)return;show(run,legacy);if(method==='POST')histories();}
catch(error){if(sequence===requestSequence){resetResults();$('stable-v2-status').textContent=error.message;}}
finally{if(sequence===requestSequence)$('stable-v2-run').disabled=!ready;}
}
function renderTrades(){
if(!activeRun)return;clearTradeSelection();
shownTrades=activeRun.portfolio.trades.filter(t=>!$('stable-v2-trade-symbol').value||t.symbol===$('stable-v2-trade-symbol').value);
tradeRows=table('stable-v2-trades',columns(tradeColumns),shownTrades,selectTrade);
}
function renderEvents(){
if(!activeRun)return;const events=(activeRun.portfolio.events||[]).filter(e=>!$('stable-v2-event-filter').value||e.eventType===$('stable-v2-event-filter').value);
table('stable-v2-events',columns([['Date','date'],['Symbol','symbol'],['Direction','direction'],['Pattern','pattern'],['Event','eventType'],['Trade','tradeId'],['Details','details',v=>JSON.stringify(v)]]),events.slice(0,1000));
if(events.length>1000){const note=document.createElement('p');note.textContent='Prime 1000 di '+events.length+' righe; tutti gli eventi sono salvati nel risultato JSON.';$('stable-v2-events').append(note);}
}
function selectTrade(trade){
if(!activeRun)return;selectedTrade=trade;const p=activeRun.portfolio;
for(const id of ['info','timeline','event-detail'])$('stable-v2-'+id).replaceChildren();$('stable-v2-event-detail').hidden=true;
$('stable-v2-chart-section').hidden=false;
$('stable-v2-chart-title').textContent=trade.symbol+' | '+trade.pattern+' | '+trade.direction+' | '+n(trade.targetR)+'R | T'+trade.number;
tradeRows.forEach((row,index)=>{row.className=shownTrades[index]===trade?'v1-selected':'';});
const bars=p.priceSeries?.[trade.symbol];
if(!bars?.length||typeof TradingChart==='undefined'){
tradeChart?.destroy();tradeChart=null;$('stable-v2-info').textContent=!bars?.length?'Dati prezzi non disponibili per questo trade nella run selezionata.':'Componente grafico non disponibile. Ricarica la pagina.';
}else{
const timestamp=(index,date)=>bars.find(bar=>bar.index===index)?.timestamp||bars[index]?.timestamp||(date?date+'T00:00:00Z':null);
const events=(p.events||[]).filter(e=>e.tradeId===trade.tradeId||(!e.tradeId&&e.symbol===trade.symbol&&e.pattern===trade.pattern&&e.direction===trade.direction&&e.index===trade.signalIndex&&['PATTERN_DETECTED','SIGNAL'].includes(e.eventType))).map(e=>({...e,occurredAt:timestamp(e.index,e.date),price:e.details?.entryPrice??e.details?.stopPrice??e.details?.takeProfitPrice??e.details?.exitPrice??trade.patternClose,quantity:trade.quantity,remainingQuantity:e.eventType==='TRADE_CLOSED'?0:trade.quantity,currentStopPrice:trade.stopPrice,details:JSON.stringify(e.details)}));
const adapted={...trade,strategy:'PATTERN_V2',entryAt:timestamp(trade.entryIndex,trade.entryDate),exitAt:trade.closed?timestamp(trade.exitIndex,trade.exitDate):null,exitIndex:trade.closed?trade.exitIndex:trade.markIndex,initialStopPrice:trade.stopPrice,finalStopPrice:trade.stopPrice,effectiveRisk:Number(trade.riskPerShare)*trade.quantity,returnPercent:trade.orderValue?Number(trade.closed?trade.netPnl:trade.netUnrealizedPnl)/Number(trade.orderValue)*100:null,takeProfits:[{targetNumber:1,price:trade.takeProfitPrice,quantity:trade.quantity}],events};
tradeChart||=TradingChart.create({host:$('stable-v2-chart'),tooltip:$('stable-v2-tooltip'),infoPanel:$('stable-v2-info'),timeline:$('stable-v2-timeline'),eventDetail:$('stable-v2-event-detail')});
tradeChart.update({simulation:{strategy:'PATTERN_V2',symbol:trade.symbol,priceSeries:bars,trades:[adapted]},selectedTradeId:TradingChart.formatTradeId(adapted),filters:{indicators:false},timeframe:'DAILY',autoFit:true});
}
$('stable-v2-pattern-info').textContent='Pattern completato '+trade.signalDate+'; candele del pattern evidenziate in viola. Entry all\'open '+trade.entryDate+'. Stop '+n(trade.stopPrice)+'; rischio '+n(trade.riskPerShare)+'; target '+n(trade.takeProfitPrice)+'. '+(trade.closed?'':'Posizione OPEN: mark '+trade.markDate+' a '+n(trade.markPrice)+'; nessun fill di uscita.');
const index=shownTrades.indexOf(trade);$('stable-v2-previous').disabled=index<=0;$('stable-v2-next').disabled=index<0||index>=shownTrades.length-1;
$('stable-v2-fit').disabled=!tradeChart;
$('stable-v2-chart-section').scrollIntoView({behavior:'smooth',block:'start'});
}
$('stable-v2-trade-symbol').onchange=renderTrades;$('stable-v2-event-filter').onchange=renderEvents;
$('stable-v2-fit').onclick=()=>tradeChart?.fitTrade();
for(const [id,delta] of [['previous',-1],['next',1]])$('stable-v2-'+id).onclick=()=>{const trade=shownTrades[shownTrades.indexOf(selectedTrade)+delta];if(trade)selectTrade(trade);};
function renderConfiguration(version){
const c=version.configuration,g=c.global,fees=g.commission||{};
$('stable-v2-config').textContent=JSON.stringify(version,null,2);
$('stable-v2-risk-percent').value=g.riskPercent;
const values={'strategy':'PATTERN_V2','start':g.startDate,'end':g.endDate,'capital':g.initialCapital,'max-order-value':g.maxOrderValueMode==='CURRENT_EQUITY_PERCENT'?null:g.maxOrderValue,'max-quantity':g.maxQuantity,'commission-model':fees.model,'commission-per-share':fees.perShare,'commission-minimum':fees.minimumPerOrder,'commission-maximum':fees.maximumPercentOrderValue,'commission':fees.fixedPerOrder,'slippage':g.slippagePercentPerSide,'adx-period':c.marketRegimeFilterAdxPeriod??14,'adx-threshold':c.marketRegimeFilterAdxThreshold??25,'adx-mode':c.marketRegimeFilterMode??'EXCLUDE_WITH_TREND'};
for(const [id,value] of Object.entries(values)){const field=$('stable-v2-'+id);field.value=value??'';field.disabled=true;}
$('stable-v2-max-order-value').placeholder=g.maxOrderValuePercent+'% equity';
$('stable-v2-adx-enabled').checked=c.marketRegimeFilterEnabled===true;
$('stable-v2-adx-enabled').disabled=true;
const rows=c.symbols.map(s=>({...s}));
table('stable-v2-mapping',[['Symbol',s=>s.symbol],['LONG Pattern',s=>s.longConfig.enabled?s.longConfig.pattern:'DISABLED'],['LONG Target R',s=>s.longConfig.targetR],['SHORT Pattern',s=>s.shortConfig.enabled?s.shortConfig.pattern:'DISABLED'],['SHORT Target R',s=>s.shortConfig.targetR],['Enabled',s=>s.enabled?'Si':'No']],rows);
$('stable-v2-rules').textContent=g.timeframe+' - '+g.entryMode+' - '+g.stopMode+' - buffer '+g.stopBufferTicks+' - '+g.sameBarPolicy+' - '+g.maxHoldingBars+' barre. Rischio '+g.riskPercent+'%; ordine '+g.maxOrderValuePercent+'% equity; leva '+(g.useLeverage?'ON':'OFF')+'. Market Regime Filter '+(c.marketRegimeFilterEnabled?'ON':'OFF')+'. Costi e split della run originale. Uscite: '+(c.exitFillPolicy||'LEVEL_ONLY')+'; gap oltre stop/target eseguiti all’apertura con OPEN_FIRST.';
}
async function histories(){
const results=await Promise.allSettled([['stable-v2-history',base+'/runs',false],['stable-v2-legacy','/api/backtest/pattern-v2/runs',true]].map(async([id,path,old])=>{
const rows=await api(path),select=$(id);select.replaceChildren(new Option('Seleziona',''));
for(const r of rows)select.add(new Option((r.timestamp||'')+' | '+(r.strategyVersion||r.configurationName||'Research')+' | rischio '+(r.riskPercent??'N/D')+'% | '+r.runId,r.runId));
select.onchange=()=>{if(select.value)return loadRun((old?'/api/backtest/pattern-v2':base)+'/runs/'+encodeURIComponent(select.value),'GET',old);};
}));
const errors=results.filter(r=>r.status==='rejected');$('stable-v2-history-status').textContent=errors.length?'Configurazione pronta. Run History non disponibile: '+errors.map(r=>r.reason.message).join('; '):'';
}
$('stable-v2-form').onsubmit=event=>event.preventDefault();
$('stable-v2-run').onclick=()=>{const riskPercent=Number($('stable-v2-risk-percent').value);if(!Number.isFinite(riskPercent)||riskPercent<=0||riskPercent>100){$('stable-v2-status').textContent='Risk Percent % deve essere maggiore di 0 e non superiore a 100';return;}return loadRun(base+'/run','POST',false,{riskPercent});};
resetResults();
action(async()=>{const version=await api(base+'/configuration');renderConfiguration(version);ready=true;$('stable-v2-status').textContent='Configurazione congelata pronta. Il backtest non trasmette ordini.';histories();});
})();
