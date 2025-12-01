package com.manoj.eventclassifier

import org.junit.Test

import org.junit.Assert.*

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
    fun multiplyByTwo(){
        val intArray = intArrayOf(3, 6, 12, 5)
        val original = 3

        var result = 0
        val finding = intArray.find { it == original }

        while(finding != null){
            result = finding * 2

        }
    }
}