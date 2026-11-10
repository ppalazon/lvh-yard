// The container field of the bind form: a list that you can also type in.
// A typed value is sent as it is. An option with aria-disabled (a followed target) cannot be picked.
// The port field lists the Container Ports of the pick.
for (const form of document.querySelectorAll("[data-bind-form]")) {
  const input = form.querySelector("#target");
  const listbox = form.querySelector("#target-options");
  const ports = form.querySelector("#containerPort-options");
  const options = [...listbox.querySelectorAll('[role="option"]')];
  let active = -1;

  const visible = () => options.filter((option) => !option.hidden);

  // Each option has an id, so aria-activedescendant on the field can name the marked option.
  options.forEach((option, index) => {
    option.id = `${listbox.id}-${index}`;
  });

  const show = (open) => {
    const hasOptions = visible().length > 0;
    listbox.hidden = !(open && hasOptions);
    input.setAttribute("aria-expanded", String(!listbox.hidden));
    if (listbox.hidden) {
      input.removeAttribute("aria-activedescendant");
    }
  };

  const filter = () => {
    const typed = input.value.trim().toLowerCase();
    for (const option of options) {
      option.hidden =
        typed !== "" && !option.dataset.value.toLowerCase().includes(typed);
    }
    // A heading stays only while one of its options shows.
    for (const heading of listbox.querySelectorAll('[role="presentation"]')) {
      let next = heading.nextElementSibling;
      let any = false;
      while (next && next.getAttribute("role") === "option") {
        any ||= !next.hidden;
        next = next.nextElementSibling;
      }
      heading.hidden = !any;
    }
  };

  const fillPorts = () => {
    const picked = options.find(
      (option) => option.dataset.value === input.value.trim(),
    );
    ports.replaceChildren();
    for (const port of (picked?.dataset.ports ?? "")
      .split(" ")
      .filter(Boolean)) {
      const option = document.createElement("option");
      option.value = port;
      ports.append(option);
    }
  };

  const mark = (index) => {
    const shown = visible();
    options.forEach((option) => option.setAttribute("aria-selected", "false"));
    active = Math.max(-1, Math.min(index, shown.length - 1));
    if (active >= 0) {
      shown[active].setAttribute("aria-selected", "true");
      shown[active].scrollIntoView({ block: "nearest" });
      input.setAttribute("aria-activedescendant", shown[active].id);
    } else {
      input.removeAttribute("aria-activedescendant");
    }
  };

  const pick = (option) => {
    if (!option || option.getAttribute("aria-disabled") === "true") {
      return;
    }
    input.value = option.dataset.value;
    show(false);
    fillPorts();
    form.querySelector("#containerPort").focus();
  };

  input.addEventListener("focus", () => {
    filter();
    show(true);
  });
  input.addEventListener("input", () => {
    filter();
    mark(-1);
    show(true);
    fillPorts();
  });
  input.addEventListener("blur", () => show(false));
  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      show(true);
      mark(active + (event.key === "ArrowDown" ? 1 : -1));
    } else if (event.key === "Enter" && !listbox.hidden && active >= 0) {
      event.preventDefault();
      pick(visible()[active]);
    } else if (event.key === "Escape") {
      mark(-1);
      show(false);
    }
  });
  // mousedown comes before the blur of the field, so the pick is not lost.
  listbox.addEventListener("mousedown", (event) => {
    event.preventDefault();
    pick(event.target.closest('[role="option"]'));
  });
}
