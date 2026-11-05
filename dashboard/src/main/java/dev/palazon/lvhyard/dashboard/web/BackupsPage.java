package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.Backup;
import dev.palazon.lvhyard.dashboard.backups.BackupFile;
import dev.palazon.lvhyard.dashboard.backups.BackupProblem;
import dev.palazon.lvhyard.dashboard.backups.ImportFailedException;
import dev.palazon.lvhyard.dashboard.backups.ImportMode;
import dev.palazon.lvhyard.dashboard.backups.ImportResult;
import dev.palazon.lvhyard.dashboard.backups.PlanEntry;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tr;

final class BackupsPage {

  static final String PATH = "/backups";

  static final String NEW_PATH = PATH + "/new";

  static final String UPLOAD_PATH = PATH + "/upload";

  private static final String UPLOAD_DIALOG = "upload-backup";

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

  /**
   * @param uploadProblems why the last upload was refused; empty when none was
   */
  static String render(
      Workstation workstation, List<BackupFile> backups, List<String> uploadProblems) {
    return Layout.page(
        "Backups",
        workstation.name(),
        Layout.Section.BACKUPS,
        main ->
            main.h1()
                .text("Backups")
                .__()
                .p()
                .text(
                    "Copies of the setup of "
                        + workstation.name()
                        + ", in the Backups folder of its Workstation Folder. They hold basic-auth"
                        + " hashes, so keep them private.")
                .__()
                .div()
                .attrClass("flex flex-wrap items-end gap-2")
                .form()
                .addAttr("method", "post")
                .addAttr("action", NEW_PATH)
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn w-fit")
                .text("New Backup")
                .__()
                .__()
                .of(buttons -> Dialogs.openButton(buttons, UPLOAD_DIALOG, "Upload a Backup"))
                .__()
                .of(content -> list(content, backups))
                .of(content ->
                    Dialogs.modal(
                        content,
                        UPLOAD_DIALOG,
                        "Upload a Backup",
                        !uploadProblems.isEmpty(),
                        body -> uploadForm(body, uploadProblems))));
  }

  /** A browser does not keep a chosen file, so after a refusal the file is chosen again. */
  private static void uploadForm(Div<?> body, List<String> problems) {
    body.form()
        .attrClass("form flex flex-col gap-6")
        .addAttr("method", "post")
        .addAttr("action", UPLOAD_PATH)
        .addAttr("enctype", "multipart/form-data")
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
        .of(field -> {
          if (problems.isEmpty()) {
            return;
          }
          field
              .div()
              .attrClass("alert")
              .addAttr("data-variant", "destructive")
              .addAttr("role", "alert")
              .h3()
              .text("Not uploaded")
              .__()
              .section()
              .ul()
              .attrClass("list-disc pl-5")
              .of(list -> problems.forEach(problem -> list.li().text(problem).__()))
              .__()
              .__()
              .__();
        })
        .__()
        .of(form -> Dialogs.actions(form, "Upload"))
        .__();
  }

  /** The confirmation step before a delete, so a click alone deletes nothing. */
  static String confirmDelete(Workstation workstation, String backup) {
    return Layout.page(
        "Delete " + backup,
        workstation.name(),
        Layout.Section.BACKUPS,
        main ->
            main.h1()
                .text("Delete a Backup")
                .__()
                .p()
                .text("Delete " + backup + " from the Backups folder? You cannot undo this.")
                .__()
                .form()
                .attrClass("flex gap-2")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(deletePath(backup)))
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
                .__());
  }

  /** Nothing when {@code problems} is empty. */
  private static void alert(Main<?> main, String title, List<String> problems) {
    if (problems.isEmpty()) {
      return;
    }
    main.div()
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
        "Import " + backup,
        workstation.name(),
        Layout.Section.BACKUPS,
        main ->
            main.h1()
                .text("Import a Backup")
                .__()
                .p()
                .text("Import " + backup + " into " + workstation.name() + ".")
                .__()
                .of(content -> alert(content, alertTitle, alert))
                .of(content -> modeForm(content, backup, mode == null ? ImportMode.ADD : mode))
                .of(content -> {
                  if (plan != null) {
                    confirm(content, backup, plan, fingerprint);
                  }
                }));
  }

  /** A write failed halfway: what the import applied stays, and the safety Backup undoes it. */
  static String importFailed(Workstation workstation, String backup, ImportFailedException failed) {
    return Layout.page(
        "Import of " + backup + " stopped",
        workstation.name(),
        Layout.Section.BACKUPS,
        main ->
            main.h1()
                .text("Import of " + backup + " stopped")
                .__()
                .of(content -> alert(content, "Import stopped", List.of(failed.getMessage())))
                .p()
                .text(
                    "Before the import, the dashboard saved the setup in "
                        + failed.safetyBackup()
                        + ". To go back, import that Backup.")
                .__()
                .h2()
                .text("What the import applied")
                .__()
                .of(content -> {
                  if (failed.applied().isEmpty()) {
                    content.p().text("Nothing: it stopped at the first change.").__();
                  } else {
                    entries(content, failed.applied());
                  }
                })
                .a()
                .attrHref(PATH)
                .attrClass("btn w-fit")
                .text("Back to Backups")
                .__());
  }

  /** The result of a confirmed import. */
  static String imported(Workstation workstation, String backup, ImportResult result) {
    return Layout.page(
        "Imported " + backup,
        workstation.name(),
        Layout.Section.BACKUPS,
        main ->
            main.h1()
                .text("Imported " + backup)
                .__()
                .p()
                .text(
                    result.safetyBackup() == null
                        ? "Nothing changed, so the dashboard wrote no safety Backup."
                        : "Before the import, the dashboard saved the setup in "
                            + result.safetyBackup()
                            + ". To go back, import that Backup.")
                .__()
                .h2()
                .text("What the import did")
                .__()
                .of(content -> entries(content, result))
                .a()
                .attrHref(PATH)
                .attrClass("btn w-fit")
                .text("Back to Backups")
                .__());
  }

  private static void modeForm(Main<?> main, String backup, ImportMode chosen) {
    main.form()
        .attrClass("flex flex-col gap-4")
        .addAttr("method", "get")
        .addAttr("action", Layout.attr(importPath(backup)))
        .div()
        .attrClass("flex flex-col gap-3")
        .addAttr("role", "radiogroup")
        .addAttr("aria-label", "Mode")
        .of(group ->
            mode(group, ImportMode.ADD, "Add: add what is new and skip what exists.", chosen))
        .of(group ->
            mode(
                group,
                ImportMode.UPDATE,
                "Update: add what is new and overwrite what exists with the file.",
                chosen))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn w-fit")
        .addAttr("data-variant", "outline")
        .text("Show the plan")
        .__()
        .__();
  }

  private static void mode(Div<?> group, ImportMode mode, String label, ImportMode chosen) {
    group
        .label()
        .attrClass("label gap-3")
        .input()
        .attrType(EnumTypeInputType.RADIO)
        .attrClass("input")
        .attrName("mode")
        .attrValue(mode.value())
        .of(input -> {
          if (mode == chosen) {
            input.attrChecked(true);
          }
        })
        .__()
        .text(label)
        .__();
  }

  private static void confirm(Main<?> main, String backup, ImportResult plan, String fingerprint) {
    main.h2()
        .text("Plan in " + plan.mode().value() + " mode")
        .__()
        .p()
        .text("Nothing changes until you import.")
        .__()
        .of(content -> entries(content, plan))
        .form()
        .attrClass("flex gap-2")
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
        .__();
  }

  private static void entries(Main<?> main, ImportResult result) {
    if (result.entries().isEmpty()) {
      main.p().text("The Backup is empty.").__();
      return;
    }
    entries(main, result.entries());
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

  private static void list(Main<?> main, List<BackupFile> backups) {
    if (backups.isEmpty()) {
      main.p().text("No Backups yet.").__();
      return;
    }
    main.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
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
        .__()
        .__();
  }

  private static void row(Tr<?> row, BackupFile file) {
    row.attrId(Layout.attr("backup-" + file.name()))
        .td()
        .attrClass("align-top font-mono")
        .text(file.name())
        .__()
        .of(cells -> {
          if (file.backup() == null) {
            cells
                .td()
                .attrClass("align-top text-destructive")
                .attrColspan(2)
                .ul()
                .of(list ->
                    file.problems().stream()
                        .map(BackupProblem::text)
                        .forEach(problem -> list.li().text(problem).__()))
                .__()
                .__();
          } else {
            cells
                .td()
                .attrClass("align-top")
                .text(exported(file.backup()))
                .__()
                .td()
                .attrClass("align-top")
                .text(holds(file.backup()))
                .__();
          }
        })
        .of(cells -> actions(cells, file))
        .__();
  }

  /** Only a file that is a Backup can be imported. */
  private static void actions(Tr<?> row, BackupFile file) {
    String backup = file.name();
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex justify-end gap-2")
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
        .addAttr("data-variant", "outline")
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
        count(backup.aliases().size(), "Alias"),
        count(backup.routeProfiles().size(), "Route Profile"));
  }

  private static String count(int count, String thing) {
    String plural = thing.endsWith("s") ? thing + "es" : thing + "s";
    return count + " " + (count == 1 ? thing : plural);
  }
}
