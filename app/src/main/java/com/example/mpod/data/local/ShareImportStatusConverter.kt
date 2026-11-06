package com.example.mpod.data.local

import androidx.room.TypeConverter
import com.example.mpod.data.local.entity.ShareImportStatus

class ShareImportStatusConverter {
    @TypeConverter
    fun fromStatus(status: ShareImportStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ShareImportStatus = ShareImportStatus.valueOf(value)
}
