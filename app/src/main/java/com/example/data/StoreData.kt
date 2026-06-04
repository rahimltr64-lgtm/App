package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// 1. Entities
// ==========================================

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val domain: String,
    val category: String,
    val plan: String, // "Basic", "Pro", "Enterprise"
    val status: String, // "Active", "Pending", "Inactive"
    val totalSales: Double = 0.0,
    val currency: String = "ر.س",
    val logoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storeId: Int,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String,
    val category: String,
    val stock: Int,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storeId: Int,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String = "",
    val itemsSummary: String, // "2x حذاء رياضي، 1x تيشرت"
    val totalAmount: Double,
    val status: String, // "Pending", "Processing", "Shipped", "Delivered", "Cancelled"
    val paymentStatus: String, // "Paid", "Pending", "Refunded"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storeId: Int,
    val orderId: Int,
    val amount: Double,
    val gateway: String, // "Stripe", "PayPal", "Bank Transfer", "Apple Pay"
    val status: String, // "Success", "Pending", "Failed"
    val referenceId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ==========================================
// 2. Data Access Objects (DAOs)
// ==========================================

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY createdAt DESC")
    fun getAllStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun getStoreById(id: Int): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity): Long

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Delete
    suspend fun deleteStore(store: StoreEntity)

    // Products
    @Query("SELECT * FROM products WHERE storeId = :storeId ORDER BY createdAt DESC")
    fun getProductsByStore(storeId: Int): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    // Orders
    @Query("SELECT * FROM orders WHERE storeId = :storeId ORDER BY createdAt DESC")
    fun getOrdersByStore(storeId: Int): Flow<List<OrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

    // Payments
    @Query("SELECT * FROM payments WHERE storeId = :storeId ORDER BY createdAt DESC")
    fun getPaymentsByStore(storeId: Int): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long
}

// ==========================================
// 3. Database
// ==========================================

@Database(
    entities = [
        StoreEntity::class,
        ProductEntity::class,
        OrderEntity::class,
        PaymentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StoreDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    companion object {
        @Volatile
        private var INSTANCE: StoreDatabase? = null

        fun getDatabase(context: android.content.Context): StoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StoreDatabase::class.java,
                    "store_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 4. Repository
// ==========================================

class StoreRepository(private val storeDao: StoreDao) {
    val allStores: Flow<List<StoreEntity>> = storeDao.getAllStores()

    fun getProductsForStore(storeId: Int): Flow<List<ProductEntity>> = 
        storeDao.getProductsByStore(storeId)

    fun getOrdersForStore(storeId: Int): Flow<List<OrderEntity>> = 
        storeDao.getOrdersByStore(storeId)

    fun getPaymentsForStore(storeId: Int): Flow<List<PaymentEntity>> = 
        storeDao.getPaymentsByStore(storeId)

    suspend fun getStore(id: Int): StoreEntity? = storeDao.getStoreById(id)

    suspend fun insertStore(store: StoreEntity): Int = storeDao.insertStore(store).toInt()
    suspend fun updateStore(store: StoreEntity) = storeDao.updateStore(store)
    suspend fun deleteStore(store: StoreEntity) = storeDao.deleteStore(store)

    suspend fun insertProduct(product: ProductEntity) = storeDao.insertProduct(product)
    suspend fun updateProduct(product: ProductEntity) = storeDao.updateProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = storeDao.deleteProduct(product)

    suspend fun insertOrder(order: OrderEntity) = storeDao.insertOrder(order)
    suspend fun updateOrder(order: OrderEntity) = storeDao.updateOrder(order)
    suspend fun deleteOrder(order: OrderEntity) = storeDao.deleteOrder(order)

    suspend fun insertPayment(payment: PaymentEntity) = storeDao.insertPayment(payment)
}
