const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const m=require('../../main/resources/static/pattern-v3-model.js');
test('Signal timeframe defaults DAILY and keeps execution DAILY',()=>{
 const c={global:{timeframe:'DAILY'}};assert.equal(m.normalizeSignalTimeframe(c).signalTimeframe,'DAILY');assert.match(m.timeframeDescription(c),/Signals: DAILY.*Execution: DAILY.*Management: DAILY/);
 c.signalTimeframe='WEEKLY';assert.equal(m.validateSignalTimeframe(c).signalTimeframe,'WEEKLY');assert.match(m.timeframeDescription(c),/Signals: WEEKLY/);
 for(const value of ['MONTHLY','HOURLY','invalid']){c.signalTimeframe=value;assert.throws(()=>m.validateSignalTimeframe(c));}
 c.signalTimeframe='WEEKLY';c.global.timeframe='HOURLY';assert.throws(()=>m.validateSignalTimeframe(c),/DAILY/);
});
test('Timeframe control round trip preserves WEEKLY and every economic parameter',()=>{
 const source=fs.readFileSync('src/main/resources/static/pattern-v3.js','utf8');
 const config=JSON.parse(fs.readFileSync('src/main/resources/pattern-research/v3-initial.json','utf8'));
 const values={'v3-range-maximum':'','v3-close-rule':'LEGACY_MAX_LE','v3-rvol-period':'20','v3-rvol-minimum':'1.2','v3-rvol-maximum':'','v3-rvol-basis':'UNVERIFIED','v3-signal-timeframe':'WEEKLY','v3-name':config.name,'v3-regime-filter-period':'14','v3-regime-filter-threshold':'25','v3-bollinger-period':'20','v3-bollinger-stddev':'2','v3-atr-period':'14','v3-bollinger-distance':'.5','v3-close-maximum':'.8','v3-range-minimum':'2.5'};
 const elements=Object.fromEntries(Object.entries(values).map(([key,value])=>[key,{value}]));
 for(const id of ['rvol','regime-filter','regime','bollinger','shadow','close','range'])elements['v3-'+id+'-enabled']={checked:false};
 for(const id of ['global','capital-limits','symbol-rows'])elements['v3-'+id]={querySelectorAll:()=>[]};
 if(config.splitCosts)elements['v3-split-rows']={querySelectorAll:()=>[]};
 const ctx={config,M:m,$:id=>elements[id]};vm.runInNewContext(source.slice(source.indexOf('function collect('),source.indexOf('function updatePreview('))+'result=collect(false);',ctx);
 assert.equal(ctx.result.signalTimeframe,'WEEKLY');assert.deepEqual(ctx.result.global,config.global);assert.deepEqual(ctx.result.symbols,config.symbols);
 const html=fs.readFileSync('src/main/resources/static/pattern-v3.html','utf8');assert.match(html,/id="v3-signal-timeframe"/);assert.match(html,/value="WEEKLY"/);assert.match(source,/Signal Timeframe/);assert.doesNotMatch(source,/\["DAILY","HOURLY","MINUTES_5"\]/);
});
