package dev.kareemlukitomo.revanced.x

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

private const val TARGET_PACKAGE = "com.twitter.android"
private const val TARGET_VERSION = "10.86.0-release.0"

private const val DOMAIN_CLASS = "Lcom/twitter/model/core/e;"
private const val DOMAIN_METHOD = "v"
private const val DOMAIN_RETURN_TYPE = "Ljava/lang/String;"
private const val ORIGINAL_DOMAIN_FORMAT = "https://x.com/%1\$s/status/%2\$d"
private const val CUSTOM_DOMAIN_FORMAT = "https://nitter.kareem.one/%1\$s/status/%2\$d"

private const val SANITIZE_CLASS = "Lcom/twitter/share/api/targets/u;"
private const val SANITIZE_METHOD = "a"
private const val INSTANCE_FIELD = "INSTANCE"

private const val JSON_HOOK_PATCH_CLASS =
    "Lapp/revanced/extension/twitter/patches/hook/json/JsonHookPatch;"
private const val JSON_HOOK_PATCH_METHOD = "<clinit>"
private const val BASE_JSON_HOOK_CLASS =
    "Lapp/revanced/extension/twitter/patches/hook/json/BaseJsonHook;"
private const val HIDE_ADS_HOOK_CLASS =
    "Lapp/revanced/extension/twitter/patches/hook/patch/ads/HideAdsHook;"
private const val RECOMMENDED_USERS_HOOK_CLASS =
    "Lapp/revanced/extension/twitter/patches/hook/patch/recommendation/RecommendedUsersHook;"

@Suppress("unused")
val changeTwitterShareDomainPatch = bytecodePatch(
    name = "Change Twitter share domain",
    description = "Rewrite shared X links to nitter.kareem.one for Twitter/X 10.86.0.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    execute {
        val targetClass = classDefs[DOMAIN_CLASS]
            ?: throw PatchException("Could not find target class $DOMAIN_CLASS")
        val mutableClass = classDefs.getOrReplaceMutable(targetClass)
        val targetMethod = mutableClass.methods.firstOrNull(::isDomainMethod)
            ?: throw PatchException("Could not find share-domain method in $DOMAIN_CLASS")
        val implementation = targetMethod.implementation
            ?: throw PatchException("Share-domain method has no implementation")

        var sawOriginalFormat = false
        var sawCustomFormat = false
        implementation.instructions.forEachIndexed { index, instruction ->
            val referenceInstruction = instruction as? ReferenceInstruction ?: return@forEachIndexed
            val stringReference = referenceInstruction.reference as? StringReference ?: return@forEachIndexed
            when (stringReference.string) {
                ORIGINAL_DOMAIN_FORMAT -> {
                    val register = (instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("Share-domain instruction does not expose its target register")
                    implementation.replaceInstruction(
                        index,
                        BuilderInstruction31c(
                            Opcode.CONST_STRING_JUMBO,
                            register,
                            ImmutableStringReference(CUSTOM_DOMAIN_FORMAT),
                        ),
                    )
                    sawOriginalFormat = true
                }

                CUSTOM_DOMAIN_FORMAT -> sawCustomFormat = true
            }
        }

        if (!sawOriginalFormat && !sawCustomFormat) {
            throw PatchException("Could not rewrite the share-domain format string")
        }
    }
}

@Suppress("unused")
val sanitizeTwitterShareLinksPatch = bytecodePatch(
    name = "Sanitize Twitter share links",
    description = "Stop Twitter/X 10.86.0 from appending tracking query parameters to shared links.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    execute {
        val targetClass = classDefs[SANITIZE_CLASS]
            ?: throw PatchException("Could not find sanitize class $SANITIZE_CLASS")
        val mutableClass = classDefs.getOrReplaceMutable(targetClass)
        val targetMethod = mutableClass.methods.firstOrNull(::isSanitizeMethod)
            ?: throw PatchException("Could not find sanitize method in $SANITIZE_CLASS")

        val implementation = targetMethod.implementation
            ?: throw PatchException("Sanitize method has no implementation")
        val parameterRegisters = parameterRegisterCount(targetMethod)
        val linkRegister = if (AccessFlags.STATIC.isSet(targetMethod.accessFlags)) 0 else 1
        if (linkRegister >= parameterRegisters) {
            throw PatchException("Unexpected register layout for sanitize method")
        }

        targetMethod.implementation = MutableMethodImplementation(parameterRegisters).apply {
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, linkRegister))
        }

        if (implementation.instructions.isEmpty()) {
            throw PatchException("Sanitize method unexpectedly had no instructions")
        }
    }
}

@Suppress("unused")
val zFixTwitterJsonHookCompatibilityPatch = bytecodePatch(
    name = "Z Fix Twitter JSON hook compatibility",
    description = "Hotfix the upstream ReVanced X JSON hook crash on Twitter/X 10.86.0.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    execute {
        val targetClass = classDefs[JSON_HOOK_PATCH_CLASS] ?: return@execute
        if (classDefs[HIDE_ADS_HOOK_CLASS] == null || classDefs[RECOMMENDED_USERS_HOOK_CLASS] == null) {
            return@execute
        }

        val mutableClass = classDefs.getOrReplaceMutable(targetClass)
        val targetMethod = mutableClass.methods.firstOrNull(::isJsonHookInitializer) ?: return@execute
        val implementation = targetMethod.implementation ?: return@execute
        val mutableImplementation = MutableMethodImplementation(implementation)

        var baseHookReferences = 0
        mutableImplementation.instructions.forEachIndexed { index, instruction ->
            val referenceInstruction = instruction as? ReferenceInstruction ?: return@forEachIndexed
            val fieldReference = referenceInstruction.reference as? FieldReference ?: return@forEachIndexed
            if (!isBaseJsonHookInstanceField(fieldReference)) return@forEachIndexed

            val register = (instruction as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("JSON hook instruction does not expose its target register")
            val replacementClass = when (baseHookReferences) {
                0 -> HIDE_ADS_HOOK_CLASS
                1 -> RECOMMENDED_USERS_HOOK_CLASS
                else -> throw PatchException(
                    "Unexpected extra $BASE_JSON_HOOK_CLASS.$INSTANCE_FIELD reference in $JSON_HOOK_PATCH_CLASS",
                )
            }

            mutableImplementation.replaceInstruction(
                index,
                BuilderInstruction21c(
                    Opcode.SGET_OBJECT,
                    register,
                    ImmutableFieldReference(replacementClass, INSTANCE_FIELD, replacementClass),
                ),
            )
            baseHookReferences++
        }

        when (baseHookReferences) {
            0 -> return@execute
            2 -> targetMethod.implementation = mutableImplementation
            else -> throw PatchException(
                "Expected 0 or 2 $BASE_JSON_HOOK_CLASS.$INSTANCE_FIELD references, found $baseHookReferences",
            )
        }
    }
}

private fun isDomainMethod(method: Method): Boolean {
    if (method.definingClass != DOMAIN_CLASS) return false
    if (method.name != DOMAIN_METHOD) return false
    if (method.returnType != DOMAIN_RETURN_TYPE) return false
    val params = method.parameterTypes.map(CharSequence::toString)
    return params == listOf("J", "Ljava/lang/String;")
}

private fun isSanitizeMethod(method: Method): Boolean {
    if (method.definingClass != SANITIZE_CLASS) return false
    if (method.name != SANITIZE_METHOD) return false
    if (method.returnType != DOMAIN_RETURN_TYPE) return false
    val params = method.parameterTypes.map(CharSequence::toString)
    return params == listOf(
        "Ljava/lang/String;",
        "Lcom/twitter/share/api/targets/t;",
        "Ljava/lang/String;",
    )
}

private fun isJsonHookInitializer(method: Method): Boolean {
    if (method.definingClass != JSON_HOOK_PATCH_CLASS) return false
    if (method.name != JSON_HOOK_PATCH_METHOD) return false
    if (method.returnType != "V") return false
    return method.parameterTypes.isEmpty()
}

private fun isBaseJsonHookInstanceField(fieldReference: FieldReference): Boolean {
    if (fieldReference.definingClass != BASE_JSON_HOOK_CLASS) return false
    if (fieldReference.name != INSTANCE_FIELD) return false
    return fieldReference.type == BASE_JSON_HOOK_CLASS
}

private fun parameterRegisterCount(method: Method): Int {
    val implicitThisRegisters = if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    return method.parameterTypes.fold(implicitThisRegisters) { count, parameterType ->
        when (parameterType.toString()) {
            "J", "D" -> count + 2
            else -> count + 1
        }
    }
}
