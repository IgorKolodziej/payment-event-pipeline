package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.domain.CustomerProfile

trait CustomerProfileLookup:
  def find(customerId: Int): IO[Option[CustomerProfile]]
