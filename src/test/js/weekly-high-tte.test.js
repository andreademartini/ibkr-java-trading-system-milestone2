const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function harness() {
  const elements = new Map(), calls = [];
  function element() {
    return {children: [], value: '', disabled: false, hidden: false, dataset: {}, style: {}, attributes: {},
      setAttribute(key,value) {this.attributes[key]=value;},
      set id(value) {elements.set(value, this);},
      append(...nodes) {this.children.push(...nodes);},
      replaceChildren(...nodes) {this.children = nodes;}, querySelectorAll() {return [];}};
  }
  function get(id) {if (!elements.has(id)) elements.set(id, element()); return elements.get(id);}
  const config = {name: 'Research', mode: 'WEEKLY_HIGH_TTE', symbols: ['AAPL'],
    startDate: '2024-01-01', endDate: '2024-12-31', initialCapital: 10000, riskPercent: 2,
    maxOrderValuePercent: 30, maxOrderValueMode: 'CURRENT_EQUITY_PERCENT', useLeverage: false,
    maxPositionValuePercent: 100, maxLongNotionalPercent: 100, maxShortNotionalPercent: 100,
    slippagePercentPerSide: .03, momentumFilter: true, momentumLookback: 5,
    pullbackLookbackBars: 5, minPullbackBars: 1, maxPullbackBars: 3,
    entryBufferMode: 'TICKS', entryBuffer: 1, stopBufferMode: 'TICKS', stopBuffer: 1,
    tickSize: .01, entryValidityBars: 3, minRewardRiskRatio: 1.5, maxHoldingDays: 10,
    gapHandling: 'OPEN_FIRST', ambiguousBarPolicy: 'CONSERVATIVE', breakoutTargetR: 2,
    partialClosePercent: 50, residualTargetR: 3, commission: {model: 'IBKR_US_FIXED'},
    splitCosts: {rules: []}, splitCommissionMode: 'SPLIT_AWARE_V1'};
  const run = {runId: 'run-1', analysis: {performance: {finalEquity: 10001},directionPerformance:[{direction:'LONG',trades:1},{direction:'SHORT',trades:2}],historicalLevels:[{symbol:'AAPL',levelType:'HIGH',levelPrice:102,originWeeks:['2024-01-01'],availableFrom:'2024-01-08',status:'ACTIVE'}],levelChartData:{AAPL:[{date:'2024-01-08',open:100,high:103,low:98,close:101},{date:'2024-01-09',open:101,high:104,low:99,close:102}]}}, setups: [], trades: [], warnings: []};
  vm.runInNewContext("const PortfolioSummary={render(){},clear(){},load(){}};\n"+fs.readFileSync('src/main/resources/static/weekly-high-tte.js', 'utf8'), {
    document: {getElementById: get, createElement: element,createElementNS(namespace,name){const el=element();el.namespaceURI=namespace;el.tag=name;return el;}, querySelectorAll() {return [];}},
    setTimeout, URL, async fetch(url, options) {
      calls.push({url, method: options.method, body: options.body ? JSON.parse(options.body) : null});
      let value = url.endsWith('/configuration') ? config : url.endsWith('/configurations') ? [config]
        : url.endsWith('/runs') ? {runs: [], configurationsExperimented: 0} : run;
      return {ok: true, async json() {return value;}};
    }
  });
  return {get, calls, config, async flush() {await new Promise(resolve => setImmediate(resolve));}};
}

test('Weekly TTE page opens read-only and never starts a backtest or broker connection', async () => {
  const h = harness(); await h.flush();
  assert.equal(h.calls.length, 3);
  assert.ok(h.calls.every(c => c.method === 'GET' && c.url.startsWith('/api/backtest/weekly-high-tte/')));
  assert.equal(h.get('modes').disabled, true);
  assert.equal(h.get('tte-riskPercent').value, 2);
});

test('Weekly TTE form preserves independent costs, buffers and risk parameters', async () => {
  const h = harness(); await h.flush();
  h.get('tte-riskPercent').value = '1.25'; h.get('tte-momentumFilter').checked = false;
  h.get('tte-entryBufferMode').value = 'PERCENT'; h.get('tte-entryBuffer').value = '.05';
  h.get('tte-symbols').value = 'AAPL, MSFT';
  h.get('config').onsubmit({preventDefault() {}}); await h.flush();
  const submitted = h.calls.find(c => c.url.endsWith('/run')).body;
  assert.equal(submitted.riskPercent, 1.25); assert.equal(submitted.momentumFilter, false);
  assert.equal(submitted.entryBufferMode, 'PERCENT'); assert.equal(submitted.entryBuffer, .05);
  assert.deepEqual(submitted.symbols, ['AAPL', 'MSFT']);
  assert.deepEqual(submitted.commission, h.config.commission);
  assert.equal(h.config.riskPercent, 2);
  assert.equal(h.get('export').href, '/api/backtest/weekly-high-tte/runs/run-1/export/excel');
  assert.ok(h.calls.every(c => c.url.startsWith('/api/backtest/weekly-high-tte/')));
});

test('Weekly TTE form refuses non-finite numeric values before a request', async () => {
  const h = harness(); await h.flush(); h.get('tte-riskPercent').value = 'NaN';
  h.get('config').onsubmit({preventDefault() {}}); await h.flush();
  assert.equal(h.calls.some(c => c.method === 'POST'), false);
  assert.match(h.get('status').textContent, /Parametro numerico non valido/);
});

test('Historical LONG/SHORT controls upgrade editable legacy configuration and render levels and chart', async () => {
  const h=harness();await h.flush();
  assert.equal(h.get('tte-allowLong').checked,true);assert.equal(h.get('tte-allowShort').checked,true);
  h.get('tte-allowLong').checked=false;
  h.get('config').onsubmit({preventDefault(){}});await h.flush();
  const c=h.calls.find(c=>c.url.endsWith('/run')).body;
  assert.equal(c.allowLong,false);assert.equal(c.allowShort,true);assert.equal(c.rulesVersion,'WEEKLY_UNTOUCHED/2');
  assert.equal(h.get('levels').children[0].children[1].children.length,1);
  assert.equal(h.get('directions').children[0].children[1].children.length,2);
  const svg=h.get('level-chart').children[1].children[0];assert.equal(svg.tag,'svg');assert.match(svg.attributes['aria-label'],/AAPL/);
  assert.ok(svg.children.some(n=>n.tag==='line'&&n.attributes.stroke==='#43d9a3'));
  assert.equal(h.config.rulesVersion,undefined);
});
