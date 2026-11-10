package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.Backup;
import dev.palazon.lvhyard.dashboard.backups.BackupFile;
import dev.palazon.lvhyard.dashboard.backups.ImportFailedException;
import dev.palazon.lvhyard.dashboard.backups.ImportMode;
import dev.palazon.lvhyard.dashboard.backups.ImportResult;
import dev.palazon.lvhyard.dashboard.backups.PlanEntry;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tr;

final class BackupsPage {

  static final String PATH = "/backups";

  static final String NEW_PATH = PATH + "/new";

  static final String UPLOAD_PATH = PATH + "/upload";

  private static final FormPage.Spec UPLOAD =
      new FormPage.Spec(
          Layout.Section.BACKUPS,
          "Upload a Backup",
          "The file joins the Backups folder. Nothing is imported yet.",
          UPLOAD_PATH,
          UPLOAD_PATH,
          "Upload");

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx");

  private BackupsPage() {}

  static String downloadPath(String backup) {
    return PATH + "/" + backup + "/download";
  }

  static String deletePath(String backup) {
    return PATH + "/" + backup + "/delete";
  }

  static String importPath(String backup) {
    return PATH + "/" + backup + "/import";
  }

  static String render(Workstation workstation, List<BackupFile> backups) {
    return Layout.page(
        "Backups",
        workstation.name(),
        Layout.Section.BACKUPS,
        main ->
            main.div()
                .attrClass("backups-page flex min-w-0 flex-col gap-6")
                .div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text("Backups")
                .__()
                .div()
                .attrClass("flex flex-wrap items-center gap-2")
                .form()
                .addAttr("method", "post")
                .addAttr("action", NEW_PATH)
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .text("New Backup")
                .__()
                .__()
                .of(buttons -> FormPage.openLink(buttons, UPLOAD))
                .__()
                .__()
                .p()
                .attrClass("text-muted-foreground")
                .text(
                    "Copies of the setup of "
                        + workstation.name()
                        + ", in the Backups folder of its Workstation Folder. They hold basic-auth"
                        + " hashes, so keep them private.")
                .__()
                .of(content -> list(content, backups))
                .__());
  }

  /**
   * @param problems why the last upload was refused; empty when none was
   */
  static String renderUpload(Workstation workstation, List<String> problems) {
    return FormPage.render(
        workstation,
        UPLOAD,
        form -> form.addAttr("enctype", "multipart/form-data"),
        fields -> uploadField(fields, problems));
  }

  /** A browser does not keep a chosen file, so after a refusal the file is chosen again. */
  private static void uploadField(FlowContent<?, ?> fields, List<String> problems) {
    fields
        .div()
        .attrClass("field")
        .label()
        .attrFor("file")
        .text("Backup file")
        .__()
        .input()
        .attrType(EnumTypeInputType.FILE)
        .attrClass("input")
        .attrId("file")
        .attrName("file")
        .attrAccept(".yaml,.yml")
        .addAttr("required", "")
        .of(input -> {
          if (!problems.isEmpty()) {
            input.addAttr("aria-invalid", "true");
          }
        })
        .__()
        .of(field -> alert(field, "Not uploaded", problems))
        .__();
  }

  /** The confirmation step before a delete, so a click alone deletes nothing. */
  static String confirmDelete(Workstation workstation, String backup) {
    return Layout.page(
        "Delete " + backup,
        workstation.name(),
        Layout.Section.BACKUPS,
        main -> {
          header(
              main,
              "Delete a Backup",
              "This removes the file from the Backups folder. You cannot undo this.");
          main.form()
              .attrClass("card flex max-w-xl flex-col gap-6 p-6")
              .addAttr("method", "post")
              .addAttr("action", Layout.attr(deletePath(backup)))
              .p()
              .attrClass("font-mono break-all")
              .text(backup)
              .__()
              .div()
              .attrClass("flex gap-3")
              .button()
              .attrType(EnumTypeButtonType.SUBMIT)
              .attrClass("btn")
              .addAttr("data-variant", "destructive")
              .text("Delete")
              .__()
              .a()
              .attrHref(PATH)
              .attrClass("btn")
              .addAttr("data-variant", "outline")
              .text("Cancel")
              .__()
              .__()
              .__();
        });
  }

  /** Nothing when {@code problems} is empty. */
  private static void alert(FlowContent<?, ?> parent, String title, List<String> problems) {
    if (problems.isEmpty()) {
      return;
    }
    parent
        .div()
        .attrClass("alert")
        .addAttr("data-variant", "destructive")
        .addAttr("role", "alert")
        .h3()
        .text(title)
        .__()
        .section()
        .ul()
        .attrClass("list-disc pl-5")
        .of(list -> problems.forEach(problem -> list.li().text(problem).__()))
        .__()
        .__()
        .__();
  }

  /**
   * The first step of an import. Without a mode it asks for one. With a plan it shows the plan and
   * the button that imports.
   *
   * @param mode the chosen mode, or {@code null}
   * @param plan the dry run in {@code mode}, or {@code null}
   * @param fingerprint of {@code plan}, which the confirmation sends back; {@code null} without a
   *     plan
   * @param problems why the file cannot be imported; empty when it can
   */
  static String importPlan(
      Workstation workstation,
      String backup,
      ImportMode mode,
      ImportResult plan,
      String fingerprint,
      List<String> problems) {
    return importPage(workstation, backup, mode, plan, fingerprint, "Cannot import", problems);
  }

  /** The plan changed after the person saw it, so it asks again with the new plan. */
  static String planChanged(
      Workstation workstation, String backup, ImportResult plan, String fingerprint) {
    return importPage(
        workstation,
        backup,
        plan.mode(),
        plan,
        fingerprint,
        "Not imported",
        List.of(
            "The plan changed after you saw it, so nothing changed. Check the new plan, then"
                + " import again."));
  }

  private static String importPage(
      Workstation workstation,
      String backup,
      ImportMode mode,
      ImportResult plan,
      String fingerprint,
      String alertTitle,
      List<String> alert) {
    return Layout.page(
        "Plan a Backup importation: " + backup,
        workstation.name(),
        Layout.Section.BACKUPS,
        main -> {
          header(
              main,
              "Plan a Backup importation",
              "Plan the import of " + backup + " into " + workstation.name() + ".");
          alert(main, alertTitle, alert);
          modeForm(main, backup, mode == null ? ImportMode.ADD : mode, plan == null);
          if (plan != null) {
            confirm(main, backup, plan, fingerprint);
          }
        });
  }

  /**
   * @param description or {@code null}
   */
  private static void header(Main<?> main, String title, String description) {
    main.div()
        .attrClass("flex max-w-3xl flex-col gap-2")
        .h1()
        .text(title)
        .__()
        .of(header -> {
          if (description != null) {
            header.p().attrClass("text-muted-foreground").text(description).__();
          }
        })
        .__();
  }

  /** A write failed halfway: what the import applied stays, and the safety Backup undoes it. */
  static String importFailed(Workstation workstation, String backup, ImportFailedException failed) {
    return Layout.page(
        "Import of " + backup + " stopped",
        workstation.name(),
        Layout.Section.BACKUPS,
        main -> {
          header(main, "Import of " + backup + " stopped", null);
          alert(main, "Import stopped", List.of(failed.getMessage()));
          main.p()
              .text(
                  "Before the import, the dashboard saved the setup in "
                      + failed.safetyBackup()
                      + ". To go back, import that Backup.")
              .__();
          main.h2().text("What the import applied").__();
          if (failed.applied().isEmpty()) {
            main.p().text("Nothing: it stopped at the first change.").__();
          } else {
            entries(main, failed.applied());
          }
          backLink(main);
        });
  }

  private static void backLink(Main<?> main) {
    main.a().attrHref(PATH).attrClass("btn w-fit").text("Back to Backups").__();
  }

  /** The result of a confirmed import. */
  static String imported(Workstation workstation, String backup, ImportResult result) {
    return Layout.page(
        "Imported " + backup,
        workstation.name(),
        Layout.Section.BACKUPS,
        main -> {
          header(
              main,
              "Imported " + backup,
              result.safetyBackup() == null
                  ? "Nothing changed, so the dashboard wrote no safety Backup."
                  : "Before the import, the dashboard saved the setup in "
                      + result.safetyBackup()
                      + ". To go back, import that Backup.");
          main.h2().text("What the import did").__();
          entryPanels(main, result);
          backLink(main);
        });
  }

  private static void modeForm(Main<?> main, String backup, ImportMode chosen, boolean primary) {
    main.form()
        .attrClass("card flex max-w-xl flex-col gap-6 p-6")
        .addAttr("method", "get")
        .addAttr("action", Layout.attr(importPath(backup)))
        .div()
        .attrClass("flex flex-col gap-4")
        .addAttr("role", "radiogroup")
        .addAttr("aria-label", "Mode")
        .of(group ->
            mode(group, ImportMode.ADD, "Add", "Add what is new and skip what exists.", chosen))
        .of(group ->
            mode(
                group,
                ImportMode.UPDATE,
                "Update",
                "Add what is new and overwrite what exists with the file.",
                chosen))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn w-fit")
        .of(button -> {
          if (!primary) {
            button.addAttr("data-variant", "outline");
          }
        })
        .text("Show the plan")
        .__()
        .__();
  }

  private static void mode(
      Div<?> group, ImportMode mode, String name, String help, ImportMode chosen) {
    group
        .label()
        .attrClass("flex cursor-pointer items-start gap-3")
        .input()
        .attrType(EnumTypeInputType.RADIO)
        .attrClass("input mt-1")
        .attrName("mode")
        .attrValue(mode.value())
        .of(input -> {
          if (mode == chosen) {
            input.attrChecked(true);
          }
        })
        .__()
        .span()
        .attrClass("flex flex-col")
        .span()
        .attrClass("font-medium")
        .text(name)
        .__()
        .span()
        .attrClass("text-muted-foreground text-sm")
        .text(help)
        .__()
        .__()
        .__();
  }

  private static void confirm(Main<?> main, String backup, ImportResult plan, String fingerprint) {
    main.div()
        .attrClass("flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between")
        .div()
        .attrClass("flex min-w-0 flex-col gap-2")
        .h2()
        .text("Plan in " + plan.mode().value() + " mode")
        .__()
        .p()
        .text("Nothing changes until you import.")
        .__()
        .__()
        .form()
        .attrClass("flex shrink-0 gap-2")
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(importPath(backup)))
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("mode")
        .attrValue(plan.mode().value())
        .__()
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("plan")
        .attrValue(fingerprint)
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .text("Import")
        .__()
        .a()
        .attrHref(PATH)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .text("Cancel")
        .__()
        .__()
        .__()
        .of(content -> entryPanels(content, plan));
  }

  private static void entryPanels(Main<?> main, ImportResult plan) {
    int skipped = 0;
    for (PlanEntry entry : plan.entries()) {
      if (entry.action() == PlanEntry.Action.SKIP) {
        skipped++;
      }
    }
    List<String> changes = new ArrayList<>(plan.entries().size() - skipped);
    List<String> skippedEntries = new ArrayList<>(skipped);
    for (PlanEntry entry : plan.entries()) {
      (entry.action() == PlanEntry.Action.SKIP ? skippedEntries : changes).add(entry(entry));
    }
    changes.sort(String.CASE_INSENSITIVE_ORDER);
    skippedEntries.sort(String.CASE_INSENSITIVE_ORDER);
    main.div()
        .attrId("plan")
        .attrClass("grid min-w-0 grid-cols-1 gap-4 wrap-anywhere md:grid-cols-2")
        .of(content -> entryPanel(content, changes, false, plan.dryRun()))
        .of(content -> entryPanel(content, skippedEntries, true, plan.dryRun()))
        .__();
  }

  private static void entryPanel(
      Div<?> parent, List<String> entries, boolean skipped, boolean planning) {
    String heading = skipped ? "plan-skipped-heading" : "plan-changes-heading";
    String label = skipped ? "Skipped" : planning ? "What's going to change" : "Changes";
    parent
        .section()
        .attrClass(
            skipped
                ? "bg-secondary flex min-w-0 flex-col gap-4 rounded-lg border p-5"
                : "bg-background flex min-w-0 flex-col gap-4 rounded-lg border p-5")
        .addAttr("aria-labelledby", heading)
        .h3()
        .attrId(heading)
        .attrClass("text-lg font-semibold")
        .text(label + " (" + entries.size() + ")" + (planning && !skipped ? "?" : ""))
        .__()
        .of(panel -> {
          if (entries.isEmpty()) {
            panel.p().text(skipped ? "Nothing is skipped." : "No changes.").__();
            return;
          }
          panel
              .ul()
              .attrClass("flex flex-col gap-3 list-disc pl-5")
              .of(list -> entries.forEach(entry -> list.li().text(entry).__()))
              .__();
        })
        .__();
  }

  private static void entries(Main<?> main, List<PlanEntry> entries) {
    main.ul()
        .attrId("plan")
        .attrClass("list-disc pl-5")
        .of(list -> entries.forEach(entry -> list.li().text(entry(entry)).__()))
        .__();
  }

  /** Such as {@code Skip Static Instance 10.1.2.3:3000 of grafana: It is the same already.} */
  private static String entry(PlanEntry entry) {
    String action = entry.action().value();
    return Character.toUpperCase(action.charAt(0))
        + action.substring(1)
        + " "
        + entry.what()
        + (entry.reason() == null ? "" : ": " + entry.reason());
  }

  private static void list(Div<?> main, List<BackupFile> backups) {
    if (backups.isEmpty()) {
      EmptyState.render(
          main,
          "No Backups yet.",
          "Click New Backup to save the setup of this Workstation now. To add a file from"
              + " another place, click Upload a Backup.");
      return;
    }
    main.table()
        .attrClass("table backups-table")
        .thead()
        .tr()
        .th()
        .text("Name")
        .__()
        .th()
        .text("Exported")
        .__()
        .th()
        .text("Holds")
        .__()
        .th()
        .of(cell -> cell.span().attrClass("sr-only").text("Actions").__())
        .__()
        .__()
        .__()
        .tbody()
        .of(body -> backups.forEach(backup -> row(body.tr(), backup)))
        .__()
        .__();
  }

  private static void row(Tr<?> row, BackupFile file) {
    row.attrId(Layout.attr("backup-" + file.name()))
        .td()
        .attrClass("align-top")
        .div()
        .attrClass("flex min-w-0 flex-col gap-2")
        .span()
        .attrClass("font-mono font-semibold")
        .text(file.name())
        .__()
        .of(content -> {
          if (file.backup() != null) {
            return;
          }
          content
              .span()
              .attrClass("badge w-fit")
              .addAttr("data-variant", "destructive")
              .text("Cannot import")
              .__();
          content
              .custom("details")
              .attrClass("backup-problems")
              .custom("summary")
              .addAttr("aria-label", Layout.attr("Problems in " + file.name()))
              .text("Problems")
              .__()
              .ul()
              .attrClass("text-destructive flex flex-col gap-2 pb-2 text-sm")
              .of(list ->
                  file.problems()
                      .forEach(problem ->
                          list.li().attrClass("whitespace-pre-wrap").text(problem.text()).__()))
              .__()
              .__();
        })
        .__()
        .__()
        .td()
        .attrClass("align-top")
        .addAttr("data-label", "Exported")
        .text(file.backup() == null ? "" : exported(file.backup()))
        .__()
        .td()
        .attrClass("align-top")
        .addAttr("data-label", "Holds")
        .text(file.backup() == null ? "" : holds(file.backup()))
        .__()
        .of(cells -> actions(cells, file))
        .__();
  }

  /** Only a file that is a Backup can be imported. */
  private static void actions(Tr<?> row, BackupFile file) {
    String backup = file.name();
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex flex-wrap gap-2 lg:justify-end")
        .of(buttons -> {
          if (file.backup() != null) {
            buttons
                .a()
                .attrHref(Layout.attr(importPath(backup)))
                .attrClass("btn")
                .addAttr("data-size", "sm")
                .addAttr("aria-label", Layout.attr("Import " + backup))
                .text("Import")
                .__();
          }
        })
        .a()
        .attrHref(Layout.attr(downloadPath(backup)))
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("Download " + backup))
        .text("Download")
        .__()
        .a()
        .attrHref(Layout.attr(deletePath(backup)))
        .attrClass("btn")
        .addAttr("data-variant", "destructive")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("Delete " + backup))
        .text("Delete")
        .__()
        .__()
        .__();
  }

  private static String exported(Backup backup) {
    if (backup.exportedFrom() == null || backup.exportedFrom().at() == null) {
      return "No date";
    }
    return backup.exportedFrom().at().format(DATE);
  }

  /** Such as {@code 2 Services, 1 Alias, 0 Route Profiles}. */
  private static String holds(Backup backup) {
    return String.join(
        ", ",
        count(backup.services().size(), "Service"),
        count(
            backup.services().values().stream().mapToInt(service -> service.aliases().size()).sum(),
            "Alias"),
        count(backup.routeProfiles().size(), "Route Profile"));
  }

  private static String count(int count, String thing) {
    String plural = thing.endsWith("s") ? thing + "es" : thing + "s";
    return count + " " + (count == 1 ? thing : plural);
  }
}
