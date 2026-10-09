package app.drift.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.designsystem.DriftTheme
import app.drift.model.repository.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h2000dp-xhdpi")
class LearnTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyRowLeadsSomewhere() {
        val opened = mutableListOf<String>()
        rule.setContent {
            DriftTheme(ThemeMode.DARK, reducedMotion = true) {
                LearnScreen(
                    onOpenArticle = { opened += "article$it" },
                    onOpenMethodology = { opened += "methodology" },
                    onOpenBodyClock = { opened += "body" },
                    onOpenSettings = { opened += "settings" },
                )
            }
        }
        rule.onNodeWithTag("learn_article_0").performClick()
        rule.onNodeWithTag("learn_article_6").performScrollTo().performClick()
        rule.onNodeWithTag("learn_body_clock").performScrollTo().performClick()
        rule.onNodeWithTag("learn_methodology").performScrollTo().performClick()
        assertThat(opened).containsExactly("article0", "article6", "body", "methodology").inOrder()
    }

    @Test fun everyArticleHasABodyAndBackWorks() {
        var back = 0
        val index = androidx.compose.runtime.mutableStateOf(0)
        rule.setContent { DriftTheme(ThemeMode.DARK, reducedMotion = true) { ArticleScreen(index.value, onBack = { back++ }) } }
        for (i in articles.indices) {
            index.value = i
            rule.waitForIdle()
            rule.onNodeWithTag("learn_article_body").assertExists()
        }
        rule.onNodeWithText("Habits that help").assertExists()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Back")).performClick()
        assertThat(back).isEqualTo(1)
    }
}
