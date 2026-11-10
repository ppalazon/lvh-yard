// Read the state just before a swap, so toggles made while a request runs are kept.
let openDetails = new Map();

document.addEventListener("htmx:before:swap", () => {
  openDetails = new Map(
    Array.from(
      document.querySelectorAll("details[data-preserve-open]"),
      (box) => [box.dataset.preserveOpen, box.open],
    ),
  );
});

document.addEventListener("htmx:after:swap", () => {
  for (const box of document.querySelectorAll("details[data-preserve-open]")) {
    if (openDetails.has(box.dataset.preserveOpen)) {
      box.open = openDetails.get(box.dataset.preserveOpen);
    }
  }
});
