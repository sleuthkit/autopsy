package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

@ServiceProvider(service=DataExplorer.class, position=300)
public class KeywordSearchDataExplorer implements DataExplorer  {
    
    private static KeywordSearchDataExplorer theInstance;
    private KeywordSearchTopComponent tc;
    
    public KeywordSearchDataExplorer() {
        this.setTheInstance();
        this.tc = new KeywordSearchTopComponent();
        this.tc.addSearchButtonListener(new ActionListener()  {

            @Override
            public void actionPerformed(ActionEvent e) {
                search(tc.getQueryText());
            }
        });
    }
    
    private synchronized void setTheInstance() {
        if (theInstance == null) {
            theInstance = this;
        } else {
            throw new RuntimeException("NOOO!!! Mulitple instances of KeywordSearchTopComponent! BAD!");
        }
    }
    
    private void search(String solrQuery) {
        
        List<FsContent> matches = new ArrayList<FsContent>();
        
        boolean allMatchesFetched = false;
        final int ROWS_PER_FETCH = 10000;
        

        
        SolrServer solr = Server.getServer().getSolr();
        
                            SolrQuery q = new SolrQuery();
        q.setQuery(solrQuery);
        q.setRows(ROWS_PER_FETCH);
        q.setFields("id");

        for (int start = 0; !allMatchesFetched; start = start + ROWS_PER_FETCH) {
            

        
            q.setStart(start);
        
            try {
                QueryResponse response = solr.query(q);
                SolrDocumentList resultList = response.getResults();
                long results = resultList.getNumFound();
                
                allMatchesFetched = start + ROWS_PER_FETCH >= results;
                
                for (SolrDocument resultDoc : resultList) {
                    long id = Long.parseLong((String) resultDoc.getFieldValue("id"));

                    SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();
                    
                    // TODO: has to be a better way to get files. Also, need to 
                    // check that we actually get 1 hit for each id
                    ResultSet rs = sc.runQuery("select * from tsk_files where obj_id=" + id);
                    matches.addAll(sc.resultSetToFsContents(rs));
                    rs.close();
                }

            } catch (SolrServerException ex) {
                // TODO: handle bad query strings, among other issues
            } catch (SQLException ex) {
                // TODO: handle error getting files from database
            }

        }
        
            String pathText = "Solr query: " + solrQuery;
            Node rootNode = new KeywordSearchNode(matches, pathText);
            
            TopComponent searchResultWin = DataResultTopComponent.createInstance("Keyword search", pathText, rootNode, matches.size());
            searchResultWin.requestActive(); // make it the active top component
    }
    
    
    @Override
    public org.openide.windows.TopComponent getTopComponent() {
        return this.tc;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

    }
    
}
