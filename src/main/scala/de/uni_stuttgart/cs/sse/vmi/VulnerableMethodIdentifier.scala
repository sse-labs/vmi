package de.uni_stuttgart.cs.sse.vmi

import com.typesafe.scalalogging.LazyLogging
import de.uni_stuttgart.cs.sse.vmi.cve.{NvdClient, NvdCve}
import de.uni_stuttgart.cs.sse.vmi.github.{GitHubCommitHandler, GitHubPullRequestHandler}
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.*

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** The primary execution coordinator for identifying specific source methods affected by a vulnerability.
 *
 * This engine resolves a given CVE identifier against the National Vulnerability Database (NVD), parses
 * public code references (such as patches, commits, or pull requests), runs AST structural analysis
 * using an Eclipse JDT compiler instance, and builds a comprehensive index of fully qualified method changes.
 *
 * === Prerequisites ===
 * Executing this tool requires a valid `GITHUB_TOKEN` present within the system execution environment.
 * If the token is missing, the initializer will print a critical diagnostic block and abort execution.
 */
object VulnerableMethodIdentifier extends LazyLogging {

  // GitHub token needed for access to the GitHub API
  private val githubToken: Option[String] = {
    val token = sys.env.get("GITHUB_TOKEN")
    token.match {
      case None =>
        System.err.println("CRITICAL ERROR: 'GITHUB_TOKEN' environment variable is missing.")
        System.err.println("Please set it in your terminal or .env file before running.")
        logger.error("GITHUB_TOKEN not found in the environment variables.")
        sys.exit(1)
      case Some(_) => token
    }
  }

  /**
   * Uses a public facing web api from the National Vulnerability Database to
   * retrieve information about the CVE record.
   *
   * @param cveId CVE identifier in standard format (i.e. CVE-<year>-<number>)
   * @return Either a model of the CVE record or None
   */
  private def resolveCVEInformation(cveId: String): Option[NvdCve] = {
    logger.info(s"Starting to resolve CVE $cveId against the NVD database...")
    val client = new NvdClient()
    val result = client.getCve(cveId)
    client.close()

    result match {
      case Left(value) =>
        logger.warn(s"Could not resolve CVE $cveId. Error: $value")
        None
      case Right(value) => Some(value)
    }
  }

  /** Application entrypoint that maps a security vulnerability ID down to its affected Java source methods.
   *
   * @param cveId The standard registration tracking identifier for the target flaw (e.g., `"CVE-2026-27830"`).
   * @return A [[Set]] containing the string representations of Fully Qualified Method Names (FQMNs)
   *         including parameter blocks (e.g., `""com.example.util.Calculator.add(int, int)""`).
   */
  @main def retrieveAffectedMethods(cveId: String): Set[String] = {
    // CVE-2025-64087
    // CVE-2026-27830 (with Commit)
    // CVE-2026-29062 (with PR)
    logger.info("Welcome to Vulnerable Method Identification.")

    if cveId.trim.isEmpty then {
      logger.warn("No CVE ID provided. Please provide one as the first argument.")
    }

    // Retrieve CVE information from the NVD
    val cveResponse = resolveCVEInformation(cveId)
    cveResponse.match {
      case None =>
        logger.error("CVE could not be successfully resolved.")
        sys.exit(1)
      case Some(cveInfo) =>
        logger.info(s"Successfully resolved ${cveInfo.id} - ${cveInfo.description.firstSentence()}")
    }
    val cve = cveResponse.get

    logger.info(s"CVE record show ${cve.references.size} references.")

    // The following lines detect the handler based on the URL provided.
    // This could be done in a more clever way, if necessary

    // Assign handlers to references
    val refs = cve.references.flatMap(r => {
      if GitHubCommitHandler.canHandle(r.url)
        then Some(new GitHubCommitHandler(githubToken.get, r.url))
      else if GitHubPullRequestHandler.canHandle(r.url)
        then Some(new GitHubPullRequestHandler(githubToken.get, r.url))
      else {
        logger.debug(s"Could not handle reference: ${r.url}")
        None
      }
    })

    val affectedMethods = refs.map(r => r.handleReference()).flatMap(t => {
      val parsedPatch = t._1
      val files = t._2

      // Parse all Java source files (result: file name -> parse compilation unit)
      val parsedFiles = files.filter((fn, _) => fn.endsWith("java"))
                             .map((fn, fc) => (fn, parseSource(fc))).to(Map)

      // Get the line numbers for all changes
      val lines = parsedPatch.getFiles.asScala
        .filter(f => f.getToFile.endsWith("java"))
        .map(f => (f.getToFile, f.getPatch))
        .map(p => (p._1, p._2.getDeltas.asScala.flatMap(d => d.getTarget.getChangePosition.asScala)))

      // Get the unique enclosing method declarations for all changes
      val elements = lines.map((fn, lines) => {
        val cu = parsedFiles(fn)
        val elements = lines.map(ln => {
          val finder = new LineNumberFinder(cu, ln)
          cu.accept(finder)
          getEnclosingMethodDeclaration(finder.getResult)
        }).toSet.flatten
        (fn, elements)
      })

      elements.flatMap((fn, elements) => elements).map(md => toFQMN(md))
    }).toSet
    affectedMethods.foreach(m => logger.info(s"Affected method: ${m}"))
    affectedMethods
  }

  private def toFQMN(md: MethodDeclaration): String = {
    val methodName = md.getName.getIdentifier

    val enclosingClasses = getEnclosingClasses(md)

    val root = md.getRoot.asInstanceOf[CompilationUnit]
    val packageNameOpt = Option(root.getPackage).map(_.getName.getFullyQualifiedName)

    val pathElements = ListBuffer[String]()
    packageNameOpt.foreach(pathElements.append)
    pathElements.appendAll(enclosingClasses)
    pathElements.append(methodName)
    val fullyQualifiedPath = pathElements.mkString(".")

    val parameters = md.parameters().asScala.map { case param: SingleVariableDeclaration =>
      val rawType = param.getType.toString

      // Handle extra array dimensions on the variable name itself, e.g., int nums[]
      val extraDimensions = "[]" * param.getExtraDimensions

      // Handle varargs, e.g., String...
      val varargs = if (param.isVarargs) "..." else ""

      s"$rawType$varargs$extraDimensions"
    }.mkString(", ")

    val completeSignature = s"$fullyQualifiedPath($parameters)"

    completeSignature
  }


  private def getEnclosingMethodDeclaration(node: Option[ASTNode]): Option[MethodDeclaration] = {
    if node.isEmpty then return None

    var current = node.get
    while (current != null && !current.isInstanceOf[MethodDeclaration])
      current = current.getParent

    if current == null then return None
    Some(current.asInstanceOf[MethodDeclaration])
  }

  private def getEnclosingClasses(node: ASTNode): List[String] = {
    @tailrec
    def walk(current: ASTNode, acc: List[String]): List[String] = {
      if (current == null) acc
      else {
        current match {
          case t: AbstractTypeDeclaration =>
            // Captures Class, Interface, Enum, or Record names
            walk(current.getParent, t.getName.getIdentifier :: acc)
          case _ =>
            walk(current.getParent, acc)
        }
      }
    }

    walk(node.getParent, Nil)
  }

  private def parseSource(javaSource: String) = {
    val parser = ASTParser.newParser(AST.JLS21)
    parser.setSource(javaSource.toCharArray)
    parser.setKind(ASTParser.K_COMPILATION_UNIT)

    // Compiler options can be set here if needed
    val options = JavaCore.getOptions
    JavaCore.setComplianceOptions(JavaCore.VERSION_23, options)
    parser.setCompilerOptions(options)

    parser.createAST(null).asInstanceOf[CompilationUnit]
  }
}

