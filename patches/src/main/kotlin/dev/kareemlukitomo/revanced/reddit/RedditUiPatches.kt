package dev.kareemlukitomo.revanced.reddit

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val TARGET_PACKAGE = "com.reddit.frontpage"
private const val TARGET_VERSION = "2026.13.0"

private const val SCREENSHOT_BANNER_CLASS =
    "Lcom/reddit/sharing/screenshot/composables/ScreenshotTakenBannerKt\$ScreenshotTakenBanner\$1\$1;"
private const val SCREENSHOT_CONTROLLER_CLASS = "Lcom/reddit/sharing/screenshot/e;"
private const val SCREENSHOT_CONTROLLER_METHOD = "e"
private const val FEED_ADS_CLASS = "Lcom/reddit/feeds/impl/domain/ads/c;"
private const val FEED_ADS_METHOD = "a"
private const val COMMENT_ADS_CLASS =
    "Lcom/reddit/comments/presentation/CommentsViewModel\$LoadAdsCombinedCall\$2\$1;"
private const val SUSPEND_METHOD = "invokeSuspend"

@Suppress("unused")
val disableRedditScreenshotPopupPatch = bytecodePatch(
    name = "Disable Reddit screenshot popup",
    description = "Disable the screenshot taken banner in Reddit 2026.13.0.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    apply {
        shortCircuitVoidMethod(
            targetClass = SCREENSHOT_CONTROLLER_CLASS,
            methodName = SCREENSHOT_CONTROLLER_METHOD,
            parameterTypes = listOf(
                SCREENSHOT_CONTROLLER_CLASS,
                "Lup3/d;",
                "Landroid/widget/FrameLayout;",
                "Lcom/reddit/frontpage/presentation/detail/r;",
                "Lcom/reddit/frontpage/presentation/detail/r;",
                "Lcom/reddit/frontpage/presentation/detail/r;",
            ),
            patchName = "Disable Reddit screenshot popup",
            requireStatic = true,
        )
        shortCircuitObjectMethod(
            targetClass = SCREENSHOT_BANNER_CLASS,
            methodName = SUSPEND_METHOD,
            parameterTypes = listOf("Ljava/lang/Object;"),
            returnInstructions =
                """
                    sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
                    return-object v0
                """.trimIndent(),
            patchName = "Disable Reddit screenshot popup",
            requireStatic = false,
        )
    }
}

@Suppress("unused")
val hideRedditAdsPatch = bytecodePatch(
    name = "Hide Reddit ads",
    description = "Disable feed and comment ad entry points in Reddit 2026.13.0.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    apply {
        shortCircuitObjectMethod(
            targetClass = FEED_ADS_CLASS,
            methodName = FEED_ADS_METHOD,
            parameterTypes = listOf(
                "Lcom/reddit/listing/common/ListingType;",
                "Ljava/util/List;",
                "Ljava/util/List;",
                "Lmw1/b;",
                "Ljava/lang/String;",
                "Ljava/lang/String;",
                "Ljava/lang/String;",
                "Ldm3/a;",
            ),
            returnInstructions =
                """
                    move-object/from16 v0, p3
                    return-object v0
                """.trimIndent(),
            patchName = "Hide Reddit ads",
            requireStatic = false,
        )
        shortCircuitObjectMethod(
            targetClass = COMMENT_ADS_CLASS,
            methodName = SUSPEND_METHOD,
            parameterTypes = listOf("Ljava/lang/Object;"),
            returnInstructions =
                """
                    sget-object p0, Lkotlin/Unit;->a:Lkotlin/Unit;
                    return-object p0
                """.trimIndent(),
            patchName = "Hide Reddit ads",
            requireStatic = false,
        )
    }
}

private fun BytecodePatchContext.shortCircuitObjectMethod(
    targetClass: String,
    methodName: String,
    parameterTypes: List<String>,
    returnInstructions: String,
    patchName: String,
    requireStatic: Boolean,
) {
    val targetMethod = findMutableMethod(
        definingClass = targetClass,
        methodName = methodName,
        returnType = "Ljava/lang/Object;",
        parameterTypes = parameterTypes,
        patchName = patchName,
        requireStatic = requireStatic,
    )

    targetMethod.addInstructions(0, returnInstructions)
}

private fun BytecodePatchContext.shortCircuitVoidMethod(
    targetClass: String,
    methodName: String,
    parameterTypes: List<String>,
    patchName: String,
    requireStatic: Boolean,
) {
    val targetMethod = findMutableMethod(
        definingClass = targetClass,
        methodName = methodName,
        returnType = "V",
        parameterTypes = parameterTypes,
        patchName = patchName,
        requireStatic = requireStatic,
    )

    targetMethod.addInstructions(0, "return-void")
}

private fun BytecodePatchContext.findMutableMethod(
    definingClass: String,
    methodName: String,
    returnType: String,
    parameterTypes: List<String>,
    patchName: String,
    requireStatic: Boolean,
): app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod {
    val targetClass = classDefs[definingClass]
        ?: throw PatchException("Could not find Reddit class $definingClass")
    val mutableClass = classDefs.getOrReplaceMutable(targetClass)
    return mutableClass.methods.firstOrNull { method ->
        method.definingClass == definingClass &&
            method.name == methodName &&
            method.returnType == returnType &&
            method.parameterTypes.map(CharSequence::toString) == parameterTypes &&
            AccessFlags.STATIC.isSet(method.accessFlags) == requireStatic
    } ?: throw PatchException("Could not find Reddit method $definingClass->$methodName for $patchName")
}
