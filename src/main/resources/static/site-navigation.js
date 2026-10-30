'use strict';
(() => {
  // Use a new document URL when moving between pages, bypassing cached pre-dropdown HTML.
  for (const link of document.querySelectorAll('.site-nav a[href]')) {
    const url = new URL(link.href, document.baseURI);
    if (url.origin !== new URL(document.baseURI).origin) continue;
    if (url.pathname !== '/' && !url.pathname.endsWith('.html')) continue;
    url.searchParams.set('nav', '20261030-v3');
    link.href = url.pathname + url.search + url.hash;
  }
  const menus = [...document.querySelectorAll('.site-nav details')];
  for (const menu of menus) {
    menu.addEventListener('toggle', () => {
      if (menu.open) for (const other of menus) if (other !== menu) other.open = false;
    });
  }
  document.addEventListener('click', event => {
    for (const menu of menus) {
      if (!menu.contains(event.target) || event.target.closest('a')) menu.open = false;
    }
  });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    const menu = menus.find(item => item.open);
    if (menu) {
      menu.open = false;
      menu.querySelector('summary').focus();
      event.preventDefault();
    }
  });
})();
