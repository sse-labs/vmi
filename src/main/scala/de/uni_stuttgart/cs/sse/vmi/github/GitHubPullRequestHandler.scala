package de.uni_stuttgart.cs.sse.vmi.github

import com.github.difflib.unifieddiff.{UnifiedDiff, UnifiedDiffReader}
import com.typesafe.scalalogging.LazyLogging
import de.uni_stuttgart.cs.sse.vmi.ReferencePatchRetriever
import org.kohsuke.github.GHPullRequest

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets

import scala.util.Try
import scala.jdk.CollectionConverters.*

/** Coordinates the retrieval and structural extraction of patch diffs and source files
 * associated with a specific GitHub pull request endpoint.
 *
 * This class fetches the target pull request metadata via the GitHub API, parses the collective
 * patch content into a unified diff format, and extracts the raw content of all involved source files.
 *
 * @param githubToken The personal access token utilized to authenticate API and raw web requests.
 * @param url         The target browser URL pointing to a specific GitHub pull request page.
 */
class GitHubPullRequestHandler(githubToken: String, url: String)
  extends ReferencePatchRetriever
    with GitHubHandling(githubToken)
    with LazyLogging {

  /** Processes the configured pull request target to resolve its structural diff and source state.
   *
   * @return A tuple containing:
   *         - A [[UnifiedDiff]] model structuring the exact lines changed.
   *         - A [[Map]] binding each modified source file name directly to its raw text payload content.
   */
  override def handleReference(): (UnifiedDiff, Map[String, String]) = {
    val ghPath = GitHubPath.fromUrl(url).get
    logger.info(s"Processing GitHub pull request ${ghPath.resourceId.getOrElse("<empty>")} for repository ${ghPath.owner}/${ghPath.repo}")

    val ghRepo = resolveRepository(ghPath)
    val pr = ghRepo.getPullRequest(ghPath.resourceId.get.toInt)

    // Parse the patch from the input stream (includes all changes made)
    val unifiedDiffStream = new ByteArrayInputStream(getUnifiedDiff(pr).getBytes(StandardCharsets.UTF_8))
    val parsedPatch = UnifiedDiffReader.parseUnifiedDiff(unifiedDiffStream)

    // Request all Java source files included from the commit (result: file name -> file content)
    val files = pr.listFiles().toList.asScala
      .map(f => (f.getFilename, readResource(f.getRawUrl))).to(Map)

    (parsedPatch, files)
  }

  private def getUnifiedDiff(pr: GHPullRequest): String = {
    val diffUrlString = s"${pr.getHtmlUrl}.diff"
    val url = URI.create(diffUrlString).toURL

    readResource(url)
  }
}

/** Companion object providing capability mapping and structural targeting validations for [[GitHubPullRequestHandler]].
 */
object GitHubPullRequestHandler {

  /** Evaluates whether an incoming resource string targets a valid location this handler can process.
   *
   * @param url The raw location path target string under review.
   * @return `true` if the URL safely targets a GitHub pull request path block, otherwise `false`.
   */
  def canHandle(url: String): Boolean = {
    val uri = Try(URI.create(url))
    uri.map(_.getHost.equals("github.com")).getOrElse(false) && uri.map(_.getPath.contains("pull")).getOrElse(false)
  }
}
