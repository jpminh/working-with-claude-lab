const { loadApp } = require('./setup/loadApp');

const STORAGE_KEY = 'ops-dashboard-theme';

function click(document, id) {
  document.getElementById(id).dispatchEvent(new window.Event('click', { bubbles: true }));
}

function theme(document) {
  return document.documentElement.getAttribute('data-theme');
}

describe('theme toggle (TODO-231)', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  test('AC-4: with nothing stored the dashboard starts dark, whatever the OS prefers', async () => {
    window.matchMedia = () => ({ matches: true, media: '(prefers-color-scheme: light)' });
    try {
      const { document } = await loadApp();
      expect(theme(document)).toBe('dark');
      expect(window.localStorage.getItem(STORAGE_KEY)).toBeNull();
    } finally {
      delete window.matchMedia;
    }
  });

  test('AC-1, AC-2: clicking #theme-toggle switches data-theme on <html> and the label names the theme you will get', async () => {
    const { document } = await loadApp();
    const toggle = document.getElementById('theme-toggle');
    expect(toggle.textContent).toBe('Light theme');

    click(document, 'theme-toggle');
    expect(theme(document)).toBe('light');
    expect(toggle.textContent).toBe('Dark theme');

    click(document, 'theme-toggle');
    expect(theme(document)).toBe('dark');
    expect(toggle.textContent).toBe('Light theme');
  });

  test('AC-3: the choice is stored in localStorage and restored on the next load', async () => {
    const first = await loadApp();
    click(first.document, 'theme-toggle');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('light');

    document.documentElement.removeAttribute('data-theme');
    const second = await loadApp();
    expect(theme(second.document)).toBe('light');
    expect(second.document.getElementById('theme-toggle').textContent).toBe('Dark theme');
  });
});
