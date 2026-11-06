package com.rassvet.essential.ui.graph


internal fun louvainCommunities(
    n: Int,
    undirectedEdges: List<Pair<Int, Int>>,
): IntArray {
    if (n == 0) return IntArray(0)
    if (undirectedEdges.isEmpty()) return IntArray(n) { it }


    val adjacency = Array(n) { HashSet<Int>() }
    var m = 0
    for ((a, b) in undirectedEdges) {
        if (a == b) continue
        if (adjacency[a].add(b)) m++
        adjacency[b].add(a)
    }
    if (m == 0) return IntArray(n) { it }

    val deg = IntArray(n) { adjacency[it].size }
    val community = IntArray(n) { it }

    val totalDeg = IntArray(n) { deg[it] }
    val twoM = 2.0 * m

    fun moveNode(node: Int, from: Int, to: Int) {
        community[node] = to
        totalDeg[from] -= deg[node]
        totalDeg[to] += deg[node]
    }

    var changed = true
    var iter = 0
    val maxIter = 8
    while (changed && iter < maxIter) {
        changed = false
        for (i in 0 until n) {
            val curr = community[i]

            val edgesPerCommunity = HashMap<Int, Int>()
            for (j in adjacency[i]) {
                edgesPerCommunity.merge(community[j], 1, Int::plus)
            }
            val kI = deg[i].toDouble()
            val kiInCurr = (edgesPerCommunity[curr] ?: 0).toDouble()


            var bestGain = 0.0
            var bestCommunity = curr
            for ((cand, kiIn) in edgesPerCommunity) {
                if (cand == curr) continue
                val sigmaTot = totalDeg[cand].toDouble()

                val gain = (kiIn - kiInCurr) / m - kI * (sigmaTot - totalDeg[curr] + kI) / (2.0 * m * m)
                if (gain > bestGain) {
                    bestGain = gain
                    bestCommunity = cand
                }
            }
            if (bestCommunity != curr && bestGain > 1e-7) {
                moveNode(i, curr, bestCommunity)
                changed = true
            }
        }
        iter++
    }


    val remap = HashMap<Int, Int>()
    val out = IntArray(n)
    for (i in 0 until n) {
        val c = community[i]
        out[i] = remap.getOrPut(c) { remap.size }
    }
    return out
}


