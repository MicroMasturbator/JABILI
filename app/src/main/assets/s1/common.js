// 公共工具：在页面里执行，结果以 JSON 字符串返回给原生层
// 选择器依据 2026-11 实测的页面结构
var NB = (function () {
  function abs(u) { try { return new URL(u, location.href).href; } catch (e) { return u || ''; } }
  function txt(el) { return el ? (el.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function img(el) {
    if (!el) return '';
    var u = el.getAttribute('data-src') || el.getAttribute('data-original') || '';
    if (!u) u = el.getAttribute('src') || '';
    return u && u.indexOf('data:') !== 0 ? abs(u) : '';
  }
  function challenge() {
    var t = document.title || '';
    return /just a moment|attention required|请稍候|請稍候|稍等/i.test(t) ||
      !!document.querySelector('#challenge-form, #cf-wrapper, .cf-turnstile, #challenge-stage, #challenge-running');
  }
  // 去掉 /s0/ 之类的前缀，统一成 /videos/xxx/
  function cleanVideoUrl(u) { return abs(u).replace(/\/s\d+\/videos\//, '/videos/'); }
  // .sub-title 里是 [眼睛图标] 观看数 [爱心图标] 收藏数，数字用空格做千分位
  function stats(sub) {
    if (!sub) return '';
    var parts = sub.innerHTML.split(/<svg[\s\S]*?<\/svg>|<i\b[^>]*>[\s\S]*?<\/i>/i)
      .map(function (h) { return h.replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim(); })
      .filter(function (s) { return s; });
    if (parts.length >= 2) return '👁 ' + parts[0] + '   ♥ ' + parts[1];
    return txt(sub);
  }
  function videos(root) {
    root = root || document;
    var out = [], seen = {};
    Array.prototype.forEach.call(root.querySelectorAll('.video-img-box'), function (b) {
      var a = b.querySelector('.img-box a[href]') || b.querySelector('a[href*="/videos/"]');
      if (!a) return;
      var href = cleanVideoUrl(a.getAttribute('href'));
      if (seen[href]) return; seen[href] = 1;
      var title = txt(b.querySelector('.detail h6.title a')) || txt(b.querySelector('.title')) || (b.querySelector('img') || {}).alt || '';
      var dur = b.querySelector('.absolute-bottom-right .label') || b.querySelector('.label');
      var im = b.querySelector('img[data-src]') || b.querySelector('img');
      out.push({ url: href, title: title, cover: img(im), duration: txt(dur), sub: stats(b.querySelector('.sub-title')) });
    });
    return out;
  }
  // 下一页：当前页是 ul.pagination 里的 span.active，直接拼 /<路径>/<n+1>/
  function nextPage() {
    var pag = document.querySelector('ul.pagination');
    if (!pag) return null;
    var cur = parseInt(txt(pag.querySelector('.active')), 10);
    if (isNaN(cur)) cur = 1;
    var max = cur;
    Array.prototype.forEach.call(pag.querySelectorAll('a.page-link'), function (a) {
      var n = parseInt(txt(a), 10);
      if (!isNaN(n) && n > max) max = n;
      var p = a.getAttribute('data-parameters') || '';
      var m = p.match(/from[^:]*:(\d+)/);
      if (m && +m[1] > max) max = +m[1];
    });
    if (max <= cur) return null;
    var path = location.pathname.replace(/\/\d+\/?$/, '/');
    if (!/\/$/.test(path)) path += '/';
    return location.origin + path + (cur + 1) + '/' + location.search.replace(/[?&](mode|function|block_id|from)=[^&]*/g, '').replace(/^&/, '?');
  }
  return { abs: abs, txt: txt, img: img, challenge: challenge, videos: videos, nextPage: nextPage, cleanVideoUrl: cleanVideoUrl };
})();
