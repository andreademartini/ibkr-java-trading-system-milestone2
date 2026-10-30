const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const root = 'src/main/resources/static/';

test('Every page has the same three native dropdowns with valid local destinations', () => {
  for (const file of fs.readdirSync(root).filter(name => name.endsWith('.html'))) {
    const html = fs.readFileSync(root + file, 'utf8');
    const nav = html.match(/<nav class="top-actions site-nav"[^>]*>([\s\S]*?)<\/nav>/)?.[1];
    assert.ok(nav, file);
    assert.deepEqual([...nav.matchAll(/<summary>(.*?)<\/summary>/g)].map(m => m[1]), ['Strategie', 'Pattern Research', 'Configurazione']);
    assert.doesNotMatch(nav, /momentum/i);
    assert.equal((nav.match(/aria-current="page"/g) || []).length, 1, file);
    assert.equal((html.match(/site-navigation\.js/g) || []).length, 1, file);
    for (const [, href] of nav.matchAll(/href="([^"]+)"/g)) {
      if (href.startsWith('/api/')) continue;
      assert.ok(fs.existsSync(root + (href === '/' ? 'index.html' : href.slice(1))), file + ': ' + href);
    }
  }
});

test('Navigation preserves page actions and keeps each destination in its intended dropdown', () => {
  const html = fs.readFileSync(root + 'index.html', 'utf8');
  const groups = [...html.matchAll(/<summary>(.*?)<\/summary><div class="site-nav-panel">([\s\S]*?)<\/div>/g)];
  assert.match(groups[0][2], /weekly-high-tte\.html/);
  assert.match(groups[1][2], /research\.html/);
  assert.match(groups[2][2], /config\.html/);
  assert.match(groups[2][2], /provider\.html/);
  for (const file of ['index.html', 'config.html', 'provider.html']) {
    assert.equal((fs.readFileSync(root + file, 'utf8').match(/id="refresh-button"/g) || []).length, 1);
  }
});

function harness(links = []) {
  const events = {};
  const menus = Array.from({length: 3}, () => ({open: false, events: {}, focused: false,
    addEventListener(name, callback) {this.events[name] = callback;},
    contains(node) {return node.menu === this;},
    querySelector() {return {focus: () => {this.focused = true;}};}
  }));
  vm.runInNewContext(fs.readFileSync(root + 'site-navigation.js', 'utf8'), {
    URL,
    document: {baseURI: 'http://localhost:8081/', querySelectorAll: selector => selector === '.site-nav a[href]' ? links : menus, addEventListener(name, callback) {events[name] = callback;}}
  });
  return {menus, events};
}

test('Opening a dropdown closes the other dropdowns', () => {
  const {menus} = harness();menus[0].open = true;menus[1].open = true;menus[1].events.toggle();
  assert.equal(menus[0].open, false);assert.equal(menus[1].open, true);
});

test('Escape closes the dropdown and restores keyboard focus to its summary', () => {
  const {menus, events} = harness();menus[2].open = true;let prevented = false;
  events.keydown({key: 'Escape', preventDefault() {prevented = true;}});
  assert.equal(menus[2].open, false);assert.equal(menus[2].focused, true);assert.equal(prevented, true);
});

test('An outside click or a destination click closes the dropdown', () => {
  const {menus, events} = harness();menus[0].open = true;
  events.click({target: {menu: null, closest: () => null}});assert.equal(menus[0].open, false);
  menus[0].open = true;events.click({target: {menu: menus[0], closest: () => ({})}});assert.equal(menus[0].open, false);
});

test('All page destinations bypass old cached HTML, including links opened in a new tab', () => {
  const links = [{href: '/pattern-v1.html'}, {href: '/pattern-v3.html?filter=long#results'}, {href: '/'}, {href: '/api/paper/status'}, {href: 'https://example.com/help.html'}];
  harness(links);
  assert.equal(links[0].href, '/pattern-v1.html?nav=20261030-v3');
  assert.equal(links[1].href, '/pattern-v3.html?filter=long&nav=20261030-v3#results');
  assert.equal(links[2].href, '/?nav=20261030-v3');
  assert.equal(links[3].href, '/api/paper/status');assert.equal(links[4].href, 'https://example.com/help.html');
});
