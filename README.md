# Vulnerable Method Identification (VMI)

A utility that maps vulnerabilities (**CVEs**) listed in the National Vulnerability Database (**NVD**) directly down to
the specific **Java source methods** that were modified to fix them.

By retrieving patch diffs from security advisories and parsing the corresponding source baselines into an Abstract
Syntax Tree (AST), VMI pinpoints the exact boundaries of vulnerable code signatures.

> ⚠️ **Scope Limitations:** Currently, this project is explicitly built to support **GitHub references** (commits and
> pull requests) and analyzes **Java source code** exclusively.

---

## Architecture Overview

VMI is designed around a modular pipeline that connects vulnerability metadata with deep structural code analysis:

1. **Vulnerability Resolution:** Queries the NVD API to fetch CVE structural data and reference endpoints.
2. **Reference Retrieval (`ReferencePatchRetriever`):** Identifies valid web targets (Commits/PRs) and handles raw
   authentication, file network streaming, and patch isolation.
3. **AST Parsing & Mapping:** Compiles affected file snapshots using an embedded Eclipse JDT compiler instance.
4. **Line-Level Identification (`LineNumberFinder`):** Traverses the tree to map line modifications to the tightest
   enclosing `MethodDeclaration`, formatting them into Fully Qualified Method Names (FQMNs).

## Prerequisites
The tool communicates with the GitHub REST API and requires a valid personal access token to prevent rate-limiting and handle private or public telemetry safely.

Create a .env file in the root directory of your project:

```
GITHUB_TOKEN=ghp_YourActualGitHubPersonalAccessTokenHere
```

## Usage

### Running via sbt CLI
Execute the tool directly from your terminal by passing a specific target CVE identifier as the primary argument block:

```Bash
sbt "run CVE-2025-64087"
```

### Programmatic Integration
You can invoke the analytical module inside your own Scala workflows or pipelines:

```Scala
import de.uni_stuttgart.cs.sse.vmi.VulnerableMethodIdentifier

val affected: Set[String] = VulnerableMethodIdentifier.retrieveAffectedMethods("CVE-2026-27830")
// Returns: Set("org.apache.commons.imaging.formats.tiff.TiffImageParser.getBufferedImage(org.apache.commons.imaging.formats.tiff.TiffDirectory, java.util.Map)")
```