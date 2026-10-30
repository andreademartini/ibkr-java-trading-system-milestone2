const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const metrics=require('../../main/resources/static/pattern-v1-metrics.js'),root='src/main/resources/static/';
test('V2 risk input sends only riskPercent and rejects invalid values before a request',async()=>{
 const h=harness();await new Promise(r=>setImmediate(r));h.get('stable-v2-risk-percent').value='8';await h.get('stable-v2-run').onclick();
 const sent=h.calls.find(c=>c.options.method==='POST');assert.deepEqual(JSON.parse(sent.options.body),{riskPercent:8});assert.equal(sent.options.headers['Content-Type'],'application/json');
 for(const value of ['',0,-1,101,'bad']){h.get('stable-v2-risk-percent').value=value;await h.get('stable-v2-run').onclick();}
 assert.equal(h.calls.filter(c=>c.options.method==='POST').length,1);
});
function harness(){
const html=fs.readFileSync(root+'pattern-v2.html','utf8'),elements=new Map();
function el(){return {value:'',children:[],textContent:'',scrollIntoView(){},replaceChildren(...x){this.children=x;},append(...x){this.children.push(...x);},add(x){this.children.push(x);}};}
for(const m of html.matchAll(/id="([^"]+)"/g))elements.set(m[1],el());
const get=id=>{assert.ok(elements.has(id),'HTML element missing: '+id);return elements.get(id);};
const version=JSON.parse(fs.readFileSync('src/main/resources/pattern-v2-stable/1.0.json','utf8'));
const p={summary:{},directionResults:{LONG:{trades:1,winRate:0.5}},symbolResults:[],trades:[{symbol:'AAPL',closed:true,realizedPnl:123,netPnl:123},{symbol:'NVDA',closed:false,realizedPnl:0,netUnrealizedPnl:12}],events:[{eventType:'SIGNAL',symbol:'AAPL',details:{source:'actual'}}],warnings:['Run warning'],startDate:'2010-01-01',endDate:'2026-09-08',actualStartDate:'2010-01-04',actualEndDate:'2026-09-08',equityCurve:[{date:'2010-01-04',equity:10000,drawdown:0},{date:'2010-01-05',equity:9000,drawdown:1000,drawdownPercent:10,cash:8000}]};
for(const [,key,source] of metrics.definitions)(source==='run'?p:p.summary)[key]=12.3456;
p.summary.winRate=0.4321;p.finalAvailableCash=null;
const run={runId:'test-run',configuration:version.configuration,portfolio:p,analysis:{periods:[],yearly:[],symbolResults:[]},costs:{commissions:50,slippage:25,transactionCosts:0}};
run.portfolioSummary={schemaVersion:'PORTFOLIO_SUMMARY/1',context:{'Run ID':run.runId,'Applied Configuration':run.configuration},metrics:require('../../main/resources/static/portfolio-summary-definitions.json').map(d=>({...d,value:(d.source==='run'?p:p.summary)[d.key]==null?null:(d.source==='run'?p:p.summary)[d.key]*(d.key==='winRate'?100:1)}))};
const charts=[],calls=[],details=[];let runResponse;
p.trades.forEach((t,i)=>Object.assign(t,{number:i+1,tradeId:'trade-'+i,strategy:'PATTERN_V2',pattern:'HAMMER',direction:'LONG',targetR:2,quantity:10,desiredQuantity:12,finalQuantity:10,quantityLimitingReasons:['ORDER_CAP'],sizeConstrained:true,signalIndex:0,patternStartIndex:0,entryIndex:1,exitIndex:i===0?2:null,markIndex:2,signalDate:'2020-01-01',entryDate:'2020-01-02',exitDate:i===0?'2020-01-03':null,markDate:'2020-01-03',entryPrice:100,stopPrice:98,takeProfitPrice:104,riskPerShare:2,orderValue:1000,markPrice:101,resultR:i===0?1.5:null,rawBarMfeR:3,rawBarMaeR:1.2}));
p.priceSeries=Object.fromEntries(['AAPL','NVDA'].map(symbol=>[symbol,[0,1,2].map(i=>({index:i,timestamp:'2020-01-0'+(i+1)+'T00:00:00Z',open:100,high:105,low:98,close:101}))]));
run.marketRegimeFilterAnalysis={summary:{}};run.marketRegimeAnalysis={};
const context={document:{getElementById:get,createElement:el},Option:function(text,value){return {text,value};},PatternV1Metrics:metrics,PatternV2Regime:{render(r){get('v2-regime-content').hidden=!r.marketRegimeAnalysis;}},PatternV2Filter:{render(){}},TradingChart:{formatTradeId(t){return t.tradeId;},create(){const detail={update(data){this.data=data;},fitTrade(){this.fitted=true;},destroy(){this.destroyed=true;}};details.push(detail);return detail;}},window:{LightweightCharts:true},LightweightCharts:{LineSeries:'line',HistogramSeries:'histogram',createChart(host,options){const chart={host,options,series:[],addSeries(type,opts){const s={type,opts,setData(d){this.data=d;}};this.series.push(s);return s;},timeScale(){return {fitContent(){}};},subscribeCrosshairMove(fn){this.crosshair=fn;},remove(){this.removed=true;}};charts.push(chart);return chart;}},fetch:async(url,options)=>{calls.push({url,options});if(url.endsWith('/run')||/\/runs\/[^/]+$/.test(url))return runResponse?runResponse(url,options):{ok:true,json:async()=>run};return {ok:true,json:async()=>url.endsWith('/configuration')?version:[{runId:'saved-run'}]};}};
vm.runInNewContext(fs.readFileSync(root+'portfolio-summary.js','utf8'),context);
vm.runInNewContext(fs.readFileSync(root+'pattern-v2-stable.js','utf8'),context);
return {get,run,charts,calls,details,setRunResponse(fn){runResponse=fn;},async flush(){await new Promise(r=>setImmediate(r));},async load(){await new Promise(r=>setImmediate(r));await get('stable-v2-run').onclick();}};
}
test('V2 uses the shared forty-card summary with numeric Italian formatting',async()=>{
const h=harness();await h.load();const cards=h.get('stable-v2-metrics').children.find(c=>c.className==='portfolio-summary-grid').children;
assert.deepEqual(cards.map(c=>c.children[0].textContent),metrics.definitions.slice(0,40).map(d=>d[0]));assert.equal(cards.length,40);
function card(key){return cards[metrics.definitions.findIndex(d=>d[1]===key)];}
assert.equal(card('winRate').children[1].textContent,'43,210%');assert.equal(card('cagr').children[1].textContent,'12,346%');assert.equal(card('finalAvailableCash').children[1].textContent,'N/A');
assert.equal(h.calls.filter(c=>c.options.method==='POST').length,1);
});

test('V2 uses V1 equity and monetary drawdown chart and retains its run values',async()=>{
const h=harness();await h.load();assert.equal(h.charts.length,1);const chart=h.charts[0];assert.equal(chart.options.height,360);
assert.equal(chart.series[0].opts.color,'#0f766e');assert.equal(chart.series[1].type,'histogram');assert.equal(chart.series[1].opts.priceScaleId,'left');
assert.equal(chart.series[1].data[1].value,-1000);assert.equal(chart.series[0].data[1].value,9000);
chart.crosshair({time:'2010-01-05'});assert.match(h.get('stable-v2-equity-point').textContent,/DD 1000.*10%/);
});
test('V2 has independent symbol and event selectors and preserves warnings and export',async()=>{
const h=harness();await h.load();h.get('stable-v2-trade-symbol').value='NVDA';h.get('stable-v2-trade-symbol').onchange();
const table=h.get('stable-v2-trades').children[0];assert.equal(table.children.length,2);assert.equal(table.children[1].children[1].textContent,'NVDA');
h.get('stable-v2-event-filter').value='NOT_PRESENT';h.get('stable-v2-event-filter').onchange();assert.equal(h.get('stable-v2-events').children[0].children.length,1);
assert.equal(h.get('stable-v2-warnings').children[0].textContent,'Run warning');assert.match(h.get('stable-v2-export').href,/pattern-v2-stable\/runs\/test-run\/export\/excel/);
});


test('V2 starts with results and both ADX analyses hidden and never automatically runs a simulation',async()=>{
const h=harness();await h.flush();for(const id of ['stable-v2-output','stable-v2-results','market-regime-filter-analysis','market-regime-analysis','stable-v2-chart-section','stable-v2-export'])assert.equal(h.get(id).hidden,true,id);
assert.equal(h.calls.some(c=>c.options.method==='POST'),false);
const html=fs.readFileSync(root+'pattern-v2.html','utf8');assert.match(html,/<div id="stable-v2-output" hidden>/);assert.match(html,/<section id="market-regime-filter-analysis"[^>]+hidden>/);assert.match(html,/<section id="market-regime-analysis"[^>]+hidden>/);
});
test('Starting another run immediately hides old output and an error cannot expose stale results',async()=>{
const h=harness();await h.load();assert.equal(h.get('stable-v2-output').hidden,false);
h.get('stable-v2-trades').children[0].children[1].onclick();assert.equal(h.get('stable-v2-chart-section').hidden,false);
let finish;h.setRunResponse(()=>new Promise(resolve=>{finish=resolve;}));const pending=h.get('stable-v2-run').onclick();
assert.equal(h.get('stable-v2-output').hidden,true);assert.equal(h.get('stable-v2-export').hidden,true);assert.equal(h.details[0].destroyed,true);assert.equal(h.charts[0].removed,true);
finish({ok:false,status:500,json:async()=>({message:'Simulazione fallita'})});await pending;
assert.equal(h.get('stable-v2-output').hidden,true);assert.equal(h.get('stable-v2-run').disabled,false);assert.equal(h.get('stable-v2-status').textContent,'Simulazione fallita');
});
test('Selecting a historical run shows its own snapshot without POST and handles missing analyses',async()=>{
const h=harness();await h.flush();const legacy=JSON.parse(JSON.stringify(h.run));legacy.runId='legacy-selected';legacy.configuration.marketRegimeFilterEnabled=false;delete legacy.marketRegimeFilterAnalysis;delete legacy.marketRegimeAnalysis;
h.setRunResponse(()=>({ok:true,json:async()=>legacy}));h.get('stable-v2-legacy').value='saved-run';await h.get('stable-v2-legacy').onchange();
assert.equal(h.get('stable-v2-output').hidden,false);assert.equal(h.get('stable-v2-filter-content').hidden,true);assert.equal(h.get('v2-regime-content').hidden,true);assert.match(h.get('v2-regime-filter-status').textContent,/non disponibile.*OFF/);
assert.match(h.get('stable-v2-used').textContent,/STORICO RESEARCH.*OFF/);assert.match(h.get('stable-v2-export').href,/pattern-v2\/runs\/legacy-selected/);assert.equal(h.calls.some(c=>c.options.method==='POST'),false);
assert.equal(h.get('stable-v2-comparison').hidden,true);assert.match(h.get('stable-v2-comparison-note').textContent,/non disponibile/);
});
test('V2 includes the V1 table columns in the same order and preserves extra net PF columns',async()=>{
const h=harness();await h.load();const source=fs.readFileSync(root+'pattern-v1-stable.js','utf8');
for(const section of ['directions','symbols','trades']){
const match=source.match(new RegExp('"v1-'+section+'"\\)\\.innerHTML = table\\((\\[[^\\]]+\\])'));
assert.ok(match,'V1 column source '+section);const expected=JSON.parse(match[1]);const actual=h.get('stable-v2-'+section).children[0].children[0].children.map(c=>c.textContent);assert.deepEqual(actual.slice(0,expected.length),expected,section);
}
});
test('Trade selection, keyboard, navigation and centering use actual V2 prices and preserve OPEN trades',async()=>{
const h=harness();await h.load();const rows=h.get('stable-v2-trades').children[0].children;
assert.equal(rows[1].tabIndex,0);rows[1].onkeydown({key:'Enter'});assert.equal(h.get('stable-v2-chart-section').hidden,false);
assert.equal(h.details[0].data.simulation.strategy,'PATTERN_V2');assert.equal(h.details[0].data.simulation.trades[0].strategy,'PATTERN_V2');assert.equal(h.details[0].data.simulation.trades[0].exitAt,'2020-01-03T00:00:00Z');assert.equal(h.details[0].data.simulation.priceSeries,h.run.portfolio.priceSeries.AAPL);
assert.equal(h.get('stable-v2-previous').disabled,true);h.get('stable-v2-next').onclick();assert.equal(h.details[0].data.simulation.symbol,'NVDA');assert.equal(h.details[0].data.simulation.trades[0].exitAt,null);assert.match(h.get('stable-v2-pattern-info').textContent,/OPEN.*nessun fill/);
h.get('stable-v2-fit').onclick();assert.equal(h.details[0].fitted,true);assert.equal(h.get('stable-v2-next').disabled,true);
h.get('stable-v2-previous').onclick();assert.equal(h.details[0].data.simulation.symbol,'AAPL');
h.get('stable-v2-trade-symbol').value='NVDA';h.get('stable-v2-trade-symbol').onchange();assert.equal(h.get('stable-v2-chart-section').hidden,true);assert.equal(h.details[0].destroyed,true);
});
test('Missing historical prices display an honest message instead of fabricated chart data',async()=>{
const h=harness();delete h.run.portfolio.priceSeries;await h.load();h.get('stable-v2-trades').children[0].children[1].onclick();assert.equal(h.details.length,0);assert.match(h.get('stable-v2-info').textContent,/Dati prezzi non disponibili/);assert.equal(h.get('stable-v2-fit').disabled,true);
});
test('A late history response cannot replace the most recently selected run',async()=>{
const h=harness();await h.flush();const pending=[];h.setRunResponse(()=>new Promise(resolve=>pending.push(resolve)));
h.get('stable-v2-history').value='first';const first=h.get('stable-v2-history').onchange();h.get('stable-v2-history').value='second';const second=h.get('stable-v2-history').onchange();
const recent=JSON.parse(JSON.stringify(h.run));recent.runId='second';pending[1]({ok:true,json:async()=>recent});await second;
pending[0]({ok:true,json:async()=>h.run});await first;assert.match(h.get('stable-v2-status').textContent,/second/);assert.equal(h.get('stable-v2-output').hidden,false);
});
