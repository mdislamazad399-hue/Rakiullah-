package com.example

import com.example.data.auth.normalizeE164PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun normalizeE164PhoneNumber_formatsValidNumbers() {
    assertEquals("+919876543210", normalizeE164PhoneNumber("+91", "9876543210"))
    assertEquals("+919876543210", normalizeE164PhoneNumber("91", "09876-543 210"))
    assertEquals("+14155552671", normalizeE164PhoneNumber("+1", "+1 (415) 555-2671"))
  }

  @Test
  fun normalizeE164PhoneNumber_rejectsInvalidNumbers() {
    assertNull(normalizeE164PhoneNumber("+91", ""))
    assertNull(normalizeE164PhoneNumber("+91", "1234"))
    assertNull(normalizeE164PhoneNumber("", "9876543210"))
  }
}
