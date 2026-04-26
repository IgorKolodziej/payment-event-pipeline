package com.team.pipeline.ports

import cats.effect.IO
import fs2.Stream

object EventSource:
  final case class InputLine(lineNumber: Long, value: String)

trait EventSource:
  def events: Stream[IO, EventSource.InputLine]
