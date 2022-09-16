package eu.rssw.sonar;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.prorefactor.core.JPNode;
import org.prorefactor.core.schema.IDatabase;
import org.prorefactor.core.schema.Schema;
import org.prorefactor.refactor.RefactorSession;
import org.prorefactor.refactor.settings.ProparseSettings;
import org.prorefactor.treeparser.ParseUnit;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.internal.google.common.io.Files;
import org.sonar.plugins.openedge.api.Constants;
import org.sonar.plugins.openedge.api.objects.DatabaseWrapper;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Splitter;

import eu.rssw.antlr.database.DumpFileUtils;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public class GroovyRule {

  @Parameter(names = {"--input"})
  private File inputFile;
  @Parameter(names = {"--groovy"})
  private File groovyFile;
  @Parameter(names = {"--databases"})
  private String databases = "";
  @Parameter(names = {"--aliases"})
  private String aliases = "";
  @Parameter(names = {"--propath"})
  private String propath = "";
  @Parameter(names = {"--charset"})
  private String charset = "";

  private Schema readDBSchema() throws IOException {
    List<IDatabase> dbs = new ArrayList<>();
    for (String str : Splitter.on(',').split(databases)) {
      String dbName;
      int colonPos = str.lastIndexOf(':');
      if (colonPos <= 1) {
        dbName = FilenameUtils.getBaseName(str);
      } else {
        dbName = str.substring(colonPos + 1);
        str = str.substring(0, colonPos);
      }
      System.out.println("Reading " + dbName + " schema from " + str);
      dbs.add(new DatabaseWrapper(DumpFileUtils.getDatabaseDescription(Path.of(str), dbName)));
    }
    Schema schema = new Schema(dbs.toArray(new IDatabase[] {}));
    schema.injectMetaSchema();
    schema.createAlias("dictdb", schema.getDbSet().first().getName());
    for (String str : Splitter.on(';').split(aliases)) {
      List<String> xxx = Splitter.on(',').splitToList(str);
      for (int zz = 1; zz < xxx.size(); zz++) {
        System.out.println("Adding " + xxx.get(zz) + " alias to " + xxx.get(0));
        schema.createAlias(xxx.get(zz), xxx.get(0));
      }
    }

    return schema;
  }

  public void execute() throws IOException {
    System.out.println("Setting Proparse session with propath: " + propath + " - Charset: " + charset);
    ProparseSettings ppSettings = new ProparseSettings(propath, false);
    RefactorSession session = new RefactorSession(ppSettings, readDBSchema(), Charset.forName(charset));

    System.out.println("Parsing " + inputFile);
    ParseUnit unit = new ParseUnit(inputFile, inputFile.getName(), session);
    unit.treeParser01();

    System.out.println("Reading Groovy script from " + groovyFile);
    Binding binding = new Binding();
    Script gScript = new GroovyShell(binding).parse(groovyFile);
    binding.setVariable("file",
        TestInputFileBuilder.create("", new File("."), inputFile).setLanguage(Constants.LANGUAGE_KEY).setType(
            Type.MAIN).setCharset(StandardCharsets.UTF_8).setContents(
                Files.toString(inputFile, StandardCharsets.UTF_8)).build());
    binding.setVariable("unit", unit);
    binding.setVariable("link", new RuleLink());

    System.out.println("Executing Groovy script...");
    gScript.run();
  }

  public static void main(String[] args) throws Throwable {
    GroovyRule xcode = new GroovyRule();
    JCommander cmder = new JCommander(xcode);
    cmder.parse(args);
    xcode.execute();
  }

  public final class RuleLink {
    public void reportIssue(InputFile file, int lineNumber, String msg) {
      System.out.println("Issue found at line " + lineNumber + ": " + msg);
    }

    public void reportIssue(InputFile file, JPNode node, String msg) {
      System.out.println("Issue found at node " + node + ": " + msg);
    }

    public void reportIssue(InputFile file, JPNode node, String msg, boolean exactLocation) {
      System.out.println("Issue found at node " + node + ": " + msg);
    }
  }
}
