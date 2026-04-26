package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.infrastructure.file.JsonlInput
import fs2.Stream

trait EventSource:
  def events: Stream[IO, JsonlInput.Line]
