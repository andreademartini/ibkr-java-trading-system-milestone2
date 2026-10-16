const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const root='src/main/resources/static/';
function harness(v,mode){
const prefix=v===1?'v1':'stable-v2',page=fs.readFileSync(root+'pattern-v'+v+'.html','utf8');
const elements=new Map([...page.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],{value:'',disabled:false,children:[],handlers:{},addEventListener(e,h){this.handlers[e]=h;},querySelector(){return {};},replaceChildren(...v){this.children=v;},append(...v){this.children.push(...v);},add(v){this.children.push(v);}}]));
const get=id=>{assert.ok(elements.has(id),'Missing HTML element '+id);return elements.get(id);};
const config=v===1?{strategyVersion:'2.1',configuration:JSON.parse(fs.readFileSync('src/main/resources/pattern-stable/2.1.json','utf8'))}:JSON.parse(fs.readFileSync('src/main/resources/pattern-v2-stable/1.0.json','utf8'));
const calls=[];
vm.runInNewContext(fs.readFileSync(root+'pattern-v'+v+'-stable.js','utf8'),{
document:{getElementById:get,createElement(){return {append(){},textContent:''};}},URLSearchParams,location:{search:''},Option:function(){},
AbortSignal:{timeout(ms){assert.equal(ms,15000);return {aborted:mode==='timeout'};}},
async fetch(url,options){calls.push({url,options});if(mode==='timeout')throw Error('aborted');if(mode==='unavailable')throw Error('Failed to fetch');if(url.endsWith('/configuration'))return {ok:true,json:async()=>config};return new Promise(()=>{});}
});
return {get,prefix,calls};
}
for(const v of [1,2]){
test('V'+v+' enables backtest after configuration even while Run History is pending',async()=>{
const h=harness(v,'history-pending');await new Promise(r=>setImmediate(r));assert.equal(h.get(h.prefix+'-run').disabled,false);assert.doesNotMatch(h.get(h.prefix+'-status').textContent,/Caricamento/);assert.ok(h.calls.every(c=>!c.options?.method||c.options.method==='GET'));assert.ok(h.calls.every(c=>c.options.signal));
});
for(const mode of ['timeout','unavailable'])test('V'+v+' reports '+mode+' and keeps backtest disabled',async()=>{
const h=harness(v,mode);await new Promise(r=>setImmediate(r));assert.equal(h.get(h.prefix+'-run').disabled,true);assert.match(h.get(h.prefix+'-status').textContent,mode==='timeout'?/Timeout/:/Backend non raggiungibile/);
});
}
test('Both stable pages preserve hidden Excel controls and report missing scripts',()=>{
const css=fs.readFileSync(root+'pattern-v1.css','utf8');assert.match(css,/\.research-shell \[hidden\] \{ display: none !important; \}/);
for(const v of [1,2]){const html=fs.readFileSync(root+'pattern-v'+v+'.html','utf8');assert.match(html,/onerror="[^\n]+Impossibile caricare lo script/);assert.match(html,/<noscript>/);}
});
