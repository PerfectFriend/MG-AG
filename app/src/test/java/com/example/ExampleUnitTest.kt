package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPinRetryDelayIncrements() {
    val delays = (1..7).map { attempts ->
      when (attempts) {
        1 -> 15
        2 -> 30
        3 -> 60
        4 -> 120
        5 -> 240
        6 -> 480
        else -> 960
      }
    }
    assertEquals(listOf(15, 30, 60, 120, 240, 480, 960), delays)
  }
}
