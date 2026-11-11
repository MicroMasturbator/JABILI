(function () {
  var r = { challenge: NB.challenge(), title: document.title,
            items: NB.videos(), next: NB.nextPage(), categories: [], tags: [] };
  // /categories/ 页：分类卡片，名字在 h4，数量在 .label
  var seen = {};
  Array.prototype.forEach.call(document.querySelectorAll('a[href*="/categories/"]'), function (a) {
    var h4 = a.querySelector('h4') || (a.closest('.video-img-box, .img-box, div') || a).querySelector('h4');
    if (!h4) return;
    var href = NB.abs(a.getAttribute('href'));
    if (!/\/categories\/[^\/?#]+\/?$/.test(href) || seen[href]) return; seen[href] = 1;
    var box = a.closest('.video-img-box') || a.parentElement || a;
    var count = NB.txt(box.querySelector('.label'));
    r.categories.push({ name: NB.txt(h4), url: href, cover: NB.img(box.querySelector('img')), group: count });
  });
  // 标签在每页都有的菜单 nav.app-nav 里：.title-box h2 是分组名，后面跟 a.tag
  var nav = document.querySelector('nav.app-nav');
  if (nav) {
    var group = '';
    var tseen = {};
    Array.prototype.forEach.call(nav.querySelectorAll('.title-box h2, a.tag'), function (el) {
      if (el.tagName === 'H2') { group = NB.txt(el); return; }
      var href = NB.abs(el.getAttribute('href'));
      if (tseen[href]) return; tseen[href] = 1;
      r.tags.push({ name: NB.txt(el), url: href, cover: '', group: group });
    });
  }
  r.loggedIn = !!((window.pageContext && pageContext.userId) || document.querySelector('a[href*="logout"]'));
  r.path = location.pathname;
  r.empty = r.items.length === 0 && /暫無相關內容|暂无相关内容/.test(document.body ? document.body.innerText : '');
  return JSON.stringify(r);
})();
