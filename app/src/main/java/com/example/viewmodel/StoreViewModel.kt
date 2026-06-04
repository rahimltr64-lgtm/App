package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val database = StoreDatabase.getDatabase(application)
    private val repository = StoreRepository(database.storeDao())

    // All stores list
    val allStores: StateFlow<List<StoreEntity>> = repository.allStores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Store
    private val _selectedStore = MutableStateFlow<StoreEntity?>(null)
    val selectedStore: StateFlow<StoreEntity?> = _selectedStore.asStateFlow()

    // Products of selected store
    val selectedStoreProducts: StateFlow<List<ProductEntity>> = _selectedStore
        .flatMapLatest { store ->
            if (store != null) repository.getProductsForStore(store.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Orders of selected store
    val selectedStoreOrders: StateFlow<List<OrderEntity>> = _selectedStore
        .flatMapLatest { store ->
            if (store != null) repository.getOrdersForStore(store.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Payments of selected store
    val selectedStorePayments: StateFlow<List<PaymentEntity>> = _selectedStore
        .flatMapLatest { store ->
            if (store != null) repository.getPaymentsForStore(store.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically check and seed database with beautiful realistic sample data if empty
        viewModelScope.launch {
            allStores.collectLatest { stores ->
                if (stores.isEmpty()) {
                    seedDatabase()
                } else {
                    // Pre-select the first store if none is selected
                    if (_selectedStore.value == null && stores.isNotEmpty()) {
                        _selectedStore.value = stores.first()
                    }
                }
            }
        }
    }

    fun selectStore(store: StoreEntity) {
        _selectedStore.value = store
    }

    fun deselectStore() {
        _selectedStore.value = null
    }

    // ==========================================
    // Database Operations
    // ==========================================

    fun createStore(name: String, category: String, plan: String, domainPrefix: String) {
        viewModelScope.launch {
            val domainSuffix = when (plan) {
                "مبتدئ (Basic)" -> ".salla.sa"
                "محترف (Pro)" -> ".zid.sa"
                else -> ".store.sa"
            }
            val sanitizedPrefix = domainPrefix.lowercase().replace(" ", "-")
            val newStore = StoreEntity(
                name = name,
                domain = "$sanitizedPrefix$domainSuffix",
                category = category,
                plan = plan,
                status = "نشط (Active)",
                currency = "ر.س",
                totalSales = 0.0
            )
            val newId = repository.insertStore(newStore)
            
            // Seed a few default products for this brand new store to make it instantly functional
            repository.insertProduct(
                ProductEntity(
                    storeId = newId,
                    name = "منتج تجريبي أول",
                    description = "هذا منتج افتراضي تم إنشاؤه تلقائياً لمتجرك الجديد. يمكنك تعديله أو حذفه في أي وقت.",
                    price = 99.0,
                    imageUrl = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=500&q=80",
                    category = category,
                    stock = 50,
                    isActive = true
                )
            )
            repository.insertProduct(
                ProductEntity(
                    storeId = newId,
                    name = "منتج تجريبي ثانٍ",
                    description = "هذا منتج افتراضي تم إنشاؤه تلقائياً لمتجرك الجديد.",
                    price = 149.0,
                    imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=500&q=80",
                    category = category,
                    stock = 20,
                    isActive = true
                )
            )

            // Seed a sample order as well
            val orderId = repository.insertOrder(
                OrderEntity(
                    storeId = newId,
                    customerName = "عبد الله العتيبي",
                    customerEmail = "abdullah@example.com",
                    customerPhone = "+966500000000",
                    itemsSummary = "1x منتج تجريبي أول",
                    totalAmount = 99.0,
                    status = "قيد الانتظار",
                    paymentStatus = "غير مدفوع"
                )
            ).toInt()

            // Update stats
            val createdStore = repository.getStore(newId)
            if (createdStore != null) {
                _selectedStore.value = createdStore
            }
        }
    }

    // Product CRUD
    fun addProduct(name: String, description: String, price: Double, stock: Int, category: String, imageUrl: String) {
        val store = _selectedStore.value ?: return
        viewModelScope.launch {
            val product = ProductEntity(
                storeId = store.id,
                name = name,
                description = description,
                price = price,
                stock = stock,
                category = category,
                imageUrl = if (imageUrl.isBlank()) "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=500&q=80" else imageUrl,
                isActive = true
            )
            repository.insertProduct(product)
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    // Order operations
    fun updateOrderStatus(order: OrderEntity, newStatus: String, newPaymentStatus: String) {
        val store = _selectedStore.value ?: return
        viewModelScope.launch {
            val updatedOrder = order.copy(status = newStatus, paymentStatus = newPaymentStatus)
            repository.updateOrder(updatedOrder)

            // If the order status transitioned to active/completed and it was paid, record payment
            if (newPaymentStatus == "مدفوع" && order.paymentStatus != "مدفوع") {
                val newPayment = PaymentEntity(
                    storeId = store.id,
                    orderId = order.id,
                    amount = order.totalAmount,
                    gateway = "مدفوعات المتجر API",
                    status = "ناجحة"
                )
                repository.insertPayment(newPayment)

                // Update total sales of store
                val currentStore = _selectedStore.value
                if (currentStore != null) {
                    val updatedStore = currentStore.copy(totalSales = currentStore.totalSales + order.totalAmount)
                    repository.updateStore(updatedStore)
                    _selectedStore.value = updatedStore
                }
            }
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.deleteOrder(order)
        }
    }

    // ==========================================
    // Seeding Logic
    // ==========================================

    private suspend fun seedDatabase() {
        // Seed Store 1: Fashion
        val store1Id = repository.insertStore(
            StoreEntity(
                name = "متجر رداء الأناقة",
                domain = "elegance-wear.salla.sa",
                category = "ملابس وأزياء",
                plan = "محترف (Pro)",
                status = "نشط (Active)",
                totalSales = 3450.0,
                currency = "ر.س",
                logoUrl = "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=100&q=80"
            )
        ).toInt()

        // Seed Store 2: Electronics
        val store2Id = repository.insertStore(
            StoreEntity(
                name = "واحة التكنولوجيا",
                domain = "tech-oasis.zid.sa",
                category = "إلكترونيات وهواتف",
                plan = "متكامل (Enterprise)",
                status = "نشط (Active)",
                totalSales = 9800.0,
                currency = "ر.س",
                logoUrl = "https://images.unsplash.com/photo-1531297484001-80022131f5a1?w=100&q=80"
            )
        ).toInt()

        // Seed Store 1 Products
        repository.insertProduct(
            ProductEntity(
                storeId = store1Id,
                name = "حقيبة يد جلدية كلاسيكية",
                description = "حقيبة مصنوعة من الجلد الطبيعي الفاخر بتصميم كلاسيكي يناسب جميع المناسبات الرسمية واليومية.",
                price = 350.0,
                imageUrl = "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=500&q=80",
                category = "حقائب",
                stock = 15,
                isActive = true
            )
        )
        repository.insertProduct(
            ProductEntity(
                storeId = store1Id,
                name = "حذاء جري رياضي الترا",
                description = "حذاء رياضي مريح جداً ومقاوم للانزلاق مع بطانة داخلية مزدوجة لامتصاص الهزات.",
                price = 280.0,
                imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=500&q=80",
                category = "أحذية",
                stock = 45,
                isActive = true
            )
        )
        repository.insertProduct(
            ProductEntity(
                storeId = store1Id,
                name = "ساعة يد فاخرة كوارتز",
                description = "ساعة يد رجالية فاخرة مقاومة للماء مع حزام جلدي باللون البني الداكن.",
                price = 850.0,
                imageUrl = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=500&q=80",
                category = "إكسسوارات",
                stock = 8,
                isActive = true
            )
        )

        // Seed Store 2 Products
        repository.insertProduct(
            ProductEntity(
                storeId = store2Id,
                name = "سماعات رأس لاسلكية برو",
                description = "سماعات رأس بخاصية عزل الصوت الخارجي التام، تعمل حتى 40 ساعة متواصلة مع شحن سريع.",
                price = 1200.0,
                imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500&q=80",
                category = "سماعات",
                stock = 12,
                isActive = true
            )
        )
        repository.insertProduct(
            ProductEntity(
                storeId = store2Id,
                name = "هاتف محمول ذكي دبل إكس",
                description = "شاشة غامرة بمعدل تحديث 120 هرتز، كاميرا ثلاثية فائقة الدقة 108 ميجابكسل، سعة تخزين 256 جيجابايت.",
                price = 3800.0,
                imageUrl = "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=500&q=80",
                category = "هواتف",
                stock = 5,
                isActive = true
            )
        )

        // Seed Store 1 Orders
        val order1Id = repository.insertOrder(
            OrderEntity(
                storeId = store1Id,
                customerName = "محمد بن مساعد",
                customerEmail = "musaad@gmail.com",
                customerPhone = "+966512345678",
                itemsSummary = "1x حقيبة يد جلدية، 1x حذاء جري",
                totalAmount = 630.0,
                status = "مكتمل",
                paymentStatus = "مدفوع"
            )
        ).toInt()

        val order2Id = repository.insertOrder(
            OrderEntity(
                storeId = store1Id,
                customerName = "سارة أحمد",
                customerEmail = "sara.ah@example.com",
                customerPhone = "+966555544332",
                itemsSummary = "1x ساعة يد فاخرة كوارتز",
                totalAmount = 850.0,
                status = "تم الشحن",
                paymentStatus = "مدفوع"
            )
        ).toInt()

        val order3Id = repository.insertOrder(
            OrderEntity(
                storeId = store1Id,
                customerName = "خالد الحربي",
                customerEmail = "khaled.h@example.com",
                customerPhone = "+966567890123",
                itemsSummary = "2x حذاء جري رياضي الترا",
                totalAmount = 560.0,
                status = "قيد المعالجة",
                paymentStatus = "غير مدفوع"
            )
        ).toInt()

        // Seed Store 2 Orders
        val order4Id = repository.insertOrder(
            OrderEntity(
                storeId = store2Id,
                customerName = "أروى القحطاني",
                customerEmail = "arwa_q@outlook.com",
                customerPhone = "+966599112233",
                itemsSummary = "1x سماعات رأس لاسلكية برو",
                totalAmount = 1200.0,
                status = "مكتمل",
                paymentStatus = "مدفوع"
            )
        ).toInt()

        val order5Id = repository.insertOrder(
            OrderEntity(
                storeId = store2Id,
                customerName = "فيصل السهلي",
                customerEmail = "faisal.s@live.co.uk",
                customerPhone = "+966544668899",
                itemsSummary = "1x هاتف محمول، 1x سماعات رأس",
                totalAmount = 5000.0,
                status = "قيد الانتظار",
                paymentStatus = "مدفوع"
            )
        ).toInt()

        // Seed Store 1 Payments
        repository.insertPayment(
            PaymentEntity(
                storeId = store1Id,
                orderId = order1Id,
                amount = 630.0,
                gateway = "Apple Pay",
                status = "Success",
                referenceId = "PAY-FAS-9981"
            )
        )
        repository.insertPayment(
            PaymentEntity(
                storeId = store1Id,
                orderId = order2Id,
                amount = 850.0,
                gateway = "مدى (Mada)",
                status = "Success",
                referenceId = "PAY-FAS-1204"
            )
        )

        // Seed Store 2 Payments
        repository.insertPayment(
            PaymentEntity(
                storeId = store2Id,
                orderId = order4Id,
                amount = 1200.0,
                gateway = "Stripe (فيزا)",
                status = "Success",
                referenceId = "PAY-TEC-5501"
            )
        )
        repository.insertPayment(
            PaymentEntity(
                storeId = store2Id,
                orderId = order5Id,
                amount = 5000.0,
                gateway = "Apple Pay",
                status = "Success",
                referenceId = "PAY-TEC-7685"
            )
        )
    }
}
