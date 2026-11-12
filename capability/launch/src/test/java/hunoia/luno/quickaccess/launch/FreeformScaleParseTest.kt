package hunoia.luno.quickaccess.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreeformScaleParseTest {

    private val displayLine = "  Display #0: [a381df8 com.foo/com.foo.MainActivity, " +
        "frame=[Rect(259, 165 - 1100, 1200)], touchableRegion=SkRegion((0,0,1200,2670)), " +
        "scaleFactor=1.0, transform=Matrix{[1.4285713, 0.0, -0.0][0.0, 1.0, -0.0][0.0, 0.0, 1.0]}, " +
        "windowToken=android.os.BinderProxy@1, displayId=0, isClone=false]"

    @Test
    fun `comma inside SkRegion does not break window matching`() {
        assertEquals(1.4285713f, FreeformScale.parse(displayLine, "com.foo"))
    }

    @Test
    fun `expected package wins over other freeform windows`() {
        val output = displayLine +
            "\n  Display #0: [b2 com.bar/com.bar.MainActivity, " +
            "frame=[Rect(0, 0 - 100, 100)], touchableRegion=SkRegion((0,0,100,100)), " +
            "scaleFactor=1.0, transform=Matrix{[1.7857143, 0.0, -0.0][0.0, 1.0, -0.0][0.0, 0.0, 1.0]}]"

        assertEquals(1.4285713f, FreeformScale.parse(output, "com.foo"))
        assertEquals(1.7857143f, FreeformScale.parse(output, "com.bar"))
    }

    @Test
    fun `mini freeform scale 4 is ignored`() {
        val output = "  Display #0: [c3 com.foo/com.foo.MainActivity, " +
            "frame=[Rect(855, 165 - 1200, 2000)], touchableRegion=SkRegion((855,165,1200,2000)), " +
            "scaleFactor=1.0, transform=Matrix{[4.0, 0.0, -0.0][0.0, 4.0, -0.0][0.0, 0.0, 1.0]}]"

        assertNull(FreeformScale.parse(output, "com.foo"))
    }

    @Test
    fun `landscape scales are rotation matrices`() {
        val output = "  Display #0: [d4 com.foo/com.foo.MainActivity, " +
            "frame=[Rect(0, 0 - 1200, 2670)], touchableRegion=SkRegion((0,0,1200,2670)), " +
            "scaleFactor=1.0, transform=Matrix{[0.0, 1.7857143, -693.71423]" +
            "[-1.0, 0.0, 81.119995][0.0, 0.0, 1.0]}]"

        assertEquals(1.7857143f, FreeformScale.parse(output, "com.foo"))
    }

    @Test
    fun `rotation without scale is ignored`() {
        val output = "  Display #0: [e5 com.foo/com.foo.MainActivity, " +
            "frame=[Rect(0, 0 - 1200, 2670)], touchableRegion=SkRegion((0,0,1200,2670)), " +
            "scaleFactor=1.0, transform=Matrix{[0.0, 1.0, -2643.0][-1.0, 0.0, 1200.0][0.0, 0.0, 1.0]}]"

        assertNull(FreeformScale.parse(output, "com.foo"))
    }
}
