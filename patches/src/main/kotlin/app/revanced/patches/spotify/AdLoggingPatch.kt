package app.revanced.patches.spotify

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

@Suppress("unused")
val adLoggingPatch = bytecodePatch(
    name = "Add ad system logging",
    description = "Logs full ad request and event data to logcat with tag SpotifyAds for reverse engineering.",
) {
    compatibleWith("com.spotify.music"("9.1.62.1601"))

    execute {
        getAdsCallFingerprint.method.addInstructions(0, """
            const-string v2, "SpotifyAds"
            const-string v3, "[GetAds] REQUEST >>>"
            invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
            invoke-virtual {p1}, Lcom/spotify/ads/esperanto/proto/GetAdsRequest;->toString()Ljava/lang/String;
            move-result-object v3
            invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
        """)

        subBreakChangedEmitFingerprint.method.addInstructions(0, """
            const-string v3, "SpotifyAds"
            const-string v4, "[InStream] SubBreakChanged >>>"
            invoke-static {v3, v4}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
        """)

        adSlotEventProcessFingerprint.method.addInstructions(0, """
            const-string v2, "SpotifyAds"
            const-string v3, "[AdSlot] EVENT >>>"
            invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
            invoke-virtual {p0}, Lcom/spotify/ads/esperanto/proto/AdSlotEvent;->toString()Ljava/lang/String;
            move-result-object v3
            invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
        """)
    }
}
