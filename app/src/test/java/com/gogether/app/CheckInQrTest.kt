package com.gogether.app

import com.gogether.app.core.ui.CheckInQr
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random
import java.util.UUID

class CheckInQrTest {
    @Test fun falseFinderPatternAndRotatingPayloadsRemainScannable() {
        val random = Random(20261124)
        val ids = listOf("cae2b98d-6b61-124d-1793-1c123c136c12") +
            List(64) { UUID(random.nextLong(), random.nextLong()).toString() }
        for (id in ids) {
            val raw = "GOGETHER-LOCAL-QR-1|$id|31.27|120.74|1800000600000|1800007800000|1800000000000|1800000030000"
            val matrix = CheckInQr.matrix(raw)
            val pixels = IntArray(720 * 720) { if (matrix[it % 720, it / 720]) -0x1000000 else -1 }
            val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(720, 720, pixels))))
            assertEquals(raw, decoded.text)
        }
    }
}
