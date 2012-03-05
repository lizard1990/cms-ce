package com.enonic.cms.core.search;

import java.util.Collection;
import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.enonic.cms.core.content.ContentKey;
import com.enonic.cms.core.search.index.ContentIndexData;

/**
 * Created by IntelliJ IDEA.
 * User: rmh
 * Date: 2/23/12
 * Time: 10:17 AM
 */
public interface ElasticSearchIndexService
{
    public Client getClient();

    public void initalizeIndex( String indexName, boolean forceDelete );

    public void createIndex( String indexName );

    public void deleteIndex( String indexName );

    public void updateIndexSettings( String indexName );

    public void putMapping( String indexName, IndexType indexType, String mapping );

    public boolean delete( String indexName, IndexType indexType, ContentKey contentKey );

    public void index( String indexName, Collection<ContentIndexData> contentIndexDatas );

    public void index( String indexName, ContentIndexData contentIndexData );

    public boolean get( String indexName, IndexType indexType, ContentKey contentKey );

    public void optimize( String indexName );

    public SearchResponse search( String indexName, IndexType indexType, SearchSourceBuilder sourceBuilder );

    public SearchResponse search( String indexName, IndexType indexType, String sourceBuilder );

    public Map<String, GetField> search( String indexName, IndexType indexType, ContentKey contentKey );

    public void flush( String indexName );

    public boolean indexExists( String indexName );

}


