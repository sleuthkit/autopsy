/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;

public class Trie<K, V> {

    private class Node<K, V> {

        private final Map<K, Node> children = new HashMap<>();
        private V leafValue = null;

        Node getOrAddChild(K childKey) {
            Node child = children.get(childKey);
            if (child == null) {
                child = new Node();
                children.put(childKey, child);
            }

            return child;
        }

        Node getChild(K childKey) {
            return children.get(childKey);
        }

        V getLeafValue() {
            return leafValue;
        }

        void setLeafValue(V leafValue) {
            this.leafValue = leafValue;
        }

    }

    public static class TrieResult<K, V> {

        private final V value;
        private final List<K> keys;
        private final boolean hasChildren;

        public TrieResult(V value, List<K> keys, boolean hasChildren) {
            this.value = value;
            this.keys = keys;
            this.hasChildren = hasChildren;
        }

        public V getValue() {
            return value;
        }

        public List<K> getKeys() {
            return keys;
        }

        public boolean hasChildren() {
            return hasChildren;
        }
    }

    private Node<K, V> root = new Node<>();

    public void add(Iterable<K> keyTokens, V leafValue) {
        Node node = root;
        for (K key : keyTokens) {
            node = node.getOrAddChild(key);
        }

        node.setLeafValue(leafValue);
    }

    public V getExact(Iterable<K> keys) {
        Node<K, V> node = root;
        for (K key : keys) {
            node = node.getChild(key);
            if (node == null) {
                return null;
            }
        }

        return node.getLeafValue();
    }

    public TrieResult<K, V> getDeepest(Iterable<K> keys) {
        Node<K, V> node = root;
        List<K> visited = new ArrayList<>();
        TrieResult<K, V> bestMatch = null;
        for (K key : keys) {
            if (node == null) {
                break;
            }

            if (node.getLeafValue() != null) {
                bestMatch = new TrieResult<K, V>(node.getLeafValue(), visited, MapUtils.isNotEmpty(node.children));
            }

            node = node.getChild(key);
            visited.add(key);
        }

        return bestMatch;
    }

    public TrieResult<K, V> getFirst(Iterable<K> keys) {
        Node<K, V> node = root;
        List<K> visited = new ArrayList<>();
        for (K key : keys) {
            if (node == null) {
                break;
            }

            if (node.getLeafValue() != null) {
                return new TrieResult<K, V>(node.getLeafValue(), visited, MapUtils.isNotEmpty(node.children));
            }

            node = node.getChild(key);
            visited.add(key);
        }

        return null;
    }
}
