package de.uni_stuttgart.cs.sse.vmi.github

import org.kohsuke.github.{GHRepository, GitHub, GitHubBuilder}

import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets

/** A trait providing core authentication and lower-level network communication utilities
 * for interacting with the GitHub API.
 *
 * Classes mixing in this trait gain access to a pre-authenticated GitHub client
 * backend as well as helper functions to read raw online resources.
 *
 * @param githubToken The personal access token used to authenticate all outbound HTTP requests.
 */
trait GitHubHandling(githubToken: String) {
  /** The authenticated underlying instance of the [[GitHub]] API wrapper.
   */
  protected val github: GitHub = new GitHubBuilder().withOAuthToken(githubToken).build()

  private def createConnection(url: URL): HttpURLConnection = {
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("GET")
    connection.setRequestProperty("Authorization", s"Bearer ${githubToken}")
    connection.setRequestProperty("User-Agent", "VMI/1.0")
    connection
  }

  /** Performs a synchronous HTTP GET request on a specified endpoint using token authentication.
   *
   * @param url The raw web address of the target file or resource.
   * @return A UTF-8 encoded [[String]] payload containing the resource body if the server returns
   *         a successful response code (200 OK), otherwise returns an empty string (`""`).
   */
  protected def readResource(url: URL): String = {
    val connection = createConnection(url)

    val responseCode = connection.getResponseCode
    if (responseCode == 200) {
      val stream = connection.getInputStream
      val text = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
      stream.close()
      text
    } else {
      ""
    }
  }

  /** Fetches a structured repository handle from the authenticated client matching the given coordinates.
   *
   * @param ghPath A [[GitHubPath]] coordinate wrapper targeting a specific owner and repo.
   * @return A [[GHRepository]] object providing access to meta properties, branches, or code hierarchies.
   */
  protected def resolveRepository(ghPath: GitHubPath): GHRepository = {
    github.getRepository(s"${ghPath.owner}/${ghPath.repo}")
  }
}
