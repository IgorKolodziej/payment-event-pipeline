package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.CustomerProfile

trait CustomerProfileLookup:
  def find(customerId: CustomerId): IO[Option[CustomerProfile]]
