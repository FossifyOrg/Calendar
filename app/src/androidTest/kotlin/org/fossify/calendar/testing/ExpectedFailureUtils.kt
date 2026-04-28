package org.fossify.calendar.testing

fun expectedFailure(testName: String = "", block: () -> Unit) {
    try {
        block()
        println("WARNING: Expected failure test '$testName' passed unexpectedly.")
    } catch (e: Throwable) {
        println("Expected failure in test '$testName': ${e.javaClass.simpleName}: ${e.message}")
        return
    }

    throw AssertionError("Expected failure test '$testName' passed unexpectedly. Investigate: bug may be fixed.")
}
