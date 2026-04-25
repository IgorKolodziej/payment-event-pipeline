package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.domain.EligibilityViolation

trait EligibilityViolationStore:
  def saveAll(violations: List[EligibilityViolation]): IO[Unit]
