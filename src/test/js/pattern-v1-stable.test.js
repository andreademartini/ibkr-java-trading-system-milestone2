const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

test('Stable V1 keeps reference values read-only and sends only the chosen risk',async()=>{
    const elements=new Map();const document={getElementById(id){if(!elements.has(id))elements.set(id,{value:'',disabled:false,handlers:{},addEventListener(event,handler){this.handlers[event]=handler;},querySelector(){return {};}});return elements.get(id);}};
    const configuration=JSON.parse(fs.readFileSync('src/main/resources/pattern-stable/2.1.json','utf8'));let sent;
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/pattern-v1-stable.js','utf8'),{
        document,URLSearchParams,location:{search:''},async fetch(url,options){if(options)sent={url,body:JSON.parse(options.body)};
            return {ok:true,async json(){return options?{schemaVersion:1,settings:{maxQuantity:1000000}}:url.endsWith('/runs')?[]:{configuration,strategyVersion:'2.1'};}};
        }
    });
    await new Promise(resolve=>setImmediate(resolve));
    for(const id of ['v1-start','v1-end','v1-capital','v1-max-order-value','v1-max-quantity','v1-commission-model','v1-slippage'])assert.equal(document.getElementById(id).disabled,true,id);
    assert.equal(document.getElementById('v1-slippage').value,0.03);assert.equal(document.getElementById('v1-capital').value,10000);
    document.getElementById('v1-risk-percent').value='8';assert.equal(document.getElementById('v1-risk-percent').disabled,false);document.getElementById('v1-capital').value=999999;await document.getElementById('v1-form').handlers.submit({preventDefault(){}});
    assert.equal(sent.url,'/api/backtest/pattern-v1/run');assert.deepEqual(sent.body,{riskPercent:8});
});

test('Stable and legacy pages have explicit independent routes; V2 is stable and V3 is experimental',()=>{
    const html=fs.readFileSync('src/main/resources/static/pattern-v1.html','utf8');
    assert.match(html,/STABLE \/ FROZEN \/ BACKTEST/);assert.match(html,/pattern-v1-stable.js/);assert.match(html,/pattern-v1-legacy.html/);
    assert.match(fs.readFileSync('src/main/resources/static/pattern-v1.js','utf8'),/\/api\/backtest\/pattern-v1\/legacy/);
    assert.match(fs.readFileSync('src/main/resources/static/pattern-v2.html','utf8'),/STABLE \/ FROZEN \/ BACKTEST/);assert.match(fs.readFileSync('src/main/resources/static/pattern-v3.html','utf8'),/RESEARCH \/ EXPERIMENTAL/);
});

test('Stable results render equity and trades when the legacy Research comparison is absent',async()=>{
    const elements=new Map();const document={getElementById(id){if(!elements.has(id))elements.set(id,{value:'',handlers:{},addEventListener(e,h){this.handlers[e]=h;},querySelector(){return {};},querySelectorAll(){return [];},insertAdjacentHTML(){}});return elements.get(id);}};
    const configuration=JSON.parse(fs.readFileSync('src/main/resources/pattern-stable/2.1.json','utf8'));
    const result={backtestId:'stable',schemaVersion:2,strategy:'PATTERN_V1',settings:{commission:configuration.global.commission,slippagePercent:0.03,maxQuantity:1000000},summary:{trades:1},directionResults:{},symbolResults:[{symbol:'AAPL',direction:'LONG',pattern:'BULLISH_ENGULFING',targetR:3,metrics:{},researchBaseline:null,warnings:[]}],priceSeries:{},events:[],warnings:[],trades:[],equityCurve:[{date:'2020-01-01',equity:10000,drawdown:0}]};
    let data=[];
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/pattern-v1-stable.js','utf8'),{
        document,URLSearchParams,location:{search:''},history:{replaceState(){}},PatternV1Metrics:require('../../main/resources/static/pattern-v1-metrics.js'),
        LightweightCharts:{LineSeries:'line',HistogramSeries:'histogram',createChart(){return {addSeries(){return {setData(d){data.push(d);}};},timeScale(){return {fitContent(){}};},subscribeCrosshairMove(){}};}},
        async fetch(url,options){return {ok:true,async json(){return options?result:url.endsWith('/runs')?[]:{configuration};}};}
    });
    await new Promise(resolve=>setImmediate(resolve));await document.getElementById('v1-form').handlers.submit({preventDefault(){}});
    assert.equal(document.getElementById('v1-results').hidden,false);assert.equal(document.getElementById('v1-excel').href,'/api/backtest/pattern-v1/stable/export/excel');
    assert.equal(data.length,2);assert.equal(data[0][0].value,10000);assert.match(document.getElementById('v1-status').textContent,/1 trade/);
});
