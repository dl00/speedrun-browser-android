package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class GameRuleset(
    var showMilliseconds: Boolean = false,
    var requireVerification: Boolean = false,
    var requireVideo: Boolean = false,
    var runTimes: Array<String>? = null,
    var defaultTime: String? = null,
    var emulatorsAllowed: Boolean = false
) : Serializable
