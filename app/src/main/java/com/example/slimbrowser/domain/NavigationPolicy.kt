package com.example.slimbrowser.domain

import java.net.URI
import java.util.Locale

enum class NavigationSource(val hasUserGesture: Boolean) {
    MANUAL_INPUT(hasUserGesture = true),
    LINK_CLICK(hasUserGesture = true),
    DEEP_LINK(hasUserGesture = true),
    REDIRECT(hasUserGesture = false),
    RESTORE(hasUserGesture = false),
    INTERNAL(hasUserGesture = false),
}

sealed interface NavigationDecision {
    data class Allow(val url: String) : NavigationDecision
    data class OpenExternal(val action: ExternalAction) : NavigationDecision
    data class Block(
        val reason: NavigationBlockReason,
        val externalRejection: ExternalLinkRejection? = null,
    ) : NavigationDecision
}

enum class NavigationBlockReason {
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    LOCAL_NETWORK_BLOCKED,
    NON_ROUTABLE_ADDRESS_BLOCKED,
    INTERNAL_URL_BLOCKED,
    EXTERNAL_LINK_BLOCKED,
    EXTERNAL_ACTION_REQUIRES_USER_GESTURE,
}

/**
 * User-directed navigation policy.
 *
 * Web-compatible schemes are loaded directly; every other syntactically valid URI is offered to
 * the platform through [ExternalLinkPolicy]. This deliberately accepts HTTP, private addresses,
 * file/content/data URIs and custom schemes so the browser makes a best effort for any user input.
 * Redirects are handled with the same best-effort policy as direct navigation. This is a personal
 * debugging browser, so a valid URI is not rejected merely because it came from a redirect or
 * uses a non-HTTP scheme.
 */
class NavigationPolicy(
    @Suppress("UNUSED_PARAMETER") val allowLocalNetwork: Boolean = true,
) {
    fun decide(
        input: String,
        source: NavigationSource = NavigationSource.LINK_CLICK,
    ): NavigationDecision {
        val normalized = UrlPolicy.normalizeForNavigation(input)
            ?: return blocked(NavigationBlockReason.INVALID_URL)
        val scheme = runCatching { URI(normalized).scheme?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return blocked(NavigationBlockReason.INVALID_URL)

        if (scheme in WEB_VIEW_SCHEMES) return NavigationDecision.Allow(normalized)

        val external = ExternalLinkPolicy.resolve(normalized)
        if (external is ExternalAction.Reject) {
            return NavigationDecision.Block(NavigationBlockReason.EXTERNAL_LINK_BLOCKED, external.reason)
        }
        // Do not gate redirects on a gesture. The user explicitly asked for a permissive browser,
        // and a site is allowed to hand navigation to its platform handler.
        return NavigationDecision.OpenExternal(external)
    }

    fun decide(
        resolution: InputResolution,
        source: NavigationSource = NavigationSource.MANUAL_INPUT,
    ): NavigationDecision = when (resolution) {
        is InputResolution.Url -> decide(resolution.normalized, source)
        is InputResolution.Search -> decide(resolution.url, source)
        is InputResolution.Invalid -> blocked(NavigationBlockReason.INVALID_URL)
    }

    fun evaluate(input: String, source: NavigationSource = NavigationSource.LINK_CLICK): NavigationDecision =
        decide(input, source)

    fun isAllowed(input: String, source: NavigationSource = NavigationSource.LINK_CLICK): Boolean =
        decide(input, source) is NavigationDecision.Allow

    private fun blocked(reason: NavigationBlockReason) = NavigationDecision.Block(reason)

    private companion object {
        val WEB_VIEW_SCHEMES = setOf(
            "http",
            "https",
            "about",
            "data",
            "blob",
            "file",
            "content",
            "javascript",
        )
    }
}
