package hunoia.luno.action.handlers

import hunoia.luno.action.api.ActionHandler

internal val allHandlers: List<ActionHandler> = listOf(
    NavigationActionHandler,
    MediaActionHandler,
    SystemActionHandler,
    RandomNameActionHandler,
    PasswordGeneratorActionHandler,
    AppLaunchActionHandler,
    ShortcutActionHandler,
    VolumeScrubActionHandler,
    ShellCommandActionHandler,
    ClickCurrentPositionActionHandler,
    ScrollActionHandler,
)
