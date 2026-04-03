package dev.kareemlukitomo.revanced.reddit

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.iface.Method
import dev.kareemlukitomo.revanced.shared.replaceExactStrings

private const val TARGET_PACKAGE = "com.reddit.frontpage"
private const val TARGET_VERSION = "2026.13.0"
private const val CUSTOM_DOMAIN = "https://redlib.kareem.one"
private const val URL_FORMATTER_CLASS = "Lvu3/f;"
private const val URL_FORMATTER_METHOD = "a"

private val REDDIT_SHARE_REPLACEMENTS = linkedMapOf(
    "https://www.reddit.com" to CUSTOM_DOMAIN,
    "https://www.reddit.com/" to "$CUSTOM_DOMAIN/",
    "https://www.reddit.com/r/" to "$CUSTOM_DOMAIN/r/",
    "https://www.reddit.com/u/" to "$CUSTOM_DOMAIN/u/",
    "https://www.reddit.com/user/" to "$CUSTOM_DOMAIN/user/",
    "https://www.reddit.com/u/{username}/comments" to "$CUSTOM_DOMAIN/u/{username}/comments",
    "https://www.reddit.com/user/{username}/comments" to "$CUSTOM_DOMAIN/user/{username}/comments",
    "https://reddit.com" to CUSTOM_DOMAIN,
    "https://reddit.com/" to "$CUSTOM_DOMAIN/",
    "https://reddit.com/r/" to "$CUSTOM_DOMAIN/r/",
    "https://reddit.com/u/" to "$CUSTOM_DOMAIN/u/",
    "https://reddit.com/user/" to "$CUSTOM_DOMAIN/user/",
    "https://reddit.com/u/{username}/comments" to "$CUSTOM_DOMAIN/u/{username}/comments",
    "https://reddit.com/user/{username}/comments" to "$CUSTOM_DOMAIN/user/{username}/comments",
    "https://reddit.com%s" to "$CUSTOM_DOMAIN%s",
    "https://www.reddit.com/comments/{link_id}" to "$CUSTOM_DOMAIN/comments/{link_id}",
    "https://www.reddit.com/comments/{link_id}/" to "$CUSTOM_DOMAIN/comments/{link_id}/",
    "https://www.reddit.com/comments/{link_id}/{title}" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}",
    "https://www.reddit.com/comments/{link_id}/{title}/" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}/",
    "https://www.reddit.com/comments/{link_id}/{title}/{comment}" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}/{comment}",
    "https://www.reddit.com/comments/{link_id}/{title}/{comment}/" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}/{comment}/",
    "https://www.reddit.com/{prefix}/{subreddit_name}/comments/{link_id}" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}",
    "https://www.reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/",
    "https://www.reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}",
    "https://www.reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}/" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}/",
    "https://www.reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}",
    "https://www.reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}/" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}/",
    "https://reddit.com/comments/{link_id}" to "$CUSTOM_DOMAIN/comments/{link_id}",
    "https://reddit.com/comments/{link_id}/" to "$CUSTOM_DOMAIN/comments/{link_id}/",
    "https://reddit.com/comments/{link_id}/{title}" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}",
    "https://reddit.com/comments/{link_id}/{title}/" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}/",
    "https://reddit.com/comments/{link_id}/{title}/{comment}" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}/{comment}",
    "https://reddit.com/comments/{link_id}/{title}/{comment}/" to "$CUSTOM_DOMAIN/comments/{link_id}/{title}/{comment}/",
    "https://reddit.com/{prefix}/{subreddit_name}/comments/{link_id}" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}",
    "https://reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/",
    "https://reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}",
    "https://reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}/" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}/",
    "https://reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}",
    "https://reddit.com/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}/" to "$CUSTOM_DOMAIN/{prefix}/{subreddit_name}/comments/{link_id}/{title}/{comment}/",
    "https://redd.it/{link_id}" to "$CUSTOM_DOMAIN/comments/{link_id}",
    "https://reddit.com/tb/{link_id}" to "$CUSTOM_DOMAIN/tb/{link_id}",
)

@Suppress("unused")
val sanitizeRedditShareLinksPatch = bytecodePatch(
    name = "Sanitize Reddit share links",
    description = "Stop Reddit 2026.13.0 from appending tracking query parameters to shared links.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    apply {
        val targetClass = classDefs[URL_FORMATTER_CLASS]
            ?: throw PatchException("Could not find URL formatter class $URL_FORMATTER_CLASS")
        val mutableClass = classDefs.getOrReplaceMutable(targetClass)
        val targetMethod = mutableClass.methods.firstOrNull(::isUrlFormatterMethod)
            ?: throw PatchException("Could not find URL formatter method in $URL_FORMATTER_CLASS")

        val registerCount = parameterRegisterCount(targetMethod)
        targetMethod.implementation = MutableMethodImplementation(registerCount).apply {
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 2))
        }
    }
}

@Suppress("unused")
val changeRedditShareDomainPatch = bytecodePatch(
    name = "Change Reddit share domain",
    description = "Rewrite shared Reddit links to redlib.kareem.one for Reddit 2026.13.0.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    apply {
        val replacementCount = classDefs.replaceExactStrings(REDDIT_SHARE_REPLACEMENTS)
        if (replacementCount == 0) {
            throw PatchException("Could not rewrite any Reddit share-link strings")
        }
    }
}

private fun isUrlFormatterMethod(method: Method): Boolean {
    if (method.definingClass != URL_FORMATTER_CLASS) return false
    if (method.name != URL_FORMATTER_METHOD) return false
    if (method.returnType != "Ljava/lang/String;") return false
    if (!AccessFlags.STATIC.isSet(method.accessFlags)) return false
    val params = method.parameterTypes.map(CharSequence::toString)
    return params == listOf(
        "Lhc3/x;",
        "Lcom/reddit/sharing/SharingNavigator\$ShareTrigger;",
        "Ljava/lang/String;",
        "Z",
    )
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
