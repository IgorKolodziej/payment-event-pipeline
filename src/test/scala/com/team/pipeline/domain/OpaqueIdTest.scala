package com.team.pipeline.domain

import com.team.pipeline.domain.CustomerId.*
import com.team.pipeline.domain.EventId.*
import munit.FunSuite

class OpaqueIdTest extends FunSuite:
  test("opaque IDs expose raw values for external adapter boundaries") {
    assertEquals(EventId(100).value, 100)
    assertEquals(CustomerId(10).value, 10)
  }

  test("fromInt accepts positive IDs") {
    assertEquals(EventId.fromInt(100), Right(EventId(100)))
    assertEquals(CustomerId.fromInt(10), Right(CustomerId(10)))
  }

  test("fromInt rejects non-positive IDs") {
    assert(EventId.fromInt(0).isLeft)
    assert(EventId.fromInt(-1).isLeft)
    assert(CustomerId.fromInt(0).isLeft)
    assert(CustomerId.fromInt(-1).isLeft)
  }
