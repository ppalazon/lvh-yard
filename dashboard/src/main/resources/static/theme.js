// Basecoat's dark theme applies under html.dark. The theme button's choice is kept in this browser; until one is
// made, the theme follows the system setting as it changes. Loaded in the head, so the page is drawn in its theme.
const system = window.matchMedia("(prefers-color-scheme: dark)");
const KEY = "theme";

let choice = null;
try {
  choice = localStorage.getItem(KEY);
} catch {
  // Storage blocked: the choice lasts until the next page.
}

function apply() {
  const dark = (choice ?? (system.matches ? "dark" : "light")) === "dark";
  document.documentElement.classList.toggle("dark", dark);
  document.getElementById("theme-toggle")?.setAttribute("aria-pressed", String(dark));
}

apply();
system.addEventListener("change", apply);
document.addEventListener("DOMContentLoaded", apply);
document.addEventListener("click", (event) => {
  if (!event.target.closest("#theme-toggle")) {
    return;
  }
  choice = document.documentElement.classList.contains("dark") ? "light" : "dark";
  try {
    localStorage.setItem(KEY, choice);
  } catch {
    // Storage blocked: the choice lasts until the next page.
  }
  apply();
});
