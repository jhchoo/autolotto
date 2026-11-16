package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class PurchaseStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun samplePurchase(
        round: Int,
        numbers: List<List<Int>> = listOf(listOf(1, 2, 3, 4, 5, 6)),
        checked: Boolean = false,
        rank: String? = null,
        prize: Long = 0,
    ) = Purchase(
        round = round,
        date = LocalDateTime.of(2026, 7, 18, 20, 45, 0),
        numbers = numbers,
        autoCount = numbers.size,
        manualCount = 0,
        amount = numbers.size * 1000,
        checked = checked,
        rank = rank,
        prize = prize,
    )

    @Test
    fun `캐시 파일이 없으면 빈 목록 반환`() {
        val list = PurchaseStore.loadPurchases(tempFolder.root, "testUser")
        assertTrue(list.isEmpty())
    }

    @Test
    fun `구매 내역을 저장하고 다시 불러올 수 있다`() {
        val p1 = samplePurchase(1234)
        val p2 = samplePurchase(1233)
        PurchaseStore.savePurchases(tempFolder.root, "testUser", listOf(p1, p2))

        val loaded = PurchaseStore.loadPurchases(tempFolder.root, "testUser")
        assertEquals(2, loaded.size)
        assertEquals(1234, loaded[0].round)
        assertEquals(1233, loaded[1].round)
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6)), loaded[0].numbers)
    }

    @Test
    fun `saveOrMergePurchase - 신규 구매가 기존 내역과 병합된다`() {
        val p1 = samplePurchase(1233)
        PurchaseStore.savePurchases(tempFolder.root, "testUser", listOf(p1))

        val newBuy = samplePurchase(1234, listOf(listOf(7, 8, 9, 10, 11, 12)))
        PurchaseStore.saveOrMergePurchase(tempFolder.root, "testUser", newBuy)

        val loaded = PurchaseStore.loadPurchases(tempFolder.root, "testUser")
        assertEquals(2, loaded.size)
        assertEquals(1234, loaded[0].round)
        assertEquals(1233, loaded[1].round)
    }

    @Test
    fun `saveOrMergePurchase - 같은 회차의 추가 구매는 한 카드로 병합된다`() {
        val first = samplePurchase(1234, listOf(listOf(1, 2, 3, 4, 5, 6)))
        PurchaseStore.savePurchases(tempFolder.root, "testUser", listOf(first))

        val second = samplePurchase(1234, listOf(listOf(10, 11, 12, 13, 14, 15)))
        PurchaseStore.saveOrMergePurchase(tempFolder.root, "testUser", second)

        val loaded = PurchaseStore.loadPurchases(tempFolder.root, "testUser")
        assertEquals(1, loaded.size)
        assertEquals(1234, loaded[0].round)
        assertEquals(2, loaded[0].numbers.size)
        assertEquals(2000, loaded[0].amount)
    }

    @Test
    fun `loadPurchases - 특정 계정 캐시 파일이 없으면 기존 캐시 파일로 폴백한다`() {
        val p = samplePurchase(1234)
        PurchaseStore.savePurchases(tempFolder.root, "default", listOf(p))

        val loaded = PurchaseStore.loadPurchases(tempFolder.root, "unknownUser")
        assertEquals(1, loaded.size)
        assertEquals(1234, loaded[0].round)
    }
}
