// The QitaUI Flash games page: the game list (catalogue.json, the same file the launcher reads) and a search of the Internet Archive.
// Nothing here hosts a game; it only links to files and, for "Play", loads Ruffle (an open-source Flash emulator) from a CDN.
(function () {
  var source = 0; // 0 = game list, 1 = Internet Archive
  var q = document.getElementById('q');
  var results = document.getElementById('results');
  var status = document.getElementById('status');
  var tabs = [document.getElementById('tab-list'), document.getElementById('tab-ia')];

  function enc(s) { return encodeURIComponent(s); }

  function card(e) {
    var div = document.createElement('div');
    div.className = 'card';
    var thumb;
    if (e.thumb) { thumb = document.createElement('img'); thumb.src = e.thumb; thumb.alt = ''; thumb.loading = 'lazy'; }
    else { thumb = document.createElement('div'); thumb.textContent = (e.title || '?').charAt(0).toUpperCase(); }
    thumb.className = 'thumb';
    var body = document.createElement('div');
    var h = document.createElement('h3'); h.textContent = e.title;
    var p = document.createElement('p'); p.textContent = e.blurb || '';
    var actions = document.createElement('div'); actions.className = 'actions';
    if (e.url) {
      var a = document.createElement('a'); a.href = e.url; a.textContent = 'Download'; a.rel = 'noopener'; actions.appendChild(a);
      var play = document.createElement('button'); play.type = 'button'; play.textContent = 'Play'; play.onclick = function () { playGame(e); }; actions.appendChild(play);
    } else {
      var look = document.createElement('button'); look.type = 'button'; look.textContent = 'Get the .swf';
      look.onclick = function () { resolveArchive(e, look); }; actions.appendChild(look);
      var page = document.createElement('a'); page.href = 'https://archive.org/details/' + enc(e.id); page.textContent = 'Archive page'; page.rel = 'noopener'; page.style.background = 'none'; page.style.border = '1px solid rgba(255,255,255,.22)'; actions.appendChild(page);
    }
    body.appendChild(h); body.appendChild(p); body.appendChild(actions);
    div.appendChild(thumb); div.appendChild(body);
    return div;
  }

  function show(list, empty) {
    results.textContent = '';
    list.forEach(function (e) { results.appendChild(card(e)); });
    status.textContent = list.length ? '' : empty;
  }

  function loadList() {
    status.textContent = 'Loading...';
    fetch('catalogue.json', { cache: 'no-cache' }).then(function (r) { return r.json(); }).then(function (j) {
      var term = q.value.trim().toLowerCase();
      var list = (j.games || []).filter(function (g) { return g.title && g.url; }).map(function (g) {
        return { id: g.url, title: g.title, blurb: g.description || g.author || '', url: g.url, thumb: g.thumb || '' };
      }).filter(function (g) { return !term || (g.title + ' ' + g.blurb).toLowerCase().indexOf(term) >= 0; });
      show(list, 'The game list is empty or nothing matches. Games are added to flash/catalogue.json in the repository; try the Internet Archive tab.');
    }).catch(function () { status.textContent = 'The game list could not be loaded.'; });
  }

  function searchArchive() {
    var terms = q.value.trim().replace(/"/g, ' ') || 'flash';
    var url = 'https://archive.org/advancedsearch.php?q=' + enc('(' + terms + ') AND mediatype:software AND (flash OR swf)') +
      '&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description&rows=40&page=1&output=json&sort%5B%5D=downloads+desc';
    status.textContent = 'Searching the Internet Archive...';
    fetch(url).then(function (r) { return r.json(); }).then(function (j) {
      var docs = (j.response && j.response.docs) || [];
      var list = docs.map(function (d) {
        var t = Array.isArray(d.title) ? d.title[0] : d.title;
        var desc = Array.isArray(d.description) ? d.description[0] : (d.description || '');
        return { id: d.identifier, title: t || d.identifier, blurb: String(desc).replace(/<[^>]*>/g, ' ').trim().slice(0, 140), url: '', thumb: 'https://archive.org/services/img/' + enc(d.identifier) };
      });
      show(list, 'Nothing found.');
    }).catch(function () { status.textContent = 'The Internet Archive could not be reached.'; });
  }

  function resolveArchive(e, btn) {
    btn.disabled = true; btn.textContent = 'Looking...';
    fetch('https://archive.org/metadata/' + enc(e.id)).then(function (r) { return r.json(); }).then(function (j) {
      var f = (j.files || []).filter(function (x) { return /\.swf$/i.test(x.name); })[0];
      if (!f) { btn.textContent = 'No .swf in this item'; return; }
      var link = 'https://archive.org/download/' + enc(e.id) + '/' + f.name.split('/').map(enc).join('/');
      var a = document.createElement('a'); a.href = link; a.textContent = 'Download ' + f.name.split('/').pop(); a.rel = 'noopener';
      btn.replaceWith(a);
    }).catch(function () { btn.disabled = false; btn.textContent = 'Try again'; });
  }

  var rufflePromise = null;
  function ruffle() {
    if (!rufflePromise) rufflePromise = new Promise(function (ok, fail) {
      var s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle';
      s.onload = function () { ok(window.RufflePlayer.newest()); };
      s.onerror = fail;
      document.head.appendChild(s);
    });
    return rufflePromise;
  }

  function playGame(e) {
    var stage = document.getElementById('stage');
    document.getElementById('stage-title').textContent = e.title;
    var holder = document.getElementById('stage-player');
    holder.textContent = 'Loading the player...';
    stage.hidden = false;
    ruffle().then(function (rf) {
      var player = rf.createPlayer();
      holder.textContent = '';
      holder.appendChild(player);
      player.load(e.url);
    }).catch(function () { holder.textContent = 'The player could not be loaded. Use Download and play it in the launcher.'; });
  }

  document.getElementById('stage-close').onclick = function () {
    document.getElementById('stage').hidden = true;
    document.getElementById('stage-player').textContent = '';
  };

  function run() { if (source === 0) loadList(); else searchArchive(); }
  tabs.forEach(function (t, i) {
    t.onclick = function () { source = i; tabs.forEach(function (x, k) { x.className = k === i ? 'on' : ''; }); results.textContent = ''; run(); };
  });
  document.getElementById('search').onsubmit = function (ev) { ev.preventDefault(); run(); };
  run();
})();
