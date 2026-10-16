const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function render(version, kind) {
    const elements = new Map();
    const element = () => ({
        children: [], value: '', textContent: '',
        get options() { return this.children; },
        append(...nodes) { this.children.push(...nodes); },
        replaceChildren(...nodes) { this.children = nodes; }
    });
    const get = id => {
        if (!elements.has(id)) elements.set(id, element());
        return elements.get(id);
    };
    const row = {
        key: 'AAPL|BULLISH_ENGULFING|LONG|STRONG_DOWNTREND|COUNTER_TREND|2010-2013',
        symbol: 'AAPL', pattern: 'BULLISH_ENGULFING', direction: 'LONG',
        regime: 'STRONG_DOWNTREND', period: '2010-2013',
        metrics: {trades: 0, winners: 0, losers: 0, netPnl: -1234.5678, averageADX: null, smallSample: true}
    };
    const run = {
        runId: 'historical-run', configuration: {name: 'Snapshot', marketRegimeFilterEnabled: true},
        marketRegimeAnalysis: {
            overview: [], secondaryOverview: [], adxBuckets: [], trendDirection: [], emaTrendDirection: [],
            periods: [], loserGroups: [], trades: [], signals: [], patternRegimes: [row]
        },
        marketRegimeFilterAnalysis: {summary: {totalPatternSignals: 0}, acceptedGroups: [row]}
    };
    const before = JSON.stringify(run);
    const context = {
        document: {getElementById: get, createElement: element},
        window: {addEventListener() {}}, requestAnimationFrame() {}
    };
    vm.runInNewContext(fs.readFileSync(`src/main/resources/static/pattern-v${version}-${kind}.js`, 'utf8'), context);
    (kind === 'regime' ? context.window[`PatternV${version}Regime`] : context[`PatternV${version}Filter`]).render(run);
    assert.equal(JSON.stringify(run), before, 'Formatting must preserve the saved data');
    return {get, row};
}

for (const version of [2, 3]) {
    test(`V${version} regime table preserves complete long labels, all columns, zero samples and N/D`, () => {
        const {get, row} = render(version, 'regime');
        const table = get(`v${version}-regime-patterns`).children[0];
        const headers = table.children[0].children[0].children;
        const cells = table.children[1].children[0].children;
        const index = label => headers.findIndex(h => h.textContent === label);
        assert.ok(headers.length > 30);
        assert.equal(cells.length, headers.length);
        assert.equal(cells[index('Group')].textContent, row.key);
        assert.equal(cells[index('Pattern')].textContent, row.pattern);
        assert.equal(cells[index('Trades')].textContent, '0');
        assert.equal(cells[index('Trades')].className, 'regime-numeric');
        assert.equal(headers[index('Trades')].className, 'regime-numeric');
        assert.equal(cells[index('Average ADX')].textContent, 'N/D');
        assert.equal(cells[index('Net PnL')].textContent, (-1234.5678).toLocaleString('it-IT', {maximumFractionDigits: 4}));
        assert.equal(cells[index('Net PnL')].className, 'regime-numeric');
        get(`v${version}-regime-symbol`).value = 'NOT_PRESENT';
        get(`v${version}-regime-symbol`).onchange();
        assert.equal(get(`v${version}-regime-patterns`).children[0].children[1].children.length, 0);
    });

    test(`V${version} filter tables keep zero-trade groups and missing values after presentation changes`, () => {
        const {get, row} = render(version, 'filter');
        const table = get(`v${version}-regime-filter-groups`).children[0];
        const headers = table.children[0].children;
        const cells = table.children[1].children;
        assert.equal(cells.length, headers.length);
        assert.equal(cells[0].textContent, row.key);
        assert.equal(cells[1].textContent, '0');
        assert.equal(cells[1].className, 'regime-numeric');
        assert.equal(cells[headers.findIndex(h => h.textContent === 'pfNet')].textContent, 'N/D');
    });
}
