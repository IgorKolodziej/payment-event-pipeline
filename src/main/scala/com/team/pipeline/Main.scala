package com.team.pipeline

import cats.effect.{IO, IOApp}
import com.team.pipeline.config.AppConfig

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    AppConfig.load.flatMap { config =>
      IO.println(
        s"Payment Event Processing Pipeline started. input=${config.app.inputFile}, output=${config.app.outputDir}"
      )
    }
