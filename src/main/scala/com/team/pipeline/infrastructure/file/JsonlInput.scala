package com.team.pipeline.infrastructure.file

import cats.effect.IO
import com.team.pipeline.ports.EventSource
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path

import java.nio.file.{Path as JPath}

object JsonlInput:
  def read(path: JPath): Stream[IO, EventSource.InputRecord] =
    Files[IO]
      .readUtf8Lines(Path.fromNioPath(path))
      .zipWithIndex
      .map { case (line, index) =>
        val sourcePosition = index + 1
        val trimmed = line.trim
        (sourcePosition, trimmed)
      }
      .filter { case (_, trimmed) =>
        trimmed.nonEmpty && !trimmed.startsWith("#")
      }
      .map { case (sourcePosition, trimmed) =>
        EventSource.InputRecord(sourcePosition = sourcePosition, value = trimmed)
      }
