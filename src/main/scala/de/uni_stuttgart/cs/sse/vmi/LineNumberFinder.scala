package de.uni_stuttgart.cs.sse.vmi

import org.eclipse.jdt.core.dom.{ASTNode, ASTVisitor, CompilationUnit}

/** An AST visitor designed to locate and capture the deepest syntactic node corresponding
 * to a specific line number within a compilation unit.
 *
 * By continuously intercepting nodes during depth-first tree traversal, this finder narrows
 * down execution structures to isolate the most specific element defined on the target line.
 *
 * @param cu         The parsed [[CompilationUnit]] root used to map character offsets to line locations.
 * @param targetLine The 1-based source code line number to search for.
 */
class LineNumberFinder(cu: CompilationUnit, targetLine: Int) extends ASTVisitor {
  private var foundNode: Option[ASTNode] = None

  /** Intercepts every node in the AST during traversal to evaluate line placement.
   *
   * Maps the node's start boundary character offset into a source line. Because the traversal
   * explores elements from top-level blocks down to nested leaf nodes, the last node matching
   * the `targetLine` overwrites parent structures, yielding the narrowest contextual element.
   *
   * @param node The structural [[ASTNode]] currently being visited by the engine.
   */
  override def preVisit(node: ASTNode): Unit = {
    // Get the starting character offset of the node
    val startPosition = node.getStartPosition

    // Translate character offset to a 1-based line number
    val nodeLine = cu.getLineNumber(startPosition)

    // If it matches our target line, capture the tightest/deepest node
    if (nodeLine == targetLine) {
      foundNode = Some(node)
    }
  }

  /** Retrieves the discovered structural node associated with the configured target line.
   *
   * @return An [[Option]] wrapping the deepest [[ASTNode]] found on that line,
   *         or [[None]] if the line holds no compilable code structures.
   */
  def getResult: Option[ASTNode] = foundNode
}
