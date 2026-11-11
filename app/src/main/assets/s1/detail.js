(function () {
  var meta = function (p) { var m = document.querySelector('meta[property="' + p + '"], meta[name="' + p + '"]'); return m ? m.getAttribute('content') : ''; };
  var hls = null;
  try { if (typeof hlsUrl !== 'undefined' && hlsUrl) hls = String(hlsUrl); } catch (e) {}
  var header = document.querySelector('section.video-info .info-header') || document.querySelector('.info-header');
  var models = [], tags = [], seen = {};
  Array.prototype.forEach.call(document.querySelectorAll('.models a.model'), function (a) {
    var href = NB.abs(a.getAttribute('href'));
    if (seen[href]) return; seen[href] = 1;
    var im = a.querySelector('img.avatar'), ph = a.querySelector('span.placeholder');
    // bootstrap tooltip 初始化后会把 title 挪到 data-original-title
    var attr = function (el) { return el ? (el.getAttribute('data-original-title') || el.getAttribute('title') || '') : ''; };
    var name = attr(im) || attr(ph) || attr(a) ||
               a.getAttribute('title') || (a.querySelector('[data-original-title]') || { getAttribute: function () { return ''; } }).getAttribute('data-original-title') || NB.txt(a);
    if (name.length <= 1) name = decodeURIComponent((href.match(/\/models\/([^\/?#]+)/) || [])[1] || name).replace(/-/g, ' ');
    if (name) models.push({ name: name, url: href, cover: im ? NB.img(im) : '', group: '' });
  });
  Array.prototype.forEach.call(document.querySelectorAll('h5.tags a'), function (a) {
    var href = NB.abs(a.getAttribute('href'));
    if (seen[href]) return; seen[href] = 1;
    tags.push({ name: NB.txt(a), url: href, cover: '', group: a.classList.contains('cat') ? 'cat' : '' });
  });
  // 标题下方：上传时间、观看数（不要把女优头像的首字母也算进来）
  var info = '';
  if (header) {
    var spans = Array.prototype.map.call(header.querySelectorAll('.header-left h6 > span'), NB.txt).filter(function (t) { return t; });
    if (spans.length >= 2) info = spans[0] + '  ·  ' + spans[1] + ' 次观看';
    else info = spans.join('  ·  ');
    var right = NB.txt(header.querySelector('.header-right h6')).replace(/^●\s*/, '');
    if (right) info += (info ? '  ·  ' : '') + right;
  }
  var current = NB.cleanVideoUrl(location.href.split('?')[0]);
  var related = NB.videos().filter(function (v) { return v.url.split('?')[0] !== current; });
  var vid = '';
  try { if (window.pageContext && pageContext.videoId) vid = String(pageContext.videoId); } catch (e) {}
  if (!vid) { var f = document.querySelector('[data-fav-video-id]'); if (f) vid = f.getAttribute('data-fav-video-id'); }
  var favBtns = Array.prototype.map.call(document.querySelectorAll('[data-fav-type]'), function (el) {
    return { type: el.getAttribute('data-fav-type'), active: /active|added|is-fav/.test(el.className) };
  });
  return JSON.stringify({
    videoId: vid, favState: favBtns, loggedIn: !!((window.pageContext && pageContext.userId) || document.querySelector('a[href*="logout"]')),
    challenge: NB.challenge(),
    title: NB.txt(header && header.querySelector('h4')) || meta('og:title') || document.title,
    cover: meta('og:image'),
    info: info, hls: hls, models: models, tags: tags, related: related
  });
})();
