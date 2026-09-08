package hunoia.luno.config.repository

import androidx.datastore.core.DataStore
import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibrarySettings
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.FrozenAppSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.store.SettingsStores
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConfigProviderFacadeTest {

    private val testInitial = InitialSettings(gestureEnabled = true, unlocked = true)
    private val testAdvanced = AdvancedSettings(keepAliveEnabled = true)
    private val testGesture = GestureSettings(actionPanelVibrate = true)
    private val testAction = ActionSettings(hideGestureButton = ActionSettings.HideGestureButton(delayMs = 500))
    private val testButtons = listOf(GestureButton(id = "btn1"))
    private val testQla = QuickAppLauncherSettings(gridColumns = 3)
    private val testFrozen = FrozenAppSettings(oneKeyPackageNames = setOf("com.example"))
    private val testSubGesture = SubGestureSettings(subGestures = listOf(SubGesture(id = "sg1", name = "Test")))
    private val testActionLib = ActionLibrarySettings(entries = listOf(ActionLibraryEntry(id = "e1", type = ActionLibraryType.Shell)))

    private val initialStore: DataStore<InitialSettings> = mockk {
        every { data } returns flowOf(testInitial)
        coEvery { updateData(any()) } returns testInitial
    }
    private val advancedStore: DataStore<AdvancedSettings> = mockk {
        every { data } returns flowOf(testAdvanced)
        coEvery { updateData(any()) } returns testAdvanced
    }
    private val gestureStore: DataStore<GestureSettings> = mockk {
        every { data } returns flowOf(testGesture)
        coEvery { updateData(any()) } returns testGesture
    }
    private val actionStore: DataStore<ActionSettings> = mockk {
        every { data } returns flowOf(testAction)
        coEvery { updateData(any()) } returns testAction
    }
    private val buttonsStore: DataStore<List<GestureButton>> = mockk {
        every { data } returns flowOf(testButtons)
        coEvery { updateData(any()) } returns testButtons
    }
    private val qlaStore: DataStore<QuickAppLauncherSettings> = mockk {
        every { data } returns flowOf(testQla)
        coEvery { updateData(any()) } returns testQla
    }
    private val frozenStore: DataStore<FrozenAppSettings> = mockk {
        every { data } returns flowOf(testFrozen)
        coEvery { updateData(any()) } returns testFrozen
    }
    private val subGestureStore: DataStore<SubGestureSettings> = mockk {
        every { data } returns flowOf(testSubGesture)
        coEvery { updateData(any()) } returns testSubGesture
    }
    private val actionLibStore: DataStore<ActionLibrarySettings> = mockk {
        every { data } returns flowOf(testActionLib)
        coEvery { updateData(any()) } returns testActionLib
    }

    private val stores = SettingsStores(
        _initialSettings = initialStore,
        _advancedSettings = advancedStore,
        _gestureSettings = gestureStore,
        _actionSettings = actionStore,
        _gestureButtons = buttonsStore,
        _quickAppLauncherSettings = qlaStore,
        _frozenAppSettings = frozenStore,
        _subGestureSettings = subGestureStore,
        _actionLibrarySettings = actionLibStore,
    )

    @Test
    fun `snapshotAll collects all 9 fields`() = runBlocking {
        val repo = ConfigBackupRepository(stores)

        val backup = repo.snapshotAll()

        assertEquals(testInitial, backup.initialSettings)
        assertEquals(testAdvanced, backup.advancedSettings)
        assertEquals(testGesture, backup.gestureSettings)
        assertEquals(testAction, backup.actionSettings)
        assertEquals(testButtons, backup.gestureButtons)
        assertEquals(testQla, backup.quickAppLauncherSettings)
        assertEquals(testFrozen, backup.frozenAppSettings)
        assertEquals(testSubGesture, backup.subGestureSettings)
        assertEquals(testActionLib, backup.actionLibrarySettings)
        assertNotNull(backup.timestamp)
        assertNotNull(backup.version)
    }

    @Test
    fun `restoreAll writes all 9 fields`() = runBlocking {
        val backup = Backup(
            initialSettings = testInitial,
            advancedSettings = testAdvanced,
            gestureSettings = testGesture,
            actionSettings = testAction,
            gestureButtons = testButtons,
            quickAppLauncherSettings = testQla,
            frozenAppSettings = testFrozen,
            subGestureSettings = testSubGesture,
            actionLibrarySettings = testActionLib,
        )
        val repo = ConfigBackupRepository(stores)

        repo.restoreAll(backup)

        coVerify(exactly = 1) { initialStore.updateData(any()) }
        coVerify(exactly = 1) { advancedStore.updateData(any()) }
        coVerify(exactly = 1) { gestureStore.updateData(any()) }
        coVerify(exactly = 1) { actionStore.updateData(any()) }
        coVerify(exactly = 1) { buttonsStore.updateData(any()) }
        coVerify(exactly = 1) { qlaStore.updateData(any()) }
        coVerify(exactly = 1) { frozenStore.updateData(any()) }
        coVerify(exactly = 1) { subGestureStore.updateData(any()) }
        coVerify(exactly = 1) { actionLibStore.updateData(any()) }
    }

    @Test
    fun `restoreAll skips null fields`() = runBlocking {
        val backup = Backup(initialSettings = testInitial)
        val repo = ConfigBackupRepository(stores)

        repo.restoreAll(backup)

        coVerify(exactly = 1) { initialStore.updateData(any()) }
        coVerify(exactly = 0) { advancedStore.updateData(any()) }
        coVerify(exactly = 0) { gestureStore.updateData(any()) }
        coVerify(exactly = 0) { actionStore.updateData(any()) }
        coVerify(exactly = 0) { buttonsStore.updateData(any()) }
        coVerify(exactly = 0) { qlaStore.updateData(any()) }
        coVerify(exactly = 0) { frozenStore.updateData(any()) }
        coVerify(exactly = 0) { subGestureStore.updateData(any()) }
        coVerify(exactly = 0) { actionLibStore.updateData(any()) }
    }

    @Test
    fun `resetAll resets all 9 stores to defaults`() = runBlocking {
        val repo = ConfigBackupRepository(stores)

        repo.resetAll()

        coVerify(exactly = 1) { initialStore.updateData(any()) }
        coVerify(exactly = 1) { advancedStore.updateData(any()) }
        coVerify(exactly = 1) { gestureStore.updateData(any()) }
        coVerify(exactly = 1) { actionStore.updateData(any()) }
        coVerify(exactly = 1) { buttonsStore.updateData(any()) }
        coVerify(exactly = 1) { qlaStore.updateData(any()) }
        coVerify(exactly = 1) { frozenStore.updateData(any()) }
        coVerify(exactly = 1) { subGestureStore.updateData(any()) }
        coVerify(exactly = 1) { actionLibStore.updateData(any()) }
    }

    @Test
    fun `settingsRepository get returns correct values`() = runBlocking {
        val repo = SettingsRepository(stores)

        assertEquals(testInitial, repo.getInitialSettings())
        assertEquals(testAdvanced, repo.getAdvancedSettings())
        assertEquals(testGesture, repo.getGestureSettings())
        assertEquals(testAction, repo.getActionSettings())
        assertEquals(testButtons, repo.getGestureButtons())
        assertEquals(testQla, repo.getQuickAppLauncherSettings())
        assertEquals(testFrozen, repo.getFrozenAppSettings())
        assertEquals(testSubGesture, repo.getSubGestureSettings())
        assertEquals(testActionLib, repo.getActionLibrarySettings())
    }

    @Test
    fun `settingsRepository update delegates to store`() = runBlocking {
        val repo = SettingsRepository(stores)

        repo.updateInitialSettings { it.copy(gestureEnabled = false) }
        repo.updateAdvancedSettings { it.copy(keepAliveEnabled = false) }
        repo.updateGestureSettings { it.copy(actionPanelVibrate = false) }
        repo.updateActionSettings { it.copy(hideGestureButton = ActionSettings.HideGestureButton(delayMs = 200)) }
        repo.updateGestureButtons { it.map { b -> b.copy(enabled = false) } }
        repo.updateQuickAppLauncherSettings { it.copy(gridColumns = 5) }
        repo.updateFrozenAppSettings { it.copy(oneKeyPackageNames = emptySet()) }
        repo.updateSubGestureSettings { it.copy(subGestures = emptyList()) }
        repo.updateActionLibrarySettings { it.copy(entries = emptyList()) }

        coVerify(exactly = 1) { initialStore.updateData(any()) }
        coVerify(exactly = 1) { advancedStore.updateData(any()) }
        coVerify(exactly = 1) { gestureStore.updateData(any()) }
        coVerify(exactly = 1) { actionStore.updateData(any()) }
        coVerify(exactly = 1) { buttonsStore.updateData(any()) }
        coVerify(exactly = 1) { qlaStore.updateData(any()) }
        coVerify(exactly = 1) { frozenStore.updateData(any()) }
        coVerify(exactly = 1) { subGestureStore.updateData(any()) }
        coVerify(exactly = 1) { actionLibStore.updateData(any()) }
    }
}
