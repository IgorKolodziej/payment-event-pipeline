package com.team.pipeline.infrastructure.file

import cats.effect.IO
import com.team.pipeline.ports.EventSource
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path

import java.nio.file.{Path as JPath}

object JsonlInput:
  def read(path: JPath): Stream[IO, EventSource.InputLine] =
    Files[IO]
      .readUtf8Lines(Path.fromNioPath(path))
      .zipWithIndex
      .map { case (line, index) => EventSource.InputLine(lineNumber = index + 1, value = line) }
      .zipWithNext
      .filterNot { case (line, next) => line.value.isEmpty && next.isEmpty }
      .map(_._1)
