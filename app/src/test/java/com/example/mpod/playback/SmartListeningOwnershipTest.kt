package com.example.mpod.playback

import com.example.mpod.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

class SmartListeningOwnershipTest {

    @Test
    fun `MainActivity must not declare SmartListeningManager dependency`() {
        // Проверяем, что MainActivity не инжектит и не хранит SmartListeningManager,
        // чтобы единственным владельцем оставался MpodApplication.
        val fields: Array<Field> = MainActivity::class.java.declaredFields
        val hasSmartListeningManager = fields.any { it.type == SmartListeningManager::class.java }

        assertTrue(
            "MainActivity не должен содержать зависимость SmartListeningManager для избежания двойного запуска.",
            !hasSmartListeningManager
        )
    }
}
