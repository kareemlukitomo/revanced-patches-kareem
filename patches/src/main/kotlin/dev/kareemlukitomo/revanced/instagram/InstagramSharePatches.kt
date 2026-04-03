package dev.kareemlukitomo.revanced.instagram

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import dev.kareemlukitomo.revanced.shared.replaceExactStrings

private const val TARGET_PACKAGE = "com.instagram.android"
private const val TARGET_VERSION = "423.0.0.47.66"
private const val CUSTOM_DOMAIN = "https://kittygram.kareem.one"

private val INSTAGRAM_DOMAIN_REPLACEMENTS = linkedMapOf(
    "https://instagram.com/p/%s" to "$CUSTOM_DOMAIN/p/%s",
    "https://www.instagram.com/p/" to "$CUSTOM_DOMAIN/p/",
    "https://www.instagram.com/%s" to "$CUSTOM_DOMAIN/%s",
    "http://instagram.com/p/{media_id}" to "$CUSTOM_DOMAIN/p/{media_id}",
    "http://instagram.com/p/{media_id}/{slug}?extra={?extra}" to "$CUSTOM_DOMAIN/p/{media_id}/{slug}?extra={?extra}",
    "http://instagram.com/p/{media_id}?comment_id={?comment_id}" to "$CUSTOM_DOMAIN/p/{media_id}?comment_id={?comment_id}",
    "http://instagram.com/p/{media_id}?extra={?extra}" to "$CUSTOM_DOMAIN/p/{media_id}?extra={?extra}",
    "http://www.instagram.com/p/{media_id}" to "$CUSTOM_DOMAIN/p/{media_id}",
    "http://www.instagram.com/p/{media_id}/{slug}" to "$CUSTOM_DOMAIN/p/{media_id}/{slug}",
    "http://www.instagram.com/p/{media_id}/{slug}?extra={?extra}" to "$CUSTOM_DOMAIN/p/{media_id}/{slug}?extra={?extra}",
    "http://www.instagram.com/p/{media_id}?comment_id={?comment_id}" to "$CUSTOM_DOMAIN/p/{media_id}?comment_id={?comment_id}",
    "http://www.instagram.com/p/{media_id}?extra={?extra}" to "$CUSTOM_DOMAIN/p/{media_id}?extra={?extra}",
    "http://instagram.com/reel/{clip_id}" to "$CUSTOM_DOMAIN/reel/{clip_id}",
    "http://instagram.com/reel/{clip_id}/{slug}?extra={?extra}" to "$CUSTOM_DOMAIN/reel/{clip_id}/{slug}?extra={?extra}",
    "http://instagram.com/reel/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reel/{clip_id}?extra={?extra}",
    "http://instagram.com/reels/videos/{clip_id}" to "$CUSTOM_DOMAIN/reels/videos/{clip_id}",
    "http://instagram.com/reels/videos/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reels/videos/{clip_id}?extra={?extra}",
    "http://instagram.com/reels/{clip_id}" to "$CUSTOM_DOMAIN/reels/{clip_id}",
    "http://instagram.com/reels/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reels/{clip_id}?extra={?extra}",
    "http://www.instagram.com/reel/{clip_id}" to "$CUSTOM_DOMAIN/reel/{clip_id}",
    "http://www.instagram.com/reel/{clip_id}/{slug}?extra={?extra}" to "$CUSTOM_DOMAIN/reel/{clip_id}/{slug}?extra={?extra}",
    "http://www.instagram.com/reel/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reel/{clip_id}?extra={?extra}",
    "http://www.instagram.com/reels/videos/{clip_id}" to "$CUSTOM_DOMAIN/reels/videos/{clip_id}",
    "http://www.instagram.com/reels/videos/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reels/videos/{clip_id}?extra={?extra}",
    "http://www.instagram.com/reels/{clip_id}" to "$CUSTOM_DOMAIN/reels/{clip_id}",
    "http://www.instagram.com/reels/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reels/{clip_id}?extra={?extra}",
    "http://www.instagram.com/{user_name}/p/{media_id}" to "$CUSTOM_DOMAIN/{user_name}/p/{media_id}",
    "http://instagram.com/p/{media_id}/{slug}" to "$CUSTOM_DOMAIN/p/{media_id}/{slug}",
    "http://instagram.com/{user_name}/p/{media_id}" to "$CUSTOM_DOMAIN/{user_name}/p/{media_id}",
    "http://www.instagram.com/{user_name}/p/{media_id}/{slug}" to "$CUSTOM_DOMAIN/{user_name}/p/{media_id}/{slug}",
)

private val INSTAGRAM_SANITIZE_REPLACEMENTS = linkedMapOf(
    "http://instagram.com/p/{media_id}/{slug}?extra={?extra}" to "http://instagram.com/p/{media_id}/{slug}",
    "http://instagram.com/p/{media_id}?comment_id={?comment_id}" to "http://instagram.com/p/{media_id}",
    "http://instagram.com/p/{media_id}?extra={?extra}" to "http://instagram.com/p/{media_id}",
    "http://www.instagram.com/p/{media_id}/{slug}?extra={?extra}" to "http://www.instagram.com/p/{media_id}/{slug}",
    "http://www.instagram.com/p/{media_id}?comment_id={?comment_id}" to "http://www.instagram.com/p/{media_id}",
    "http://www.instagram.com/p/{media_id}?extra={?extra}" to "http://www.instagram.com/p/{media_id}",
    "http://instagram.com/reel/{clip_id}/{slug}?extra={?extra}" to "http://instagram.com/reel/{clip_id}/{slug}",
    "http://instagram.com/reel/{clip_id}?extra={?extra}" to "http://instagram.com/reel/{clip_id}",
    "http://instagram.com/reels/videos/{clip_id}?extra={?extra}" to "http://instagram.com/reels/videos/{clip_id}",
    "http://instagram.com/reels/{clip_id}?extra={?extra}" to "http://instagram.com/reels/{clip_id}",
    "http://www.instagram.com/reel/{clip_id}/{slug}?extra={?extra}" to "http://www.instagram.com/reel/{clip_id}/{slug}",
    "http://www.instagram.com/reel/{clip_id}?extra={?extra}" to "http://www.instagram.com/reel/{clip_id}",
    "http://www.instagram.com/reels/videos/{clip_id}?extra={?extra}" to "http://www.instagram.com/reels/videos/{clip_id}",
    "http://www.instagram.com/reels/{clip_id}?extra={?extra}" to "http://www.instagram.com/reels/{clip_id}",
    "$CUSTOM_DOMAIN/p/{media_id}/{slug}?extra={?extra}" to "$CUSTOM_DOMAIN/p/{media_id}/{slug}",
    "$CUSTOM_DOMAIN/p/{media_id}?comment_id={?comment_id}" to "$CUSTOM_DOMAIN/p/{media_id}",
    "$CUSTOM_DOMAIN/p/{media_id}?extra={?extra}" to "$CUSTOM_DOMAIN/p/{media_id}",
    "$CUSTOM_DOMAIN/reel/{clip_id}/{slug}?extra={?extra}" to "$CUSTOM_DOMAIN/reel/{clip_id}/{slug}",
    "$CUSTOM_DOMAIN/reel/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reel/{clip_id}",
    "$CUSTOM_DOMAIN/reels/videos/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reels/videos/{clip_id}",
    "$CUSTOM_DOMAIN/reels/{clip_id}?extra={?extra}" to "$CUSTOM_DOMAIN/reels/{clip_id}",
)

@Suppress("unused")
val changeInstagramShareDomainPatch = bytecodePatch(
    name = "Change Instagram share domain",
    description = "Rewrite shared Instagram links to kittygram.kareem.one for Instagram 423.0.0.47.66.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    execute {
        val replacementCount = classDefs.replaceExactStrings(INSTAGRAM_DOMAIN_REPLACEMENTS)
        if (replacementCount == 0) {
            throw PatchException("Could not rewrite any Instagram share-link strings")
        }
    }
}

@Suppress("unused")
val sanitizeInstagramShareLinksPatch = bytecodePatch(
    name = "Sanitize Instagram share links",
    description = "Strip tracking query parameters from shared Instagram links for Instagram 423.0.0.47.66.",
) {
    compatibleWith(TARGET_PACKAGE(TARGET_VERSION))

    execute {
        val replacementCount = classDefs.replaceExactStrings(INSTAGRAM_SANITIZE_REPLACEMENTS)
        if (replacementCount == 0) {
            throw PatchException("Could not rewrite any Instagram tracking query strings")
        }
    }
}
