package de.uni_stuttgart.cs.sse.vmi

import java.text.BreakIterator
import java.util.Locale

extension (text: String)
  /**
   * Safely extracts the first sentence of a string using linguistic boundaries.
   * Defaults to US English rules, but accepts any Locale.
   */
  def firstSentence(locale: Locale = Locale.US): String =
    if text == null || text.trim.isEmpty then ""
    else
      // 1. Initialize the linguistic sentence boundary tracker
      val boundary = BreakIterator.getSentenceInstance(locale)
      boundary.setText(text)

      // 2. Find where the very first sentence starts and ends
      val start = boundary.first()
      val end = boundary.next()

      // 3. Extract the substring if a valid end boundary was found
      if end != BreakIterator.DONE then
        text.substring(start, end).trim
      else
        text.trim