package com.team.pipeline.infrastructure.file

import cats.effect.IO
import com.team.pipeline.ports.EventSource
import fs2.Stream

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration

final class PacedFileReplayEventSource(path: Path, delay: FiniteDuration) extends EventSource:
  override def events: Stream[IO, EventSource.InputRecord] =
    JsonlInput.read(path).meteredStartImmediately(delay)
