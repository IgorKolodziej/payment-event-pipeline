package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.domain.ProcessedEvent

trait ProcessedEventStore:
  def save(event: ProcessedEvent): IO[Unit]
