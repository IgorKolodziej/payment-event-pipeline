package com.team.pipeline.infrastructure.file

import cats.effect.IO
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path

import java.nio.file.{Path as JPath}

object JsonlInput:
  final case class Line(lineNumber: Long, value: String)

  def read(path: JPath): Stream[IO, Line] =
    Files[IO]
      .readUtf8Lines(Path.fromNioPath(path))
      .zipWithNext
      .filterNot { case (line, next) => line.isEmpty && next.isEmpty }
      .map(_._1)
      .zipWithIndex
      .map { case (line, index) => Line(lineNumber = index + 1, value = line) }
