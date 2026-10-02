package app.revanced.patches.spotify

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.revanced.util.forEachInstructionAsSequence
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val EXTENSION_CLASS = "Lapp/revanced/extension/signature/SignatureSpoofPatch;"

private val TARGET_METHODS = listOf(
    ImmutableMethodReference("Landroid/content/pm/PackageManager;","getPackageInfo",listOf("Ljava/lang/String;","I"),"Landroid/content/pm/PackageInfo;"),
    ImmutableMethodReference("Landroid/app/ApplicationPackageManager;","getPackageInfo",listOf("Ljava/lang/String;","I"),"Landroid/content/pm/PackageInfo;"),
)

// Generate replacement smali
private fun buildReplacement(desc: String, parameterTypes: List<String>, registerString: String): String {
    val paramStr = parameterTypes.joinToString("")
    return "invoke-static { $registerString }, $EXTENSION_CLASS->${desc}(Landroid/content/pm/PackageManager;$paramStr)Landroid/content/pm/PackageInfo;"
}

@Suppress("unused")
val signatureSpoofPatch = bytecodePatch(
    name = "Spoof APK signature",
    description = "Intercepts all PackageManager calls returning cert info for Spotify.",
) {
    compatibleWith("com.spotify.music"("9.1.62.1601"))
    extendWith("extensions/signature-spoof.mpe")

    execute {
        forEachInstructionAsSequence(
            match = { classDef, _, instruction, instructionIndex ->
                if (classDef.type.startsWith("Lapp/revanced/")) return@forEachInstructionAsSequence null
                val ref = instruction.getReference<MethodReference>() ?: return@forEachInstructionAsSequence null
                val matched = TARGET_METHODS.find { MethodUtil.methodSignaturesMatch(it, ref) }
                    ?: return@forEachInstructionAsSequence null
                Triple(instruction as Instruction35c, instructionIndex, Pair(ref, matched))
            },
            transform = { method, entry ->
                val (instruction, instructionIndex, pair) = entry
                val (actualRef, matchedRef) = pair

                // Use the ACTUAL method name (might be different for ApplicationPackageManager vs PackageManager)
                val replacementName = actualRef.name

                val parameterString = actualRef.parameterTypes.joinToString(separator = "")
                val registerString = with(instruction) {
                    listOf(registerC, registerD, registerE, registerF)
                        .take(registerCount)
                        .joinToString(", ") { "v$it" }
                }

                method.replaceInstruction(
                    instructionIndex,
                    "invoke-static { $registerString }, $EXTENSION_CLASS->$replacementName(Landroid/content/pm/PackageManager;$parameterString)Landroid/content/pm/PackageInfo;",
                )
            },
        )
    }
}
