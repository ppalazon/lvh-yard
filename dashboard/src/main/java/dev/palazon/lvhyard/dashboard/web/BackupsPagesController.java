package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.BackupExport;
import dev.palazon.lvhyard.dashboard.backups.BackupFormat;
import dev.palazon.lvhyard.dashboard.backups.BackupsFolder;
import dev.palazon.lvhyard.dashboard.backups.NoBackupException;
import java.net.URI;
import java.util.function.Supplier;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Backups page. It only translates to views. Nothing here deletes a Backup unless a person
 * confirms it.
 */
@Controller
class BackupsPagesController {

  private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  private final Workstation workstation;

  private final BackupsFolder folder;

  private final BackupExport export;

  BackupsPagesController(Workstation workstation, BackupsFolder folder, BackupExport export) {
    this.workstation = workstation;
    this.folder = folder;
    this.export = export;
  }

  @GetMapping(path = BackupsPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String backups() {
    return BackupsPage.render(workstation, folder.list());
  }

  @PostMapping(path = BackupsPage.NEW_PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> create() {
    folder.writeNew("", BackupFormat.write(export.backup()));
    return seeOther(BackupsPage.PATH);
  }

  @GetMapping(path = BackupsPage.PATH + "/{backup}/download")
  ResponseEntity<String> download(@PathVariable String backup) {
    String text = found(() -> folder.text(backup));
    return ResponseEntity.ok()
        .contentType(YAML)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(backup).build().toString())
        .body(text);
  }

  @GetMapping(path = BackupsPage.PATH + "/{backup}/delete", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String confirmDelete(@PathVariable String backup) {
    found(() -> folder.read(backup));
    return BackupsPage.confirmDelete(workstation, backup);
  }

  @PostMapping(path = BackupsPage.PATH + "/{backup}/delete", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> delete(@PathVariable String backup) {
    found(() -> {
      folder.delete(backup);
      return null;
    });
    return seeOther(BackupsPage.PATH);
  }

  private static <T> T found(Supplier<T> read) {
    try {
      return read.get();
    } catch (NoBackupException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
  }

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
