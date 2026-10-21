package com.anistream.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluentEmojisTest {
    @Test fun urlEncodesFolderSpacesAndAppends3dSuffix() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@main/assets/Smiling%20face%20with%20sunglasses/3D/smiling_face_with_sunglasses_3d.png",
            FluentEmojis.urlFor("smiling_face_with_sunglasses")
        )
        assertEquals(
            "https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@main/assets/Star-struck/3D/star-struck_3d.png",
            FluentEmojis.byId("star-struck").url
        )
    }

    @Test fun unknownIdFallsBackAndListHas24() {
        assertEquals(24, FluentEmojis.all.size)
        assertTrue(FluentEmojis.urlFor("nope").contains("Grinning%20face"))
    }
}
