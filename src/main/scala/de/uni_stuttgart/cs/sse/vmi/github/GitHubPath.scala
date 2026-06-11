package de.uni_stuttgart.cs.sse.vmi.github

import scala.util.Try
import java.net.URI

/** Represents a structured representation of a GitHub repository resource path.
 *
 * This class decomposes a standard GitHub URL or coordinate path into its core components,
 * allowing for easy routing, identification, and manipulation of repository resources
 * (such as issues, pull requests, actions, or source files).
 *
 * === Example Usage ===
 * {{{
 * // Represents: github.com/owner/repo
 * val repoPath = GitHubPath("scala", "scala3", None, None)
 *
 * // Represents a specific issue: github.com/owner/repo/issues/42
 * val issuePath = GitHubPath("scala", "scala3", Some("issues"), Some("42"))
 * }}}
 *
 * @param owner        The GitHub username or organization name that owns the repository (e.g., "scala").
 * @param repo         The name of the target repository (e.g., "scala3").
 * @param resourceType An optional segment identifying the type of sub-resource under inspection
 *                     (e.g., Some("issues"), Some("pulls"), Some("blob")). Returns [[None]] if
 *                     the path targets the repository root.
 * @param resourceId   An optional unique identifier corresponding to the specific target resource
 *                     (e.g., Some("42"), Some("main/README.md")). Returns [[None]] if targeting
 *                     the generalized resource collection index or if no sub-resource exists.
 */
case class GitHubPath(
                       owner: String,
                       repo: String,
                       resourceType: Option[String],
                       resourceId: Option[String]
                     )

/** Companion object for [[GitHubPath]] providing factory utilities for structural extraction.
 *
 * This object handles parsing web location structures to isolate individual target metadata coordinates,
 * mapping path segments directly into structured types.
 */
object GitHubPath {

  /** Creates a [[GitHubPath]] by structurally tokenizing a valid URI string.
   *
   * This method decomposes the path hierarchy of an incoming URL by splitting it into sequential segments
   * after dropping leading prefixes.
   *
   * === Parsing Mechanics ===
   * - Extracts paths relative to the URI host domain (e.g., skips `https://github.com/`).
   * - Expects paths containing exactly 2 segments (`owner/repo`) or at least 4 segments (`owner/repo/type/id`).
   * - Any supplemental sub-segments past the fourth element are safely discarded.
   *
   * === Behavior ===
   * {{{
   * // Minimum Valid Structure:
   * GitHubPath.fromUrl("https://github.com/scala/scala3")
   * // => Some(GitHubPath("scala", "scala3", None, None))
   *
   * // Contextual Sub-Resource Structure:
   * GitHubPath.fromUrl("https://github.com/scala/scala3/issues/42")
   * // => Some(GitHubPath("scala", "scala3", Some("issues"), Some("42")))
   * }}}
   *
   * @param url A string representing the full, well-formed URI location.
   * @return An [[Option]] wrapping the target [[GitHubPath]], or [[None]] if the URI path segment depth
   *         does not structurally line up with repository or resource bounds.
   */
  def fromUrl(url: String): Option[GitHubPath] = {
    val uri = Try(URI.create(url)).get
    uri.getPath.stripPrefix("/").split("/").toList match {
      case owner :: repo :: resType :: resId :: _ =>
        Some(GitHubPath(owner, repo, Some(resType), Some(resId)))
      case owner :: repo :: Nil =>
        Some(GitHubPath(owner, repo, None, None))
      case _ =>
        None // Not a valid repository URL structure
    }
  }
}