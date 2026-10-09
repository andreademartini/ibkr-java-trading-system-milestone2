const test = require("node:test");
const assert = require("node:assert/strict");
const presentation = require("../../main/resources/static/pattern-v1-metrics.js");

test("PATTERN V1 summary separates closed performance from unrealized PnL and cash from equity", () => {
    const run = {finalCapital:10250,finalCash:5000,finalLongMarketValue:5250,averageGrossExposurePercent:51.2,timeInMarketPercent:75,
        summary:{realizedNetPnl:0,unrealizedPnl:250,netPnl:250,profitFactor:null,expectancyR:null,closedTrades:0,openTrades:1}};
    const fields = Object.fromEntries(presentation.summary(run).map(metric => [metric.label,metric]));
    assert.equal(fields["Final Equity"].value,10250);
    assert.equal(fields["Final Cash"].value,5000);
    assert.equal(fields["Realized Net PnL"].value,0);
    assert.equal(fields["Unrealized PnL"].value,250);
    assert.equal(fields["Profit Factor (PnL)"].value,null);
    assert.equal(fields["Expectancy R"].value,null);
    assert.equal(fields["Average Gross Exposure %"].value,51.2);
    assert.equal(fields["Time In Market %"].value,75);
});
test("PATTERN V1 metric units and explanations are explicit", () => {
    const labels = presentation.definitions.map(([label]) => label);
    for (const label of ["Average Winner PnL","Average Loser PnL","Average Winner R","Average Loser R","Breakevens","Profit Factor (R)","Executed MAE R"])
        assert.ok(labels.includes(label),label);
    for (const ambiguous of ["Average Winner","Average Loser","Exposure","Final Capital"])assert.ok(!labels.includes(ambiguous));
    assert.ok(presentation.definitions.every(([, , , tooltip])=>tooltip.length>15));
    assert.ok(presentation.definitions.find(([label])=>label==="Profit Factor (PnL)")[3].includes("CHIUSI"));
});
