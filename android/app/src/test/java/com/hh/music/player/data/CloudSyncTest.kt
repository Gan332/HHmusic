package com.hh.music.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure reconciliation planning for the v1.8 favorites alignment. */
class CloudSyncTest {

    private fun song(id: Long) = Song(id = id, name = "s$id")

    @Test
    fun `union plan merges cloud-only into local and pushes local-only to cloud`() {
        val cloud = listOf(song(1), song(2), song(3))
        val local = listOf(song(2), song(4))

        val plan = CloudSync.planReconciliation(cloud, local)

        assertEquals(listOf(1L, 3L), plan.missingLocally.map { it.id })
        assertEquals(listOf(4L), plan.toPushToCloud.map { it.id })
    }

    @Test
    fun `identical sides produce an empty plan`() {
        val both = listOf(song(1), song(2))

        val plan = CloudSync.planReconciliation(both, both)

        assertTrue(plan.missingLocally.isEmpty())
        assertTrue(plan.toPushToCloud.isEmpty())
    }

    @Test
    fun `synthetic negative import ids are never pushed to the cloud`() {
        val cloud = emptyList()
        val local = listOf(song(-5), song(-6), song(7))

        val plan = CloudSync.planReconciliation(cloud, local)

        assertEquals(listOf(7L), plan.toPushToCloud.map { it.id })
    }

    @Test
    fun `push budget caps the cloud traffic of one login`() {
        val cloud = emptyList()
        val local = (1L..250L).map { song(it) }

        val plan = CloudSync.planReconciliation(cloud, local)

        assertEquals(CloudSync.MAX_CLOUD_PUSHES_PER_LOGIN, plan.toPushToCloud.size)
        // Custom (smaller) cap is honored too.
        val tiny = CloudSync.planReconciliation(cloud, local, maxPushes = 3)
        assertEquals(3, tiny.toPushToCloud.size)
    }

    @Test
    fun `zero cap yields nothing to push while still merging locally`() {
        val cloud = listOf(song(1))
        val local = listOf(song(2))

        val plan = CloudSync.planReconciliation(cloud, local, maxPushes = 0)

        assertEquals(listOf(1L), plan.missingLocally.map { it.id })
        assertTrue(plan.toPushToCloud.isEmpty())
    }
}
