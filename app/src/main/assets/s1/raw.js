(function () {
  return JSON.stringify({ challenge: NB.challenge(), body: document.body ? document.body.innerText : '' , path: location.pathname,
                          loggedIn: !!((window.pageContext && pageContext.userId) || document.querySelector('a[href*="logout"]')) });
})();
