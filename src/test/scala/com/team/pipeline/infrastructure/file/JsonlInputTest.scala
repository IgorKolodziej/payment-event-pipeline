package com.team.pipeline.infrastructure.file

import cats.effect.IO
import cats.effect.Resource
import com.team.pipeline.ports.EventSource
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Path

class JsonlInputTest extends CatsEffectSuite:
  test("read streams JSONL records with one-based physical source positions") {
    val input =
      """{"eventId":1}
        |{"eventId":2}
        |{"eventId":3}
        |""".stripMargin

    tempFile.use { path =>
      for
        _ <- IO.blocking(Files.writeString(path, input))
        lines <- JsonlInput.read(path).compile.toList
      yield assertEquals(
        lines,
        List(
          EventSource.InputRecord(1, """{"eventId":1}"""),
          EventSource.InputRecord(2, """{"eventId":2}"""),
          EventSource.InputRecord(3, """{"eventId":3}""")
        )
      )
    }
  }

  test("read ignores blank lines and comment lines") {
    val input =
      """{"eventId":1}
        |
        |   
        |# comment
        |  # comment with leading whitespace
        |{"eventId":2}
        |""".stripMargin

    tempFile.use { path =>
      for
        _ <- IO.blocking(Files.writeString(path, input))
        lines <- JsonlInput.read(path).compile.toList
      yield assertEquals(
        lines,
        List(
          EventSource.InputRecord(1, """{"eventId":1}"""),
          EventSource.InputRecord(6, """{"eventId":2}""")
        )
      )
    }
  }

  private def tempFile: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempFile("payment-events-", ".jsonl")))(path =>
      IO.blocking(Files.deleteIfExists(path)).map(_ => ())
    )
end JsonlInputTest
