// Teeming website: mobile menu, bestiary filter, lightbox, recipe-slot links, TOC highlight.
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

  const btn = $('.menu-btn'), nav = $('.nav');
  if (btn) btn.addEventListener('click', () => {
    const open = nav.classList.toggle('open');
    btn.setAttribute('aria-expanded', open);
  });

  // fade-in on scroll (content stays visible without JS, and a timer shows everything if the observer never fires)
  document.documentElement.classList.add('js');
  setTimeout(() => $$('.reveal').forEach(el => el.classList.add('in')), 2500);
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: 0.08 });
    $$('.reveal').forEach(el => io.observe(el));
  } else $$('.reveal').forEach(el => el.classList.add('in'));

  // bestiary: dimension chips and a name search
  const chips = $$('.chip[data-d]'), find = $('#find'), none = $('.nomatch');
  if (chips.length) {
    let dim = 'all';
    const apply = () => {
      const q = (find.value || '').trim().toLowerCase();
      let shown = 0;
      $$('.dimsec').forEach(sec => {
        let any = false;
        $$('.beast', sec).forEach(b => {
          const ok = (dim === 'all' || b.dataset.dim === dim) && (!q || b.dataset.s.includes(q));
          b.hidden = !ok; if (ok) { any = true; shown++; }
        });
        sec.hidden = !any;
        const idx = $('.mobidx', sec); if (idx) idx.hidden = !!q;
      });
      none.hidden = shown > 0;
    };
    chips.forEach(c => c.addEventListener('click', () => {
      dim = c.dataset.d;
      chips.forEach(o => o.setAttribute('aria-pressed', o === c));
      apply();
    }));
    find.addEventListener('input', apply);
  }

  // a recipe slot holding one of the mod's items jumps to that item's card
  $$('.slot[data-href]').forEach(s => s.addEventListener('click', () => { location.hash = s.dataset.href; }));

  // lightbox: walks the visible shots of the same group (a creature's two photos, or one gallery)
  const shots = $$('a.shot');
  if (shots.length) {
    const lb = document.createElement('div');
    lb.className = 'lb'; lb.setAttribute('role', 'dialog'); lb.setAttribute('aria-label', 'Image viewer');
    lb.innerHTML = '<button class="x" aria-label="Close">✕</button><button class="p" aria-label="Previous">‹</button><button class="n" aria-label="Next">›</button><img alt=""><div class="cap"></div>';
    document.body.appendChild(lb);
    const img = $('img', lb), cap = $('.cap', lb);
    let list = [], idx = 0;
    const show = i => {
      idx = (i + list.length) % list.length;
      const a = list[idx];
      img.src = a.href; img.alt = a.dataset.cap || '';
      cap.textContent = a.dataset.cap || '';
    };
    const close = () => lb.classList.remove('open');
    const group = a => a.closest('.dimsec, .strip, .figs, section, main');
    shots.forEach(a => a.addEventListener('click', e => {
      e.preventDefault();
      list = shots.filter(s => !s.closest('[hidden]') && group(s) === group(a));
      show(list.indexOf(a));
      lb.classList.add('open');
    }));
    $('.x', lb).onclick = close;
    $('.p', lb).onclick = () => show(idx - 1);
    $('.n', lb).onclick = () => show(idx + 1);
    lb.addEventListener('click', e => { if (e.target === lb) close(); });
    document.addEventListener('keydown', e => {
      if (!lb.classList.contains('open')) return;
      if (e.key === 'Escape') close();
      if (e.key === 'ArrowLeft') show(idx - 1);
      if (e.key === 'ArrowRight') show(idx + 1);
    });
  }

  // TOC highlight
  const toc = $$('.toc a');
  if (toc.length && 'IntersectionObserver' in window) {
    const map = new Map(toc.map(a => [a.getAttribute('href').slice(1), a]));
    const io2 = new IntersectionObserver(es => es.forEach(e => {
      if (e.isIntersecting) { toc.forEach(a => a.classList.remove('on')); const a = map.get(e.target.id); if (a) a.classList.add('on'); }
    }), { rootMargin: '-20% 0px -70% 0px' });
    map.forEach((_, id) => { const el = document.getElementById(id); if (el) io2.observe(el); });
  }
})();
