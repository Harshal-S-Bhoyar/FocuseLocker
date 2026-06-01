package com.focuslock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unlock_requests",
    indices = [Index(value = ["session_id"])],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UnlockRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "requested_at")
    val requestedAt: Long,
    
    @ColumnInfo(name = "status")
    val status: String, // PENDING | OTP_SENT | OTP_VERIFIED | COOLDOWN | COMPLETED | FAILED | EMERGENCY
    
    @ColumnInfo(name = "otp_attempts")
    val otpAttempts: Int = 0,
    
    @ColumnInfo(name = "cooldown_ends_at")
    val cooldownEndsAt: Long? = null
)

object UnlockRequestStatus {
    const val PENDING = "PENDING"
    const val OTP_SENT = "OTP_SENT"
    const val OTP_VERIFIED = "OTP_VERIFIED"
    const val COOLDOWN = "COOLDOWN"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val EMERGENCY = "EMERGENCY"
}
