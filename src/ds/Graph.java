package ds;

import java.util.*;

public class Graph<T> {
    public Map<GraphNode<T>, Set<GraphNode<T>>> adjacencyList;
    public Map<Integer, GraphNode<T>> nodes;

    public Graph() {
        this.adjacencyList = new HashMap<>();
        this.nodes = new HashMap<>();
    }

    public void addNode(GraphNode<T> node) {
        nodes.put(node.getId(), node);
        if (!adjacencyList.containsKey(node)) {
            adjacencyList.put(node, new HashSet<>());
        }
    }

    public void addEdge(GraphNode<T> from, GraphNode<T> to) {
        // check if from and to nodes exist
        if (!nodes.containsKey(from.getId()) || !nodes.containsKey(to.getId())) {
            throw new IllegalArgumentException("Both nodes must be added to the graph before adding an edge.");
        }

        if (!adjacencyList.containsKey(from)) {
            adjacencyList.put(from, new HashSet<>());
        }
        adjacencyList.get(from).add(to);
    }

    public GraphNode<T> getNode(int id) {
        return nodes.get(id);
    }

    public List<GraphNode<T>> getPredecessors(GraphNode<T> node) {
        List<GraphNode<T>> predecessors = new ArrayList<>();
        for (Map.Entry<GraphNode<T>, Set<GraphNode<T>>> entry : adjacencyList.entrySet()) {
            if (entry.getValue().contains(node)) {
                predecessors.add(entry.getKey());
            }
        }
        return predecessors;
    }

    public List<GraphNode<T>> getSuccessors(GraphNode<T> node) {
        List<GraphNode<T>> successors = new ArrayList<>();
        if (!adjacencyList.containsKey(node)) {
            return successors;
        }
        for (var target : adjacencyList.get(node)) {
            successors.add(nodes.get(target.getId()));
        }
        return successors;
    }
}