package de.uni_stuttgart.cs.sse.vmi

import com.github.difflib.unifieddiff.UnifiedDiff

/** A core architectural trait defining the abstraction layer for retrieving and isolating
 * reference patch data from a remote source version control system.
 *
 * Implementations of this trait are responsible for targeting specific project state references
 * (such as individual commits or pull requests) to extract their structural modifications
 * and source baselines.
 */
trait ReferencePatchRetriever {
  /** Resolves and extracts the unified delta adjustments and affected raw assets from the target source reference.
   *
   * @return A tuple containing:
   *         - A [[UnifiedDiff]] object detailing the precise syntactic modifications made across the change block.
   *         - A [[Map]] index associating each altered source file name directly to its raw text content payload.
   */
  def handleReference(): (UnifiedDiff, Map[String, String])
}
