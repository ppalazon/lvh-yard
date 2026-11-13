package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.backups.BackupExport;
import cc.lvhy.yard.dashboard.backups.BackupFormat;
import cc.lvhy.yard.dashboard.backups.BackupImport;
import cc.lvhy.yard.dashboard.backups.BackupProblem;
import cc.lvhy.yard.dashboard.backups.BackupsFolder;
import cc.lvhy.yard.dashboard.backups.ImportFailedException;
import cc.lvhy.yard.dashboard.backups.ImportMode;
import cc.lvhy.yard.dashboard.backups.ImportResult;
import cc.lvhy.yard.dashboard.backups.NoBackupException;
import cc.lvhy.yard.dashboard.backups.PlanChangedException;
import cc.lvhy.yard.dashboard.backups.RefusedBackupException;
import cc.lvhy.yard.dashboard.backups.UnknownImportModeException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Backups page. It only translates to views. Nothing here deletes or imports a Backup unless a
 * person confirms it.
 */
@Controller
class BackupsPagesController {

  private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  private final Workstation workstation;

  private final BackupsFolder folder;

  private final BackupExport export;

  private final BackupImport backupImport;

  BackupsPagesController(
      Workstation workstation,
      BackupsFolder folder,
      BackupExport export,
      BackupImport backupImport) {
    this.workstation = workstation;
    this.folder = folder;
    this.export = export;
    this.backupImport = backupImport;
  }

  @GetMapping(path = BackupsPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String backups() {
    return BackupsPage.render(workstation, folder.list());
  }

  @GetMapping(path = BackupsPage.UPLOAD_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String uploadForm() {
    return BackupsPage.renderUpload(workstation, List.of());
  }

  @PostMapping(path = BackupsPage.NEW_PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> create() {
    folder.writeNew("", BackupFormat.write(export.backup()));
    return seeOther(BackupsPage.PATH);
  }

  @PostMapping(
      path = BackupsPage.UPLOAD_PATH,
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> upload(@RequestParam MultipartFile file) throws IOException {
    String uploaded = file.getOriginalFilename();
    String name = uploaded == null ? "" : uploaded;
    String text = new String(file.getBytes(), StandardCharsets.UTF_8);
    String nameProblem = BackupsFolder.nameProblem(name);
    if (nameProblem != null) {
      return uploadRefused(nameProblem);
    }
    try {
      BackupFormat.read(text);
    } catch (RefusedBackupException e) {
      return uploadRefused(texts(e));
    }
    try {
      folder.write(name, text);
    } catch (FileAlreadyExistsException e) {
      return uploadRefused("The Backups folder already has a file " + name + ".");
    }
    return seeOther(BackupsPage.PATH);
  }

  private ResponseEntity<String> uploadRefused(String problem) {
    return uploadRefused(List.of(problem));
  }

  private ResponseEntity<String> uploadRefused(List<String> problems) {
    return html(HttpStatus.BAD_REQUEST, BackupsPage.renderUpload(workstation, problems));
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

  /** The first step of an import: without a mode it asks for one, with a mode it shows the plan. */
  @GetMapping(path = BackupsPage.PATH + "/{backup}/import", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> importPlan(
      @PathVariable String backup, @RequestParam(required = false) String mode) {
    String text = found(() -> folder.text(backup));
    ImportMode chosen = mode == null ? null : mode(mode);
    try {
      if (chosen == null) {
        BackupFormat.read(text);
        return html(
            HttpStatus.OK,
            BackupsPage.importPlan(workstation, backup, null, null, null, List.of()));
      }
      ImportResult plan = backupImport.run(text, chosen, true);
      return html(
          HttpStatus.OK,
          BackupsPage.importPlan(
              workstation, backup, chosen, plan, BackupImport.fingerprint(text, plan), List.of()));
    } catch (RefusedBackupException e) {
      return html(
          HttpStatus.BAD_REQUEST,
          BackupsPage.importPlan(workstation, backup, chosen, null, null, texts(e)));
    }
  }

  /**
   * @param plan the fingerprint of the plan that the person saw; only that plan is imported
   */
  @PostMapping(path = BackupsPage.PATH + "/{backup}/import", produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> importBackup(
      @PathVariable String backup, @RequestParam String mode, @RequestParam String plan) {
    String text = found(() -> folder.text(backup));
    ImportMode chosen = mode(mode);
    try {
      return html(
          HttpStatus.OK,
          BackupsPage.imported(workstation, backup, backupImport.runConfirmed(text, chosen, plan)));
    } catch (PlanChangedException e) {
      return html(
          HttpStatus.CONFLICT,
          BackupsPage.planChanged(
              workstation, backup, e.plan(), BackupImport.fingerprint(text, e.plan())));
    } catch (RefusedBackupException e) {
      return html(
          HttpStatus.BAD_REQUEST,
          BackupsPage.importPlan(workstation, backup, chosen, null, null, texts(e)));
    } catch (ImportFailedException e) {
      return html(
          HttpStatus.INTERNAL_SERVER_ERROR, BackupsPage.importFailed(workstation, backup, e));
    }
  }

  private static ImportMode mode(String mode) {
    try {
      return ImportMode.of(mode);
    } catch (UnknownImportModeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  private static List<String> texts(RefusedBackupException refused) {
    return refused.problems().stream().map(BackupProblem::text).toList();
  }

  private static ResponseEntity<String> html(HttpStatus status, String page) {
    return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(page);
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
