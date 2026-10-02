package app.revanced.patches.spotify

import app.morphe.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal val getAdsCallFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Ljava/lang/Object;")
    strings("Unable to parse data as com.spotify.ads.esperanto.proto.GetAdsResponse:")
}

internal val adSlotEventProcessFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL)
    parameters("Lcom/spotify/ads/esperanto/proto/AdSlotEvent;")
    returns("Lp/wn0;")
}

internal val subBreakChangedEmitFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    parameters("Ljava/lang/Object;", "Lp/iwh;")
    returns("Ljava/lang/Object;")
    custom { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.INVOKE_STATIC &&
            (instruction as? ReferenceInstruction)?.reference?.toString()?.contains("SubBreakChangedResponse") == true
        } ?: false
    }
}

internal val clientTokenErrorFingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    strings("Received an error while retrieving client token")
}
