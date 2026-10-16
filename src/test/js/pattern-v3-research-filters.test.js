const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const m=require('../../main/resources/static/pattern-v3-model.js');
const config=()=>JSON.parse(fs.readFileSync('src/main/resources/pattern-research/v3-initial.json','utf8'));
test('New filter submissions cannot be silently ignored by an outdated backend',async()=>{
 const source=fs.readFileSync('src/main/resources/static/pattern-v3.js','utf8'),code=source.slice(source.indexOf('async function api('),source.indexOf('function get('));let updated=false;const calls=[];
 const ctx={fetch:async(url,options)=>{calls.push({url,options});return url.endsWith('/research-filter-capabilities')?{ok:true,json:async()=>updated?{filterModelVersion:'V3_RESEARCH_FILTERS/1'}:{}}:{ok:true,json:async()=>({saved:true})};}};vm.runInNewContext(code,ctx);
 await assert.rejects(ctx.api('/run','POST',{closeStrengthMin:.906}),/riavvia il backend/);assert.equal(calls.filter(c=>c.options.method==='POST').length,0);
 updated=true;await ctx.api('/run','POST',{closeStrengthMin:.906});assert.deepEqual(JSON.parse(calls.at(-1).options.body),{closeStrengthMin:.906});
});
test('Research presets preserve every unrelated setting and never start a run',()=>{
 const base=config();base.marketRegimeFilterEnabled=true;base.signalTimeframe='WEEKLY';base.relativeVolumePeriod=31;base.relativeVolumeMin=1.75;
 const fields=['closeStrengthFilterEnabled','closeStrengthMin','patternRangeFilterEnabled','minimumPatternRangePct','patternRangeMinPct','patternRangeMaxPct','relativeVolumeFilterEnabled'];
 for(const preset of ['BASELINE','A','B']){const c=m.researchPreset(base,preset),a=m.clone(c),b=m.clone(base);for(const key of fields){delete a[key];delete b[key];}assert.deepEqual(a,b);assert.equal(c.relativeVolumeFilterEnabled,false);assert.equal(c.closeStrengthFilterEnabled,preset!=='BASELINE');if(preset!=='BASELINE'){assert.equal(c.closeStrengthMin,.906);assert.equal(c.patternRangeMinPct,preset==='A'?1.38:3.63);assert.equal(c.patternRangeMaxPct,null);}assert.equal(c.relativeVolumePeriod,31);}
 assert.equal(base.closeStrengthMin,undefined);assert.throws(()=>m.researchPreset(base,'BEST_CAGR'));
 const js=fs.readFileSync('src/main/resources/static/pattern-v3.js','utf8');const handler=js.slice(js.indexOf("document.querySelectorAll('[data-research-preset]')"),js.indexOf("$('v3-close-rule').addEventListener"));assert.doesNotMatch(handler,/api\(|fetch\(/);
});
test('Research parameter validation includes optional max and strict numeric units',()=>{
 const c=m.researchPreset(config(),'A');m.validateResearchFilters(c);assert.equal(c.relativeVolumeFilterEnabled,false);assert.equal(c.relativeVolumePeriod,20);assert.equal(c.relativeVolumeMin,1.2);assert.equal(c.relativeVolumeBasis,'UNVERIFIED');
 for(const [key,value] of [['closeStrengthMin',1.001],['closeStrengthMin',NaN],['patternRangeMinPct',-1],['patternRangeMaxPct',1],['relativeVolumePeriod',0],['relativeVolumePeriod',1.5],['relativeVolumeMin',-1],['relativeVolumeMax',1],['relativeVolumeBasis','AUTO']]){const copy=m.clone(c);copy[key]=value;assert.throws(()=>m.validateResearchFilters(copy),key);}
 const legacy=config();delete legacy.closeStrengthMin;m.validateResearchFilters(legacy);assert.equal(legacy.maxCloseStrength,.8);assert.equal(legacy.closeStrengthMin,undefined);
});
test('Actual form collection sends minimum CloseStrength, optional bounds and OFF volume values',()=>{
 const source=fs.readFileSync('src/main/resources/static/pattern-v3.js','utf8'),c=m.researchPreset(config(),'A');
 const values={'v3-signal-timeframe':'WEEKLY','v3-name':c.name,'v3-regime-filter-period':'14','v3-regime-filter-threshold':'25','v3-bollinger-period':'20','v3-bollinger-stddev':'2','v3-atr-period':'14','v3-bollinger-distance':'.5','v3-close-maximum':'.906','v3-close-rule':'MIN_GE','v3-range-minimum':'1.38','v3-range-maximum':'','v3-rvol-period':'20','v3-rvol-minimum':'1.20','v3-rvol-maximum':'','v3-rvol-basis':'UNVERIFIED'};
 const elements=Object.fromEntries(Object.entries(values).map(([k,value])=>[k,{value}]));for(const id of ['regime-filter','regime','bollinger','shadow','close','range','rvol'])elements['v3-'+id+'-enabled']={checked:['close','range'].includes(id)};for(const id of ['global','capital-limits','symbol-rows','split-rows'])elements['v3-'+id]={querySelectorAll:()=>[]};
 const ctx={config:c,M:m,$:id=>elements[id]};vm.runInNewContext(source.slice(source.indexOf('function collect('),source.indexOf('function updatePreview('))+'draft=collect(false);',ctx);assert.equal(ctx.draft.closeStrengthMin,.906);assert.equal(ctx.draft.maxCloseStrength,.8);assert.equal(ctx.draft.patternRangeMinPct,1.38);assert.equal(ctx.draft.patternRangeMaxPct,null);assert.equal(ctx.draft.relativeVolumeFilterEnabled,false);assert.equal(ctx.draft.relativeVolumePeriod,20);assert.equal(ctx.draft.relativeVolumeMin,1.2);assert.deepEqual(ctx.draft.global,c.global);
 const html=fs.readFileSync('src/main/resources/static/pattern-v3.html','utf8');for(const id of ['v3-close-enabled','v3-close-maximum','v3-range-enabled','v3-range-minimum','v3-rvol-enabled','v3-rvol-period'])assert.equal([...html.matchAll(new RegExp('id="'+id+'"','g'))].length,1,id);
});
