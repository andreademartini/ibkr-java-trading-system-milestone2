const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const m=require('../../main/resources/static/pattern-v3-model.js');
test('Legacy defaults OFF and independent analysis flag',()=>{const c={marketRegimeAnalysisEnabled:false,global:{timeframe:'DAILY'}};m.validateMarketRegimeFilter(c);assert.equal(c.marketRegimeFilterEnabled,false);assert.equal(c.marketRegimeFilterAdxPeriod,14);assert.equal(c.marketRegimeFilterAdxThreshold,25);c.marketRegimeFilterEnabled=true;m.validateMarketRegimeFilter(c);assert.equal(c.marketRegimeAnalysisEnabled,false);});
test('Strict period threshold mode and null validation',()=>{for(const [key,values] of Object.entries({marketRegimeFilterAdxPeriod:[null,'14',0,1,14.5,NaN,Infinity],marketRegimeFilterAdxThreshold:[null,'25',-1,101,NaN,Infinity],marketRegimeFilterEnabled:[null,'true'],marketRegimeFilterMode:[null,'EXCLUDE_COUNTER_TREND']}))for(const value of values)assert.throws(()=>m.validateMarketRegimeFilter({global:{timeframe:'DAILY'},[key]:value}));for(const v of [0,24.99,25,25.01,100])m.validateMarketRegimeFilter({global:{timeframe:'DAILY'},marketRegimeFilterAdxThreshold:v});});
test('Checkbox fields collection and selected run audit are present',()=>{const html=fs.readFileSync('src/main/resources/static/pattern-v3.html','utf8'),js=fs.readFileSync('src/main/resources/static/pattern-v3.js','utf8'),renderer=fs.readFileSync('src/main/resources/static/pattern-v3-filter.js','utf8');assert.match(html,/id="v3-regime-filter-enabled" type="checkbox"/);for(const id of ['period','threshold'])assert.match(html,new RegExp('id="v3-regime-filter-'+id+'"'));assert.match(js,/draft.marketRegimeFilterEnabled=\$\("v3-regime-filter-enabled"\).checked/);assert.match(js,/PatternV3Filter.render\(run\)/);assert.match(js,/r.marketRegimeFilterEnabled\?"ON":"OFF"/);assert.doesNotMatch(renderer,/fetch\(|innerHTML/);assert.match(renderer,/signalsEvaluated/);assert.match(renderer,/rejectedInsufficientData/);});
test('Selected run renders rejections and clears them when a legacy run is reopened',()=>{
 const renderer=require('../../main/resources/static/pattern-v3-filter.js');const elements={};
 const element=()=>({children:[],textContent:'',append(node){this.children.push(node);},replaceChildren(){this.children=[];}});
 global.document={createElement:element,getElementById:id=>elements[id]??=(element())};
 try {renderer.render({runId:'filtered',configuration:{marketRegimeFilterEnabled:true,marketRegimeFilterAdxPeriod:7,marketRegimeFilterAdxThreshold:30},marketRegimeFilterAnalysis:{summary:{rejectedWithTrend:1,rejectedInsufficientData:0},signals:[{symbol:'<script>',accepted:false,rejectionReason:'MARKET_REGIME_WITH_TREND',indicators:{adx14:32,plusDI14:28,minusDI14:12}}]}});
 assert.match(elements['v3-regime-filter-status'].textContent,/ON.*Period 7.*Threshold 30/);assert.equal(elements['v3-regime-filter-rejections'].children[0].children.length,2);
 assert.equal(elements['v3-regime-filter-rejections'].children[0].children[1].children[1].textContent,'<script>');
 renderer.render({runId:'legacy',configuration:{}});assert.match(elements['v3-regime-filter-status'].textContent,/OFF.*Legacy/);assert.equal(elements['v3-regime-filter-rejections'].children[0].children.length,1);
 } finally {delete global.document;}
});
test('Form collects ON period and precise threshold independently of analysis',()=>{
 const vm=require('node:vm'),source=fs.readFileSync('src/main/resources/static/pattern-v3.js','utf8');
 const config=JSON.parse(fs.readFileSync('src/main/resources/pattern-stable/2.1.json','utf8'));
 const elements={};for(const [id,value] of Object.entries({'v3-regime-filter-period':'7','v3-regime-filter-threshold':'24.99','v3-bollinger-period':'20','v3-bollinger-stddev':'2','v3-atr-period':'14','v3-bollinger-distance':'.5','v3-name':'FILTER_TEST','v3-close-maximum':'.8','v3-range-minimum':'2.5'}))elements[id]={value};
 for(const id of ['v3-regime-filter-enabled','v3-regime-enabled','v3-bollinger-enabled','v3-shadow-enabled','v3-close-enabled','v3-range-enabled'])elements[id]={checked:id==='v3-regime-filter-enabled'};
 for(const id of ['v3-capital-limits','v3-global','v3-symbol-rows','v3-split-rows'])elements[id]={querySelectorAll:()=>[]};
 const patterns={LONG:['BULLISH_ENGULFING','BULLISH_HARAMI','HAMMER','PIERCING_PATTERN'],SHORT:['SHOOTING_STAR']};const ctx={config,M:m,patterns,$:id=>elements[id],set:()=>{}};
 vm.runInNewContext(source.slice(source.indexOf('function collect('),source.indexOf('function updatePreview('))+'result=collect();',ctx);
 const saved=JSON.parse(JSON.stringify(ctx.result));assert.equal(saved.marketRegimeFilterEnabled,true);assert.equal(saved.marketRegimeFilterAdxPeriod,7);assert.equal(saved.marketRegimeFilterAdxThreshold,24.99);assert.equal(saved.marketRegimeAnalysisEnabled,false);assert.equal(config.marketRegimeFilterEnabled,undefined);
 elements['v3-regime-filter-threshold'].value='';assert.throws(()=>vm.runInNewContext('collect();',ctx),/ADX Threshold/);
});
