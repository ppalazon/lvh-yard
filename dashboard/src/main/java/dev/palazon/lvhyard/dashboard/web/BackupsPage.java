package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.Backup;
import dev.palazon.lvhyard.dashboard.backups.BackupFile;
import dev.palazon.lvhyard.dashboard.backups.BackupProblem;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tr;

final class BackupsPage {

  static final String PATH = "/backups";

  static final String NEW_PATH = PATH + "/new";

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx");

  private BackupsPage() {}

  static String downloadPath(String backup) {
    return PATH + "/" + backup + "/download";
  }

  static String deletePath(String backup) {
    return PATH + "/" + backup + "/delete";
  }

  static String render(Workstation workstation, List<BackupFile> backups) {
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
                        + ", in the backups folder of its Workstation Folder. They hold basic-auth"
                        + " hashes, so keep them private.")
                .__()
                .form()
                .addAttr("method", "post")
                .addAttr("action", NEW_PATH)
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn w-fit")
                .text("New Backup")
                .__()
                .__()
                .of(content -> list(content, backups)));
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
                .text("Delete " + backup + " from the backups folder? You cannot undo this.")
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
        .of(cells -> actions(cells, file.name()))
        .__();
  }

  /** Room for more actions on a row, such as an import, goes before Download. */
  private static void actions(Tr<?> row, String backup) {
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex justify-end gap-2")
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
