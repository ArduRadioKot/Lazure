package com.rassvet.essential.ui.graph

import org.junit.Assert.assertEquals
import org.junit.Test

class LouvainTest {
    @Test
    fun louvainCommunities_emptyGraphReturnsSingletons() {
        val communities = louvainCommunities(n = 3, undirectedEdges = emptyList())
        assertEquals(3, communities.size)
        assertEquals(setOf(0, 1, 2), communities.toSet())
    }

    @Test
    fun louvainCommunities_cliqueFormsSingleCommunity() {
        val edges =
            listOf(
                0 to 1,
                1 to 2,
                0 to 2,
            )
        val communities = louvainCommunities(n = 3, undirectedEdges = edges)
        assertEquals(1, communities.toSet().size)
    }

    @Test
    fun louvainCommunities_twoComponentsStaySeparate() {
        val edges = listOf(0 to 1, 2 to 3)
        val communities = louvainCommunities(n = 4, undirectedEdges = edges)
        assertEquals(communities[0], communities[1])
        assertEquals(communities[2], communities[3])
    }
}
