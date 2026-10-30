package com.autosmsforwarder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.ui.SettingsActions
import com.autosmsforwarder.ui.SettingsContent
import com.autosmsforwarder.ui.SettingsUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun separateRouteSectionsShowKeywordFieldsOnlyForKeywordMode() {
        var state by mutableStateOf(SettingsUiState(loaded = true))
        composeRule.setContent { SettingsContent(state, SettingsActions()) }

        composeRule.onNodeWithText("SMS forwarding").assertExists()
        composeRule.onNodeWithText("Web archive").assertExists()
        composeRule.onNodeWithText("Forwarding keywords").assertDoesNotExist()
        composeRule.onNodeWithText("Web archive keywords").assertDoesNotExist()
        composeRule.onNodeWithText("Save settings").assertIsEnabled()

        composeRule.runOnUiThread {
            state = state.copy(forwardMatchMode = MatchMode.KEYWORDS, webMatchMode = MatchMode.KEYWORDS)
        }
        composeRule.onNodeWithText("Forwarding keywords").assertExists()
        composeRule.onNodeWithText("Web archive keywords").assertExists()
    }

    @Test fun webSwitchCallsOnlyItsOwnAction() {
        var webEnabled = false
        var forwardingChanged = false
        composeRule.setContent {
            SettingsContent(SettingsUiState(loaded = true), SettingsActions(
                onWebEnabled = { webEnabled = it },
                onForwardingEnabled = { forwardingChanged = true }
            ))
        }

        composeRule.onNodeWithContentDescription("Web archive").performClick()
        composeRule.runOnIdle {
            assertTrue(webEnabled)
            assertTrue(!forwardingChanged)
        }
    }

    @Test fun optionalSourceFieldsCanBeEnteredAndClearedWithoutBlockingSave() {
        var state by mutableStateOf(SettingsUiState(loaded = true))
        var saved = false
        composeRule.setContent {
            SettingsContent(state, SettingsActions(
                onAppSourcePhoneNumber = { state = state.copy(appSourcePhoneNumber = it) },
                onAppSourceName = { state = state.copy(appSourceName = it) },
                onSave = { saved = true }
            ))
        }
        composeRule.onNodeWithText("App source phone number (optional)").performScrollTo().performTextInput("+919876543210")
        composeRule.onNodeWithText("App source name (optional)").performScrollTo().performTextInput("Home phone")
        composeRule.runOnIdle {
            assertTrue(state.appSourcePhoneNumber == "+919876543210")
            assertTrue(state.appSourceName == "Home phone")
            state = state.copy(appSourcePhoneNumber = "", appSourceName = "")
        }
        composeRule.onNodeWithText("Save settings").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(saved) }
    }

    @Test fun savedSettingsShowVisibleConfirmation() {
        composeRule.setContent {
            SettingsContent(SettingsUiState(loaded = true, saved = true), SettingsActions())
        }
        composeRule.onNodeWithText("Settings saved").assertExists()
    }
}
