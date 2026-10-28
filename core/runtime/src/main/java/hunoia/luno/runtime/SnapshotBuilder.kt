package hunoia.luno.runtime

import hunoia.luno.core.domain.RuntimeSnapshot

/**
 * 活运行态 → 不可变快照（`:core:domain` 的 [RuntimeSnapshot]）。
 *
 * 生产者固定在这里：条件求值、自动化仲裁、模板变量都只读快照，谁都不许自己拼一份。
 * 放在 `:core:runtime` 而不是 `:feature:conditions`：消费方不止条件求值。
 */
fun GestureRuntimeState.toConditionContext(
    variables: Map<String, String> = emptyMap(),
): RuntimeSnapshot = RuntimeSnapshot(
    nowMs = nowMs,
    packageName = currentPackageName,
    isLockScreen = isNowInLockScreenPage,
    isLauncher = isInLauncher,
    isLandscape = isLandscape,
    isKeyboardInput = isKeyboardInputActive,
    isCharging = isCharging,
    batteryLevel = batteryLevel,
    minuteOfDay = minuteOfDay,
    dayOfWeek = dayOfWeek,
    networkType = networkType,
    wifiSsid = wifiSsid,
    headphonesConnected = headphonesConnected,
    bluetoothAdapterOn = bluetoothAdapterOn,
    bluetoothAudioConnected = bluetoothAudioConnected,
    bluetoothDeviceName = bluetoothDeviceName,
    bluetoothDeviceAddress = bluetoothDeviceAddress,
    airplaneMode = airplaneMode,
    ringerMode = ringerMode,
    volumePercent = volumePercent,
    isMusicPlaying = isMusicPlaying,
    isTorchOn = isTorchOn,
    isDndOn = isDndOn,
    appChangedTo = appChangedTo,
    screenEvent = screenEvent,
    chargingChangedTo = chargingChangedTo,
    volumeChanged = volumeChanged,
    notificationPosted = notificationPosted,
    notificationRemoved = notificationRemoved,
    activeNotifications = activeNotifications,
    packageChanged = packageChanged,
    shareReceived = shareReceived,
    clipboardChanged = clipboardChanged,
    intervalDueKeys = intervalDueKeys,
    bluetoothChanged = bluetoothChanged,
    headphonesChanged = headphonesChanged,
    scheduledTimeHit = scheduledTimeHit,
    gesturePerformedId = gesturePerformedId,
    gesturePerformedName = gesturePerformedName,
    keyboardEvent = keyboardEvent,
    musicChanged = musicChanged,
    torchChangedTo = torchChangedTo,
    dndChangedTo = dndChangedTo,
    variables = variables,
)

