package com.tombstone.scansn.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class ProductCategory(val title: String, val requiresImei: Boolean) {
    PHONE("Телефон", true), TABLET("Планшет", false), PC_COMPONENT("Комплектующее ПК", false)
}
@Entity(tableName = "products", indices = [Index(value = ["sn"]), Index(value = ["imei"]), Index(value = ["orderNumber"])])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: ProductCategory = ProductCategory.PHONE,
    val componentType: String = "", val marketplace: String = "WB",
    val brand: String = "", val model: String = "", val color: String = "",
    val account: String = "", val price: String = "", val customsDuty: String = "",
    val sn: String = "", val imei: String = "", val orderNumber: String = "",
    val notes: String = "", val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
@Entity(tableName = "device_info_cache")
data class DeviceInfoCacheEntity(
    @PrimaryKey val deviceKey: String, val brand: String, val model: String,
    val fieldsJson: String, val sourceUrl: String, val fetchedAt: Long
)
@Dao interface DeviceInfoDao {
    @Query("SELECT * FROM device_info_cache WHERE deviceKey = :deviceKey LIMIT 1")
    suspend fun get(deviceKey: String): DeviceInfoCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(info: DeviceInfoCacheEntity)
}
@Dao interface ProductDao {
    @Query("SELECT * FROM products ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ProductEntity>>
    @Insert suspend fun insert(product: ProductEntity): Long
    @Update suspend fun update(product: ProductEntity)
    @Query("DELETE FROM products WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM products WHERE sn = :value AND id != :excludeId AND sn != '' LIMIT 1")
    suspend fun findSnDuplicate(value: String, excludeId: Long): ProductEntity?
    @Query("SELECT * FROM products WHERE imei = :value AND id != :excludeId AND imei != '' LIMIT 1")
    suspend fun findImeiDuplicate(value: String, excludeId: Long): ProductEntity?
}
@Database(entities = [ProductEntity::class, DeviceInfoCacheEntity::class], version = 3, exportSchema = true)
abstract class ScanSNDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun deviceInfoDao(): DeviceInfoDao
    companion object {
        @Volatile private var instance: ScanSNDatabase? = null
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN componentType TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS device_info_cache (
                    deviceKey TEXT NOT NULL PRIMARY KEY, brand TEXT NOT NULL, model TEXT NOT NULL,
                    fieldsJson TEXT NOT NULL, sourceUrl TEXT NOT NULL, fetchedAt INTEGER NOT NULL
                )""".trimIndent())
            }
        }
        fun get(context: Context): ScanSNDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ScanSNDatabase::class.java, "scansn.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
class ProductRepository(private val dao: ProductDao) {
    val products = dao.observeAll()
    suspend fun save(product: ProductEntity): Long = if (product.id == 0L) dao.insert(product) else {
        dao.update(product); product.id
    }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun findSnDuplicate(value: String, excludeId: Long) = dao.findSnDuplicate(value, excludeId)
    suspend fun findImeiDuplicate(value: String, excludeId: Long) = dao.findImeiDuplicate(value, excludeId)
}