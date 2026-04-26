package com.team.pipeline.infrastructure.file

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class EventSourceTest extends CatsEffectSuite:
  test("file replay source streams JSONL records unchanged") {
    tempFile.use { path =>
      for
        _ <- IO.blocking(Files.writeString(path, input))
        lines <- FileReplayEventSource(path).events.compile.toList
      yield assertEquals(lines, expectedLines)
    }
  }

  test("paced file replay source preserves record order and line numbers") {
    tempFile.use { path =>
      for
        _ <- IO.blocking(Files.writeString(path, input))
        lines <- PacedFileReplayEventSource(path, 1.millis).events.compile.toList
      yield assertEquals(lines, expectedLines)
    }
  }

  private val input =
    """{"eventId":1}
      |
      |{"eventId":2}
      |""".stripMargin

  private val expectedLines =
    List(
      JsonlInput.Line(1, """{"eventId":1}"""),
      JsonlInput.Line(2, ""),
      JsonlInput.Line(3, """{"eventId":2}""")
    )

  private def tempFile: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempFile("payment-events-", ".jsonl")))(path =>
      IO.blocking(Files.deleteIfExists(path)).map(_ => ())
    )
end EventSourceTest
