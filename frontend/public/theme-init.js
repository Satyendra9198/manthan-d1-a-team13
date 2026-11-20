// Applies the persisted / system theme before first paint to avoid a flash.
(function () {
  var theme = null;
  try { theme = localStorage.getItem("rr.theme"); } catch (e) { theme = null; }
  var dark = theme === "dark" || ((theme === null || theme === "system") &&
    window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
  if (dark) document.documentElement.classList.add("dark");
})();
