package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for core User CRUD operations on the `staff_users` table.
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM staff_users ORDER BY id ASC")
    fun observeAll(): Flow<List<StaffUserEntity>>

    @Query("SELECT * FROM staff_users WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<StaffUserEntity?>

    @Query("SELECT * FROM staff_users ORDER BY id ASC")
    suspend fun getAll(): List<StaffUserEntity>

    @Query("SELECT * FROM staff_users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): StaffUserEntity?

    @Query("SELECT * FROM staff_users WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): StaffUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: StaffUserEntity): Long

    @Update
    suspend fun update(user: StaffUserEntity): Int

    @Delete
    suspend fun delete(user: StaffUserEntity): Int

    @Query("DELETE FROM staff_users WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
