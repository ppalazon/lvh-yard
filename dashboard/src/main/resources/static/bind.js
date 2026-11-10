// The two fields of the bind form are lists that you can also type in.
// A typed value is sent as it is. An option with aria-disabled (a followed target) cannot be picked.
// The port field lists the open ports of the target in the container field.

// A field with a listbox under it. It shows the options that match what you typed, or all of them
// when the field holds one option exactly. onPick runs after a pick.
const combobox = (input, listbox, onPick) => {
  let active = -1;

  const options = () => [...listbox.querySelectorAll('[role="option"]')];
  const visible = () => options().filter((option) => !option.hidden);

  const show = (open) => {
    listbox.hidden = !(open && visible().length > 0);
    input.setAttribute("aria-expanded", String(!listbox.hidden));
    if (listbox.hidden) {
      input.removeAttribute("aria-activedescendant");
    }
  };

  const filter = () => {
    const typed = input.value.trim().toLowerCase();
    const all = options();
    const exact = all.some(
      (option) => option.dataset.value.toLowerCase() === typed,
    );
    for (const option of all) {
      option.hidden =
        typed !== "" &&
        !exact &&
        !option.dataset.value.toLowerCase().includes(typed);
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

  const mark = (index) => {
    const shown = visible();
    options().forEach((option) =>
      option.setAttribute("aria-selected", "false"),
    );
    active = Math.max(-1, Math.min(index, shown.length - 1));
    if (active >= 0) {
      shown[active].setAttribute("aria-selected", "true");
      shown[active].scrollIntoView({ block: "nearest" });
      input.setAttribute("aria-activedescendant", shown[active].id);
    } else {
      input.removeAttribute("aria-activedescendant");
    }
  };

  // Each option has an id, so aria-activedescendant on the field can name the marked option.
  const refresh = () => {
    options().forEach((option, index) => {
      option.id = `${listbox.id}-${index}`;
    });
    filter();
    mark(-1);
  };

  const pick = (option) => {
    if (!option || option.getAttribute("aria-disabled") === "true") {
      return;
    }
    input.value = option.dataset.value;
    show(false);
    onPick();
  };

  input.addEventListener("focus", () => {
    filter();
    show(true);
  });
  input.addEventListener("input", () => {
    filter();
    mark(-1);
    show(true);
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

  refresh();
  return { refresh };
};

const portOption = (entry) => {
  const [port, hostPorts] = entry.split(":");
  const option = document.createElement("li");
  option.setAttribute("role", "option");
  option.className =
    "flex cursor-default justify-between gap-2 rounded-sm px-2 py-1.5 text-sm aria-selected:bg-accent";
  option.dataset.value = port;
  const number = document.createElement("span");
  number.textContent = port;
  const detail = document.createElement("span");
  detail.className = "text-muted-foreground";
  detail.textContent = hostPorts
    ? `published on ${hostPorts.split(",").join(", ")}`
    : "not published";
  option.append(number, detail);
  return option;
};

for (const form of document.querySelectorAll("[data-bind-form]")) {
  const target = form.querySelector("#target");
  const targets = form.querySelector("#target-options");
  const port = form.querySelector("#containerPort");
  const ports = form.querySelector("#containerPort-options");

  const portCombobox = combobox(port, ports, () => {});

  // The ports of the target in the field. A target that is not in the list has none.
  const fillPorts = () => {
    const picked = [...targets.querySelectorAll('[role="option"]')].find(
      (option) => option.dataset.value === target.value.trim(),
    );
    ports.replaceChildren(
      ...(picked?.dataset.ports ?? "")
        .split(" ")
        .filter(Boolean)
        .map(portOption),
    );
    portCombobox.refresh();
  };

  combobox(target, targets, () => {
    fillPorts();
    port.focus();
  });
  target.addEventListener("input", fillPorts);
  fillPorts();
}
