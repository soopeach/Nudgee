package com.soopeach.nudgee.client.data.notifications

import com.soopeach.nudgee.client.notifications.AndroidFcmDeviceRegistrar

actual suspend fun registerPlatformPushToken() {
    AndroidFcmDeviceRegistrar.registerCurrentToken()
}
