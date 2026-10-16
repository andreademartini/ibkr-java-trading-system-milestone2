const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto');
test('Main menu exposes PAPER first and V1/V2 backtest plus independent V3 research',()=>{for(const file of ['index.html','pattern-v1.html','pattern-v2.html','pattern-v3.html','paper-trading.html']){const html=fs.readFileSync('src/main/resources/static/'+file,'utf8');for(const path of ['/paper-trading.html','/pattern-v1.html','/pattern-v2.html','/pattern-v3.html'])assert.match(html,new RegExp('href="'+path+'"'));assert.ok(html.indexOf('href="/paper-trading.html"')<html.indexOf('href="/pattern-v1.html"'));}for(const file of ['pattern-v1.html','pattern-v2.html']){const html=fs.readFileSync('src/main/resources/static/'+file,'utf8');assert.doesNotMatch(html,/id="paper-tab-paper"|id="paper-panel"/);assert.match(html,/STABLE \/ FROZEN \/ BACKTEST/);}assert.match(fs.readFileSync('src/main/resources/static/pattern-v3.html','utf8'),/RESEARCH \/ EXPERIMENTAL/);});
test('Frozen V2 submits only the risk parameter and preserves old research history link',()=>{const html=fs.readFileSync('src/main/resources/static/pattern-v2.html','utf8'),js=fs.readFileSync('src/main/resources/static/pattern-v2-stable.js','utf8');assert.doesNotMatch(html,/id="v2-form"|id="v2-global"|Enable Market Regime Filter/);assert.match(html,/Run History V2 Research storica/);assert.ok(js.includes("loadRun(base+'/run','POST',false,{riskPercent})"));assert.doesNotMatch(js,/method:'PUT'|\/configuration.*POST/);});
function harness({localAccess=false,adminConfigured=true,denyLocal=false}={}){const elements=new Map(),calls=[];function element(tag='div'){return {tag,children:[],value:'',dataset:{},hidden:false,disabled:false,textContent:'',append(...items){this.children.push(...items);},replaceChildren(...items){this.children=items;},elements:[]};}function get(id){if(!elements.has(id))elements.set(id,element());return elements.get(id);}const d={globalKill:false,globalReason:'',simultaneousCertified:false,strategies:['PATTERN_V1','PATTERN_V2'].map((strategyId,i)=>({strategyId,strategyVersion:i?'1.0':'2.1',configurationHash:'hash-'+i,configurationSnapshot:{marketRegimeFilterEnabled:!!i,marketRegimeFilterAdxPeriod:14,marketRegimeFilterAdxThreshold:25,marketRegimeFilterMode:'EXCLUDE_WITH_TREND'},dashboard:{session:{mode:'DISABLED',reason:'Not activated',initialCapital:10000,executions:[],orders:[],trades:[],signals:[],limits:{}},connection:{connected:false,account:'DU_TEST',paperIdentityVerified:false},capital:{equity:10000,realized:0,unrealized:0},performance:{openPositions:0,maximumDrawdownPercent:0},checks:[{name:'PAPER_IDENTITY',passed:false,reason:'Unverified'}],events:[]}})),reconciliation:{complete:false,time:null,issues:['No snapshot'],positions:[]},comparison:{available:false,reason:'No comparable PAPER observations',metrics:[],curves:{}},accountEvents:[]};let confirmation=null;const context={document:{getElementById:get,createElement:element,querySelector(){return null;}},window:{},crypto,Date,Map,URLSearchParams,setInterval(){return 1;},clearInterval(){},prompt(){return confirmation;},async fetch(url,options){calls.push({url,options,body:options.body?JSON.parse(options.body):null});if(url.endsWith('/logout'))return {ok:true,status:200,json:async()=>{throw Error('Logout has an empty response body');}};if(denyLocal&&url.endsWith('/local-session'))return {ok:false,status:401,json:async()=>({message:'Local access denied'})};return {ok:true,status:200,async json(){return url.endsWith('/access')?{adminConfigured,localAccess}:(url.endsWith('/login')||url.endsWith('/local-session'))?{csrf:'csrf-test'}:d;}};}};vm.runInNewContext(fs.readFileSync('src/main/resources/static/paper-trading.js','utf8'),context);return {get,calls,data:d,setConfirmation(value){confirmation=value;},async flush(){await new Promise(r=>setImmediate(r));},async login(){get('desk-token').value='test-secret';await get('desk-login').onsubmit({preventDefault(){}});await this.flush();}};}
function walk(node){return [node,...node.children.flatMap(walk)];}
test('Opening and logging into central PAPER never starts or connects either strategy',async()=>{const h=harness();await h.flush();assert.equal(h.calls.length,1);await h.login();assert.equal(h.get('desk-token').value,'');assert.equal(h.get('strategies').children.length,2);assert.equal(h.calls.filter(c=>/\/start|\/connect/.test(c.url)).length,0);assert.match(h.get('desk-safety').textContent,/simultaneo DISABILITATO/);for(const card of h.get('strategies').children){const start=walk(card).find(n=>n.dataset.operation==='start');assert.equal(start.disabled,true);}});
test('Separate strategy command has CSRF and command ID and cannot send account or LIVE override',async()=>{const h=harness();await h.login();const stop=walk(h.get('strategies').children[1]).find(n=>n.dataset.operation==='stop');stop.onclick();await h.flush();const c=h.calls.find(c=>c.url.endsWith('/PATTERN_V2/stop'));assert.equal(c.options.headers['X-Paper-CSRF'],'csrf-test');assert.deepEqual(Object.keys(c.body),['commandId']);assert.match(c.body.commandId,/^[a-f0-9-]{36}$/);assert.equal(h.calls.filter(c=>c.url.endsWith('/PATTERN_V1/stop')).length,0);});
test('ADX badge uses complete backend regime metadata even when legacy snapshot omits defaults',async()=>{const h=harness();h.data.strategies[1].configurationSnapshot={marketRegimeFilterEnabled:true};h.data.strategies[1].regime={enabled:true,adxPeriod:14,adxThreshold:25,mode:'EXCLUDE_WITH_TREND'};await h.login();assert.ok(walk(h.get('strategies').children[1]).some(n=>n.textContent.includes('ADX14 >= 25 / EXCLUDE_WITH_TREND')));});
test('Global kill requires exact confirmation and refresh only requests reconciliation',async()=>{const h=harness();await h.login();h.get('desk-kill').onclick();await h.flush();assert.equal(h.calls.filter(c=>c.url.endsWith('/account/kill')).length,0);h.setConfirmation('ATTIVA KILL SWITCH');h.get('desk-kill').onclick();await h.flush();assert.equal(h.calls.find(c=>c.url.endsWith('/account/kill')).body.confirmation,'ATTIVA KILL SWITCH');await h.get('desk-refresh').onclick();assert.equal(h.calls.filter(c=>c.url.endsWith('/refresh')).length,2);assert.equal(h.calls.filter(c=>c.url.endsWith('/start')).length,0);});
test('Central UI does not persist credentials or invent equity dates or backend states',()=>{const js=fs.readFileSync('src/main/resources/static/paper-trading.js','utf8');assert.doesNotMatch(js,/localStorage|sessionStorage|innerHTML/);assert.match(js,/data\.comparison\.available/);assert.match(js,/s\.mode/);assert.match(js,/SHADOW/);});

test('V1 has no embedded PAPER buttons and V2 uses the same frozen configuration layout',()=>{
const one=fs.readFileSync('src/main/resources/static/pattern-v1.html','utf8'),two=fs.readFileSync('src/main/resources/static/pattern-v2.html','utf8');
assert.doesNotMatch(one,/paper-tab|paper-panel|pattern-v1-paper.js/);
for(const html of [one,two]){assert.match(html,/panel research-config/);assert.match(html,/research-form/);assert.match(html,/Configurazione completa congelata/);assert.match(html,/LONG Target R/);assert.match(html,/class="v1-metrics"/);}
assert.ok(two.includes('<details><summary>Configurazione completa congelata</summary><pre id="stable-v2-config">'));
assert.ok(two.includes('href="/pattern-v3.html">Apri PATTERN V3 RESEARCH'));
});
test('V2 frozen form displays backend values without configuration writes or automatic runs',async()=>{
const elements=new Map(),calls=[];
function el(){return {children:[],value:'',disabled:false,replaceChildren(...v){this.children=v;},append(...v){this.children.push(...v);},add(v){this.children.push(v);}};}
function get(id){if(!elements.has(id))elements.set(id,el());return elements.get(id);}
const version=JSON.parse(fs.readFileSync('src/main/resources/pattern-v2-stable/1.0.json','utf8'));
vm.runInNewContext(fs.readFileSync('src/main/resources/static/pattern-v2-stable.js','utf8'),{
document:{getElementById:get,createElement:el},Option:function(text,value){return {text,value};},
fetch:async(url,options)=>{calls.push({url,options});return {ok:true,json:async()=>url.endsWith('/configuration')?version:[]};}
});
await new Promise(r=>setImmediate(r));
assert.equal(get('stable-v2-capital').value,version.configuration.global.initialCapital);
assert.equal(get('stable-v2-end').value,version.configuration.global.endDate);
assert.equal(get('stable-v2-adx-enabled').checked,true);
assert.equal(get('stable-v2-adx-period').value,14);
assert.equal(get('stable-v2-adx-threshold').value,25);
assert.equal(get('stable-v2-adx-threshold').disabled,true);
assert.match(get('stable-v2-rules').textContent,/13 barre/);
assert.equal(get('stable-v2-run').disabled,false);
assert.equal(calls.some(c=>c.options.method!=='GET'),false);
});


test('Local PAPER opens directly without a configured token and never connects or starts trading',async()=>{
const h=harness({localAccess:true,adminConfigured:false});await h.flush();
assert.equal(h.get('desk-login').hidden,true);assert.equal(h.get('desk-controls').hidden,false);assert.equal(h.get('strategies').children.length,2);
const bootstrap=h.calls.find(c=>c.url.endsWith('/local-session'));assert.ok(bootstrap);assert.equal(bootstrap.options.method,'POST');assert.equal(bootstrap.options.headers['X-Paper-Local-Session'],'1');assert.deepEqual(bootstrap.body,{});
assert.equal(h.calls.filter(c=>/\/login$|\/start$|\/connect$/.test(c.url)).length,0);
assert.equal(h.get('desk-logout').textContent,'Chiudi dashboard');
const stop=walk(h.get('strategies').children[0]).find(n=>n.dataset.operation==='stop');stop.onclick();await h.flush();
assert.equal(h.calls.find(c=>c.url.endsWith('/PATTERN_V1/stop')).options.headers['X-Paper-CSRF'],'csrf-test');
});
test('Remote PAPER keeps the password form and never requests local access',async()=>{
const h=harness({localAccess:false,adminConfigured:false});await h.flush();assert.equal(h.get('desk-login').hidden,false);assert.match(h.get('desk-message').textContent,/Accesso remoto/);assert.equal(h.calls.length,1);
});
test('Denied local session never falls back to unprotected status or trading operations',async()=>{
const h=harness({localAccess:true,adminConfigured:false,denyLocal:true});await h.flush();assert.equal(h.calls.length,2);assert.equal(h.calls.some(c=>c.url.includes('/status')),false);assert.equal(h.get('desk-message').textContent,'Local access denied');
});
test('Closing the local dashboard invalidates the session without changing strategy states',async()=>{
const h=harness({localAccess:true});await h.flush();await h.get('desk-logout').onclick();
assert.equal(h.get('desk-content').hidden,true);assert.equal(h.get('desk-login').hidden,true);assert.match(h.get('desk-message').textContent,/Ricarica la pagina/);
assert.equal(h.calls.filter(c=>c.url.endsWith('/logout')).length,1);assert.equal(h.calls.some(c=>/\/start$|\/stop$|\/kill$|\/connect$/.test(c.url)),false);
});
