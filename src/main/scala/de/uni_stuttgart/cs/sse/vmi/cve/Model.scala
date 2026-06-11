package de.uni_stuttgart.cs.sse.vmi.cve

case class CvssData(
                     version: String,
                     vectorString: String,
                     baseScore: Double,
                     baseSeverity: String
                   )

case class NvdReference(
                         url: String,
                         source: String,
                         tags: List[String] // e.g., List("Vendor Advisory", "Exploit")
                       )

case class NvdCve(
                   id: String,
                   description: String,
                   cvss: Option[CvssData],
                   references: List[NvdReference] // Restored field!
                 )