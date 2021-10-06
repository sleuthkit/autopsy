/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.SearchResultChildFactory.ChildKey;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.SearchResultsDTO;

/**
 *
 * @author gregd
 */
public abstract class SearchResultChildFactory<T extends SearchResultsDTO<S>, S> extends ChildFactory<ChildKey<S, T>> {

    private T results;

    public SearchResultChildFactory() {
        this(null);
    }
    
    public SearchResultChildFactory(T initialResults) {
        this.results = initialResults;
    }

    @Override
    protected boolean createKeys(List<ChildKey<S, T>> toPopulate) {
        T results = this.results;

        if (results != null) {
            List<ChildKey<S, T>> childKeys = results.getItems().stream()
                    .map((item) -> new ChildKey<>(results, item))
                    .collect(Collectors.toList());

            toPopulate.addAll(childKeys);
        }

        return true;
    }

    @Override
    protected Node createNodeForKey(ChildKey<S, T> key) {
        return createNodeForKey(key.getSearchResults(), key.getChild());
    }
    
    protected abstract Node createNodeForKey(T searchResults, S itemData);

    public void update(T newResults) {
        this.results = newResults;
        this.refresh(false);
    }
    
    public int getResultCount() {
        return results == null ? 0 : results.getTotalResultsCount();
    }

            
    static class ChildKey<S, T extends SearchResultsDTO<S>> {

        private final T searchResults;
        private final S child;

        ChildKey(T searchResults, S child) {
            this.searchResults = searchResults;
            this.child = child;
        }

        T getSearchResults() {
            return searchResults;
        }

        S getChild() {
            return child;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.child);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ChildKey<?, ?> other = (ChildKey<?, ?>) obj;
            if (!Objects.equals(this.child, other.child)) {
                return false;
            }
            return true;
        }

    }
}
