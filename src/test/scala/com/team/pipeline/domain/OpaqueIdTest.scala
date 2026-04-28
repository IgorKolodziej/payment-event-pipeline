package com.team.pipeline.domain

import com.team.pipeline.domain.CustomerId.*
import com.team.pipeline.domain.EventId.*
import munit.FunSuite

class OpaqueIdTest extends FunSuite:
  test("opaque IDs expose raw values for external adapter boundaries") {
    assertEquals(EventId(100).value, 100)
    assertEquals(CustomerId(10).value, 10)
  }
