// A switch sends its form as soon as it flips, so it needs no submit button.
document.addEventListener("change", (event) => {
  const field = event.target;
  if (
    field instanceof HTMLInputElement &&
    field.matches("form input[role=switch]")
  ) {
    field.form.requestSubmit();
  }
});
