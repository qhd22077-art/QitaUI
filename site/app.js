// The QitaUI Flash games page: the game list (catalogue.json, the same file the launcher reads) and a search of the Internet Archive.
// Nothing here hosts a game; it only links to files and, for "Play", loads Ruffle (an open-source Flash emulator) from a CDN.
(function () {
  var source = 0; // 0 = game list, 1 = Internet Archive
  var q = document.getElementById('q');
  var results = document.getElementById('results');
  var status = document.getElementById('status');
  var tabs = [document.getElementById('tab-list'), document.getElementById('tab-ia'), document.getElementById('tab-sites')];

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
      var fl = e.files || { swf: [], zips: [] };
      fl.swf.slice(0, 3).forEach(function (f) {
        var a = document.createElement('a'); a.href = f.url; a.textContent = 'Download ' + f.name.split('/').pop(); a.rel = 'noopener'; actions.appendChild(a);
      });
      fl.zips.slice(0, 2).forEach(function (f) {
        var a = document.createElement('a'); a.href = f.url; a.textContent = 'Zip, ' + Math.round(f.size / 1048576) + ' MB'; a.rel = 'noopener'; actions.appendChild(a);
      });
      if (fl.swf.length > 3) { var more = document.createElement('span'); more.textContent = '+' + (fl.swf.length - 3) + ' more on the Archive page'; actions.appendChild(more); }
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

  // The Archive's search cannot tell what a file is, so it is asked broadly and each candidate's own file list is checked:
  // only items that really hold a .swf (or a small zip) are shown.
  var filesCache = {};
  function archiveFiles(id) {
    if (filesCache[id]) return Promise.resolve(filesCache[id]);
    return fetch('https://archive.org/metadata/' + enc(id)).then(function (r) { return r.json(); }).then(function (j) {
      var out = { swf: [], zips: [], note: '' };
      if (j.is_dark || (j.metadata && String(j.metadata['access-restricted-item']) === 'true')) { out.note = 'Restricted on the Archive'; return out; }
      (j.files || []).forEach(function (f) {
        var link = 'https://archive.org/download/' + enc(id) + '/' + f.name.split('/').map(enc).join('/');
        var size = parseInt(f.size, 10) || 0;
        if (/\.swf$/i.test(f.name)) { if (out.swf.length < 40) out.swf.push({ url: link, name: f.name }); }
        else if (/\.zip$/i.test(f.name) && size > 0 && size <= 150 * 1048576) out.zips.push({ url: link, name: f.name, size: size });
      });
      out.zips.sort(function (a, b) { return a.size - b.size; });
      out.zips = out.swf.length ? [] : out.zips.slice(0, 3);
      filesCache[id] = out;
      return out;
    }).catch(function () { return { swf: [], zips: [], note: 'Could not be reached' }; });
  }

  function iaQuery(qs) {
    var url = 'https://archive.org/advancedsearch.php?q=' + enc(qs) +
      '&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description&rows=40&page=1&output=json&sort%5B%5D=downloads+desc';
    return fetch(url).then(function (r) { return r.json(); }).then(function (j) { return (j.response && j.response.docs) || []; }).catch(function () { return null; });
  }

  function inBatches(items, size, fn) {
    var out = [];
    var chain = Promise.resolve();
    for (var i = 0; i < items.length; i += size) {
      (function (chunk) {
        chain = chain.then(function () { return Promise.all(chunk.map(fn)); }).then(function (r) { out = out.concat(r); });
      })(items.slice(i, i + size));
    }
    return chain.then(function () { return out; });
  }

  function searchArchive() {
    var terms = q.value.trim().replace(/[\\"():\[\]{}^~*?!+\/&|-]/g, ' ').replace(/\s+/g, ' ').trim() || 'game';
    status.textContent = 'Searching the Internet Archive...';
    results.textContent = '';
    Promise.all([
      iaQuery('(' + terms + ') AND (format:Flash OR format:SWF)'),
      iaQuery('(' + terms + ') AND (flash OR swf) AND mediatype:(software OR movies OR data)')
    ]).then(function (lists) {
      if (lists[0] === null && lists[1] === null) { status.textContent = 'The Internet Archive could not be reached.'; return; }
      var seen = {};
      var cands = [];
      (lists[0] || []).concat(lists[1] || []).forEach(function (d) {
        if (seen[d.identifier]) return;
        seen[d.identifier] = true;
        var t = Array.isArray(d.title) ? d.title[0] : d.title;
        if (/\b(flash ?player|installer|plug-?in|macromedia|adobe|projector|authoring|animate|cs[0-9]|mx)\b/i.test(t || '')) return;
        var desc = Array.isArray(d.description) ? d.description[0] : (d.description || '');
        cands.push({ id: d.identifier, title: t || d.identifier, blurb: String(desc).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120), url: '', thumb: 'https://archive.org/services/img/' + enc(d.identifier) });
      });
      cands = cands.slice(0, 30);
      status.textContent = 'Checking ' + cands.length + ' results for Flash files...';
      return inBatches(cands, 6, function (e) { return archiveFiles(e.id).then(function (f) { e.files = f; return e; }); }).then(function (done) {
        var good = done.filter(function (e) { return e.files.swf.length || e.files.zips.length; });
        show(good, 'Checked ' + done.length + ' results and none hold Flash files. Try other words.');
        if (good.length) status.textContent = 'Checked ' + done.length + ' results: ' + good.length + ' hold Flash files.';
      });
    });
  }

  // Direct links and game-list files. Other sites' pages cannot be read from a web page (browsers forbid it); the launcher's My sites can.
  function searchSites() {
    var a = q.value.trim();
    results.textContent = '';
    if (!a) { status.textContent = 'Paste a link to a game (.swf), or to a game-list file in the same format as catalogue.json. The QitaUI launcher can also read web pages (Store, Flash games, My sites).'; return; }
    if (a.indexOf('://') < 0) a = 'https://' + a;
    if (/\.swf(\?|$)/i.test(a)) {
      var name = decodeURIComponent(a.split('?')[0].split('/').pop().replace(/\.swf$/i, '')).replace(/[_-]+/g, ' ');
      show([{ id: a, title: name, blurb: a, url: a, thumb: '' }], '');
      return;
    }
    status.textContent = 'Reading the list...';
    fetch(a).then(function (r) { return r.json(); }).then(function (j) {
      var arr = Array.isArray(j) ? j : (j.games || []);
      var list = arr.map(function (g) {
        if (typeof g === 'string') return { id: g, title: g.split('/').pop(), blurb: '', url: g, thumb: '' };
        return { id: g.url, title: g.title || g.url, blurb: g.description || g.author || '', url: g.url, thumb: g.thumb || '' };
      }).filter(function (g) { return g.url; });
      show(list, 'That file holds no games (it needs a "games" list with a title and a url for each).');
    }).catch(function () { status.textContent = 'That address could not be read from a web page (many sites do not allow it). Paste a direct .swf link or a game-list file, or use the QitaUI launcher, which can read pages.'; });
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

  function run() { if (source === 0) loadList(); else if (source === 1) searchArchive(); else searchSites(); }
  tabs.forEach(function (t, i) {
    t.onclick = function () { source = i; tabs.forEach(function (x, k) { x.className = k === i ? 'on' : ''; }); results.textContent = ''; run(); };
  });
  document.getElementById('search').onsubmit = function (ev) { ev.preventDefault(); run(); };
  run();
})();
