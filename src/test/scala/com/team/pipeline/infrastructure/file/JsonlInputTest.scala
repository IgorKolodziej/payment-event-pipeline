package com.team.pipeline.infrastructure.file

import cats.effect.IO
import munit.CatsEffectSuite

import java.nio.file.Files

class JsonlInputTest extends CatsEffectSuite:
  test("read streams JSONL lines with one-based line numbers") {
    val input =
      """{"eventId":1}
        |{"eventId":2}
        |{"eventId":3}
        |""".stripMargin

    for
      path <- IO.blocking(Files.createTempFile("payment-events-", ".jsonl"))
      _ <- IO.blocking(Files.writeString(path, input))
      lines <- JsonlInput.read(path).compile.toList
      _ <- IO.blocking(Files.deleteIfExists(path))
    yield assertEquals(
      lines,
      List(
        JsonlInput.Line(1, """{"eventId":1}"""),
        JsonlInput.Line(2, """{"eventId":2}"""),
        JsonlInput.Line(3, """{"eventId":3}""")
      )
    )
  }

  test("read preserves blank lines for the pipeline to handle explicitly") {
    val input =
      """{"eventId":1}
        |
        |{"eventId":2}
        |""".stripMargin

    for
      path <- IO.blocking(Files.createTempFile("payment-events-", ".jsonl"))
      _ <- IO.blocking(Files.writeString(path, input))
      lines <- JsonlInput.read(path).compile.toList
      _ <- IO.blocking(Files.deleteIfExists(path))
    yield assertEquals(
      lines,
      List(
        JsonlInput.Line(1, """{"eventId":1}"""),
        JsonlInput.Line(2, ""),
        JsonlInput.Line(3, """{"eventId":2}""")
      )
    )
  }
end JsonlInputTest
