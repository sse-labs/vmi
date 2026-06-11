package de.uni_stuttgart.cs.sse.vmi.cve

import sttp.client3.*

class NvdClient(baseUrl: String = "https://services.nvd.nist.gov/rest/json/cves/2.0") {

  private val backend = HttpURLConnectionBackend()

  def getCve(cveId: String): Either[String, NvdCve] = {
    val request = basicRequest
      .get(uri"$baseUrl?cveId=$cveId")
      .header("User-Agent", "VMI/1.0")
      .header("Accept", "application/json")

    val response = request.send(backend)

    response.body match {
      case Right(jsonBody) =>
        try {
          val doc = ujson.read(jsonBody)
          val vulnerabilities = doc("vulnerabilities").arr

          if (vulnerabilities.isEmpty) {
            Left(s"CVE $cveId not found in NVD database.")
          } else {
            val cveNode = vulnerabilities.head("cve")
            val id = cveNode("id").str

            // 1. Extract English description
            val description = cveNode("descriptions").arr
              .find(d => d("lang").str == "en")
              .map(_("value").str)
              .getOrElse("No English description available.")

            // 2. Extract CVSS v3.1 safely if present
            val cvssOpt = for {
              metrics <- cveNode.obj.get("metrics")
              v31List <- metrics.obj.get("cvssMetricV31")
              primaryMetric <- v31List.arr.find(m => m("type").str == "Primary").orElse(v31List.arr.headOption)
              cvssData <- primaryMetric.obj.get("cvssData")
            } yield {
              CvssData(
                version = cvssData("version").str,
                vectorString = cvssData("vectorString").str,
                baseScore = cvssData("baseScore").num,
                baseSeverity = cvssData("baseSeverity").str
              )
            }

            // 3. Extract References (safely handles missing blocks or tags)
            val references = cveNode.obj.get("references") match {
              case Some(refsArray) =>
                refsArray.arr.map { ref =>
                  NvdReference(
                    url = ref("url").str,
                    source = ref.obj.get("source").map(_.str).getOrElse("Unknown"),
                    tags = ref.obj.get("tags").map(_.arr.map(_.str).toList).getOrElse(Nil)
                  )
                }.toList
              case None => Nil
            }

            Right(NvdCve(id, description, cvssOpt, references))
          }
        } catch {
          case ex: Exception => Left(s"Failed to process NVD JSON: ${ex.getMessage}")
        }

      case Left(errorBody) =>
        Left(s"NVD API request failed with status ${response.code}: $errorBody")
    }
  }

  def close(): Unit = backend.close()
}