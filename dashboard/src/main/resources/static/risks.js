// The Service page swaps its region every 5 seconds, and the new risks box comes closed.
// The open state lives only in this variable, so it lasts only while the page is open.
let risksOpen = false;

// toggle does not bubble, so it is caught on the way down.
document.addEventListener(
  "toggle",
  (event) => {
    if (event.target.id === "risks") {
      risksOpen = event.target.open;
    }
  },
  true,
);

document.addEventListener("htmx:after:swap", () => {
  const risks = document.getElementById("risks");
  if (risks) {
    risks.open = risksOpen;
  }
});
