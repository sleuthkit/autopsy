/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.url.analytics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;

class Trie<K, V> {

    private class Node<K, V> {

        private final Map<K, Node<K, V>> children = new HashMap<>();
        private V leafValue = null;

        Node<K, V> getOrAddChild(K childKey) {
            Node<K, V> child = children.get(childKey);
            if (child == null) {
                child = new Node();
                children.put(childKey, child);
            }

            return child;
        }

        Node<K, V> getChild(K childKey) {
            return children.get(childKey);
        }

        V getLeafValue() {
            return leafValue;
        }

        void setLeafValue(V leafValue) {
            this.leafValue = leafValue;
        }

    }

    static class TrieResult<K, V> {

        private final V value;
        private final List<K> keys;
        private final boolean hasChildren;

        TrieResult(V value, List<K> keys, boolean hasChildren) {
            this.value = value;
            this.keys = keys;
            this.hasChildren = hasChildren;
        }

        V getValue() {
            return value;
        }

        List<K> getKeys() {
            return keys;
        }

        boolean hasChildren() {
            return hasChildren;
        }
    }

    
    private Node<K, V> root = new Node<>();

    void add(Iterable<K> keyTokens, V leafValue) {
        Node<K, V> node = root;
        for (K key : keyTokens) {
            node = node.getOrAddChild(key);
        }

        node.setLeafValue(leafValue);
    }

    V getExact(Iterable<K> keys) {
        Node<K, V> node = root;
        for (K key : keys) {
            node = node.getChild(key);
            if (node == null) {
                return null;
            }
        }

        return node.getLeafValue();
    }

    TrieResult<K, V> getDeepest(Iterable<K> keys) {
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

    TrieResult<K, V> getFirst(Iterable<K> keys) {
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
