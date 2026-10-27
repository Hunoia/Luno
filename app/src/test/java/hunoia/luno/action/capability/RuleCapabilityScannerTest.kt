package hunoia.luno.action.capability

import hunoia.luno.action.model.ActionLibraryEntry
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.core.domain.Capability
import hunoia.luno.action.model.RUN_RULE_TYPE_ID
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.WhenGroup
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCapabilityScannerTest {

    private fun action(typeId: String, params: JsonObject = JsonObject(emptyMap())) =
        RuleStep(kind = RuleStepKind.ACTION, action = StoredAction(typeId, params))

    private fun rule(id: String, vararg steps: RuleStep) = AutomationRule(
        id = id,
        name = id,
        condition = WhenGroup(),
        effect = RuleEffect(steps.toList()),
    )

    @Test
    fun `scanner expands disabled steps library refs and runRule targets`() {
        val library = ActionLibrarySettings(
            entries = listOf(
                ActionLibraryEntry(id = "notify", typeId = "system.notify"),
            ),
        )
        val target = rule("target", action("accessibility.tap"))
        val root = rule(
            "root",
            action("accessibility.back"),
            action("accessibility.home").copy(enabled = false),
            action(
                "internal.libraryRef",
                buildJsonObject { put("entryId", "notify") },
            ),
            action(
                RUN_RULE_TYPE_ID,
                buildJsonObject { put("ruleId", "target") },
            ),
        )

        assertEquals(
            setOf(Capability.Accessibility, Capability.Notification),
            RuleCapabilityScanner.requiredCapabilities(root, listOf(root, target), library),
        )
    }

    @Test
    fun `library ref cycle does not recurse forever`() {
        val library = ActionLibrarySettings(
            entries = listOf(
                ActionLibraryEntry(
                    id = "a",
                    typeId = "internal.libraryRef",
                    params = buildJsonObject { put("entryId", "b") },
                ),
                ActionLibraryEntry(
                    id = "b",
                    typeId = "internal.libraryRef",
                    params = buildJsonObject { put("entryId", "a") },
                ),
                ActionLibraryEntry(id = "c", typeId = "system.notify"),
            ),
        )
        val root = rule(
            "root",
            action("internal.libraryRef", buildJsonObject { put("entryId", "a") }),
            action("internal.libraryRef", buildJsonObject { put("entryId", "c") }),
        )

        assertEquals(
            setOf(Capability.Notification),
            RuleCapabilityScanner.requiredCapabilities(root, listOf(root), library),
        )
    }

    @Test
    fun `notification access includes constraints and runRule target if branches`() {
        val notification = WhenGroup(
            items = listOf(ConditionItem(type = ConditionType.NOTIFICATION_EXISTS)),
        )
        val target = rule("target").copy(
            effect = RuleEffect(
                steps = listOf(
                    RuleStep(kind = RuleStepKind.IF, condition = notification),
                ),
            ),
        )
        val root = rule(
            "root",
            action(RUN_RULE_TYPE_ID, buildJsonObject { put("ruleId", "target") }),
        ).copy(constraints = notification)

        assertTrue(RuleCapabilityScanner.notificationAccessRequired(root, listOf(root, target), null))
        assertFalse(
            RuleCapabilityScanner.notificationAccessRequired(
                rule("plain", action("accessibility.back")),
                listOf(root, target),
                null,
            ),
        )
    }

    @Test
    fun `disabled notification branch does not require listener access`() {
        val target = rule("target").copy(
            effect = RuleEffect(
                steps = listOf(
                    RuleStep(
                        kind = RuleStepKind.IF,
                        condition = WhenGroup(
                            items = listOf(ConditionItem(type = ConditionType.NOTIFICATION_POSTED)),
                        ),
                        enabled = false,
                    ),
                ),
            ),
        )
        val root = rule(
            "root",
            action(RUN_RULE_TYPE_ID, buildJsonObject { put("ruleId", "target") }),
        )
        assertFalse(RuleCapabilityScanner.notificationAccessRequired(root, listOf(root, target), null))
    }
}
