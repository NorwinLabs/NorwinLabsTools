package com.norwinlabs.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class CircleMemberTest {
    @Test
    fun testCircleMemberData() {
        val member = CircleShareFragment.CircleMember(
            id = "123456",
            name = "Test User",
            photoBase64 = "base64data"
        )
        
        assertEquals("123456", member.id)
        assertEquals("Test User", member.name)
        assertEquals("base64data", member.photoBase64)
    }

    @Test
    fun testP2PPayloadFormat() {
        val userId = "user1"
        val name = "Gavin"
        val lat = 40.7128
        val lng = -74.0060
        
        val payload = "LOC,$userId,$name,$lat,$lng"
        val parts = payload.split(",")
        
        assertEquals("LOC", parts[0])
        assertEquals("user1", parts[1])
        assertEquals("Gavin", parts[2])
        assertEquals(40.7128, parts[3].toDouble(), 0.0001)
        assertEquals(-74.0060, parts[4].toDouble(), 0.0001)
    }
}
