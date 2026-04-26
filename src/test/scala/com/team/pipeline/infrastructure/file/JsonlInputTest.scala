package com.team.pipeline.infrastructure.file

import cats.effect.IO
import cats.effect.Resource
import com.team.pipeline.ports.EventSource
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Path

class JsonlInputTest extends CatsEffectSuite:
  test("read streams JSONL lines with one-based line numbers") {
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
          EventSource.InputLine(1, """{"eventId":1}"""),
          EventSource.InputLine(2, """{"eventId":2}"""),
          EventSource.InputLine(3, """{"eventId":3}""")
        )
      )
    }
  }

  test("read preserves blank lines for the pipeline to handle explicitly") {
    val input =
      """{"eventId":1}
        |
        |{"eventId":2}
        |""".stripMargin

    tempFile.use { path =>
      for
        _ <- IO.blocking(Files.writeString(path, input))
        lines <- JsonlInput.read(path).compile.toList
      yield assertEquals(
        lines,
        List(
          EventSource.InputLine(1, """{"eventId":1}"""),
          EventSource.InputLine(2, ""),
          EventSource.InputLine(3, """{"eventId":2}""")
        )
      )
    }
  }

  private def tempFile: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempFile("payment-events-", ".jsonl")))(path =>
      IO.blocking(Files.deleteIfExists(path)).map(_ => ())
    )
end JsonlInputTest
