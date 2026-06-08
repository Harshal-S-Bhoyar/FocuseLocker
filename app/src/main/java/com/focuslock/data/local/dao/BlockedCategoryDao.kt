package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.data.local.entity.BlockedCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCategoryDao {
    
    @Query("SELECT * FROM blocked_categories ORDER BY category_key ASC")
    fun getAllCategories(): Flow<List<BlockedCategoryEntity>>
    
    @Query("SELECT * FROM blocked_categories WHERE is_active = 1")
    fun getActiveCategories(): Flow<List<BlockedCategoryEntity>>
    
    @Query("SELECT category_key FROM blocked_categories WHERE is_active = 1")
    suspend fun getActiveCategoryKeys(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: BlockedCategoryEntity)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<BlockedCategoryEntity>)
    
    @Update
    suspend fun updateCategory(category: BlockedCategoryEntity)
    
    @Query("UPDATE blocked_categories SET is_active = :isActive WHERE category_key = :categoryKey")
    suspend fun updateCategoryStatus(categoryKey: String, isActive: Boolean)

    @Query("UPDATE blocked_categories SET is_active = 1 WHERE category_key = 'AUTO_BLOCKED'")
    suspend fun ensureAutoBlockedActive()
}
