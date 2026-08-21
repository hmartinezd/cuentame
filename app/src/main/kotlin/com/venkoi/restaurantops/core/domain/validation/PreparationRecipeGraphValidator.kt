package com.venkoi.restaurantops.core.domain.validation

import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeDependencyEdge
import javax.inject.Inject

class PreparationRecipeGraphValidator @Inject constructor() {

    fun hasCycle(edges: List<PreparationRecipeDependencyEdge>): Boolean {
        val adjacency = mutableMapOf<String, MutableList<String>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
        }

        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()

        fun isCyclicUtil(node: String): Boolean {
            if (recStack.contains(node)) return true
            if (visited.contains(node)) return false

            visited.add(node)
            recStack.add(node)

            val children = adjacency[node] ?: emptyList()
            for (child in children) {
                if (isCyclicUtil(child)) return true
            }

            recStack.remove(node)
            return false
        }

        for (node in adjacency.keys) {
            if (!visited.contains(node)) {
                if (isCyclicUtil(node)) return true
            }
        }

        return false
    }

    /**
     * Evaluates if adding proposed edges for a recipe would create a cycle in the graph.
     *
     * @param existingGraph All non-archived edges in the restaurant.
     * @param outputIngredientId The ID of the output ingredient of the recipe being edited.
     * @param proposedEdges Proposed edges for this specific recipe (output -> component).
     */
    fun wouldCreateCycle(
        existingGraph: List<PreparationRecipeDependencyEdge>,
        outputIngredientId: String,
        proposedEdges: List<PreparationRecipeDependencyEdge>
    ): Boolean {
        val filteredGraph = existingGraph.filter { it.fromId != outputIngredientId }
        return hasCycle(filteredGraph + proposedEdges)
    }
}
