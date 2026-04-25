package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.domain.Alert

trait AlertStore:
  def saveAll(alerts: List[Alert]): IO[Unit]
