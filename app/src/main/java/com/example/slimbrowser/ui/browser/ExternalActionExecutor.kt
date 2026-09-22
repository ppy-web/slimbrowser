package com.example.slimbrowser.ui.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.slimbrowser.R
import com.example.slimbrowser.domain.ExternalAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Executes only the small, typed set of external actions approved by the domain layer.
 *
 * Typed phone/mail/map actions retain their dedicated behavior. Other valid custom schemes are
 * delegated to the system with ACTION_VIEW so user-entered links get the best available handler.
 */
class ExternalActionExecutor(
    private val activity: Activity,
    private val callback: Callback = Callback { },
) : AutoCloseable {
    fun interface Callback {
        fun onResult(result: Result)
    }

    sealed interface Result {
        data class Started(val action: ExternalAction) : Result
        data class Rejected(val action: ExternalAction.Reject) : Result
        data class Invalid(val action: ExternalAction) : Result
        data class Cancelled(val action: ExternalAction) : Result
        data class Unavailable(val action: ExternalAction) : Result
        data class Failed(val action: ExternalAction, val cause: Throwable) : Result
    }

    private var confirmationDialog: AlertDialog? = null

    fun execute(action: ExternalAction) {
        if (!activityCanShowUi()) {
            callback.onResult(Result.Invalid(action))
            return
        }

        if (action is ExternalAction.Reject) {
            showBlockedFeedback()
            callback.onResult(Result.Rejected(action))
            return
        }

        val intent = buildStrictIntent(action)
        if (intent == null) {
            showBlockedFeedback()
            callback.onResult(Result.Invalid(action))
            return
        }

        if (action is ExternalAction.OpenGeo || action is ExternalAction.OpenMarket) {
            showConfirmation(action, intent)
        } else {
            launch(action, intent)
        }
    }

    private fun showConfirmation(action: ExternalAction, intent: Intent) {
        confirmationDialog?.dismiss()
        val messageRes = when (action) {
            is ExternalAction.OpenGeo -> R.string.external_geo_confirmation
            is ExternalAction.OpenMarket -> R.string.external_market_confirmation
            else -> return
        }
        confirmationDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.external_action_confirmation_title)
            .setMessage(activity.getString(messageRes, action.uri.orEmpty()))
            .setPositiveButton(R.string.continue_to_external_app) { _, _ ->
                launch(action, intent)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                callback.onResult(Result.Cancelled(action))
            }
            .setOnCancelListener {
                callback.onResult(Result.Cancelled(action))
            }
            .create()
            .also(AlertDialog::show)
    }

    private fun launch(action: ExternalAction, intent: Intent) {
        try {
            activity.startActivity(intent)
            callback.onResult(Result.Started(action))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.no_external_app, Toast.LENGTH_SHORT).show()
            callback.onResult(Result.Unavailable(action))
        } catch (error: SecurityException) {
            Toast.makeText(activity, R.string.external_action_failed, Toast.LENGTH_SHORT).show()
            callback.onResult(Result.Failed(action, error))
        } catch (error: RuntimeException) {
            Toast.makeText(activity, R.string.external_action_failed, Toast.LENGTH_SHORT).show()
            callback.onResult(Result.Failed(action, error))
        }
    }

    private fun buildStrictIntent(action: ExternalAction): Intent? {
        val uri = action.uri?.let(Uri::parse) ?: return null
        if (action is ExternalAction.OpenUri && uri.scheme.equals("intent", ignoreCase = true)) {
            return runCatching {
                Intent.parseUri(action.uri, Intent.URI_INTENT_SCHEME).apply {
                    component = null
                    selector = null
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            }.getOrNull()
        }
        val expected = when (action) {
            is ExternalAction.Dial -> ExpectedIntent(Intent.ACTION_DIAL, "tel")
            is ExternalAction.SendEmail -> ExpectedIntent(Intent.ACTION_SENDTO, "mailto")
            is ExternalAction.SendSms -> ExpectedIntent(Intent.ACTION_SENDTO, "sms")
            is ExternalAction.OpenGeo -> ExpectedIntent(Intent.ACTION_VIEW, "geo")
            is ExternalAction.OpenMarket -> ExpectedIntent(Intent.ACTION_VIEW, "market")
            is ExternalAction.OpenUri -> ExpectedIntent(Intent.ACTION_VIEW, uri.scheme.orEmpty())
            is ExternalAction.Reject -> return null
        }
        if (!uri.scheme.equals(expected.scheme, ignoreCase = true)) return null
        return Intent(expected.action, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    private fun showBlockedFeedback() {
        Toast.makeText(activity, R.string.external_link_blocked, Toast.LENGTH_SHORT).show()
    }

    private fun activityCanShowUi(): Boolean = !activity.isFinishing && !activity.isDestroyed

    override fun close() {
        confirmationDialog?.dismiss()
        confirmationDialog = null
    }

    private data class ExpectedIntent(
        val action: String,
        val scheme: String,
    )
}
