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
      .map { case (line, index) => (index + 1, line.trim) }
      .collect {
        case (sourcePosition, trimmed) if trimmed.nonEmpty && !trimmed.startsWith("#") =>
          EventSource.InputRecord(sourcePosition = sourcePosition, value = trimmed)
      }
