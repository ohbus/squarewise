package com.subhrodip.squarewise.db.routing

import com.subhrodip.squarewise.db.errors.DbPlatformException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DbWatermarkTest {
    @Test
    fun `round trips postgres lsn`() {
        val watermark = DbWatermark.parse("0/16B6C50")
        assertEquals("0/16B6C50", watermark.asLsn())
        assertEquals(watermark, DbWatermark.parse(watermark.asLsn()))
    }

    @Test
    fun `rejects malformed lsn`() {
        assertFailsWith<DbPlatformException> { DbWatermark.parse("not-an-lsn") }
    }

    @Test
    fun `rejects negative watermark positions`() {
        assertFailsWith<DbPlatformException> { DbWatermark.fromPosition(-1) }
    }

    @Test
    fun `creates a watermark from a valid postgres position`() {
        val watermark = DbWatermark.fromPosition(0x16B6C50L)

        assertEquals("0/16B6C50", watermark.asLsn())
        assertEquals(watermark, DbWatermark.parse(watermark.asLsn()))
    }

    @Test
    fun `rejects missing and oversized lsn components`() {
        assertFailsWith<DbPlatformException> { DbWatermark.parse("0/") }
        assertFailsWith<DbPlatformException> { DbWatermark.parse("100000000/1") }
        assertFailsWith<DbPlatformException> { DbWatermark.parse("1/100000000") }
    }
}
