package com.enonic.cms.core.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Sets;

import com.enonic.cms.core.content.ContentEntityFetcherImpl;
import com.enonic.cms.core.content.ContentIndexEntity;
import com.enonic.cms.core.content.ContentKey;
import com.enonic.cms.core.content.category.CategoryKey;
import com.enonic.cms.core.content.contenttype.ContentTypeKey;
import com.enonic.cms.core.content.index.AggregatedQuery;
import com.enonic.cms.core.content.index.AggregatedResult;
import com.enonic.cms.core.content.index.ContentDocument;
import com.enonic.cms.core.content.index.ContentIndexQuery;
import com.enonic.cms.core.content.index.ContentIndexService;
import com.enonic.cms.core.content.index.IndexValueQuery;
import com.enonic.cms.core.content.index.IndexValueResultImpl;
import com.enonic.cms.core.content.index.IndexValueResultSet;
import com.enonic.cms.core.content.index.IndexValueResultSetImpl;
import com.enonic.cms.core.content.resultset.ContentResultSet;
import com.enonic.cms.core.content.resultset.ContentResultSetLazyFetcher;
import com.enonic.cms.core.content.resultset.ContentResultSetNonLazy;
import com.enonic.cms.core.search.builder.ContentIndexData;
import com.enonic.cms.core.search.builder.ContentIndexDataFactory;
import com.enonic.cms.core.search.query.IndexQueryException;
import com.enonic.cms.core.search.query.IndexValueQueryTranslator;
import com.enonic.cms.core.search.query.QueryTranslator;
import com.enonic.cms.store.dao.ContentDao;

/**
 * This class implements the content index service based on elasticsearch
 */
public class ContentIndexServiceImpl
    implements ContentIndexService
{
    public final static String CONTENT_INDEX_NAME = "cms";

    private IndexMappingProvider indexMappingProvider;

    private ElasticSearchIndexService elasticSearchIndexService;

    private final Logger LOG = Logger.getLogger( ContentIndexServiceImpl.class.getName() );

    private final ContentIndexDataFactory contentIndexDataFactory = new ContentIndexDataFactory();

    private QueryTranslator queryTranslator;

    private final IndexValueQueryTranslator indexValueQueryTranslator = new IndexValueQueryTranslator();

    private ContentDao contentDao;

    @PostConstruct
    public void initializeContentIndex()
    {
        final boolean indexExists = elasticSearchIndexService.indexExists( CONTENT_INDEX_NAME );

        if ( !indexExists )
        {
            elasticSearchIndexService.createIndex( CONTENT_INDEX_NAME );
            addMapping();
        }
        else
        {
            verifyMapping();
        }
    }

    // TODO: Implement
    private void verifyMapping()
    {

    }

    private void addMapping()
    {
        doAddMapping( IndexType.Content );
        doAddMapping( IndexType.Binaries );
    }

    private void doAddMapping( IndexType indexType )
    {
        String mapping = getMapping( indexType );
        elasticSearchIndexService.putMapping( CONTENT_INDEX_NAME, indexType.toString(), mapping );
    }

    private String getMapping( final IndexType indexType )
    {
        return indexMappingProvider.getMapping( CONTENT_INDEX_NAME, indexType.toString() );
    }

    public int remove( ContentKey contentKey )
    {
        // TODO : Delete children aswell
        final boolean deleted = elasticSearchIndexService.delete( CONTENT_INDEX_NAME, IndexType.Content, contentKey );

        if ( deleted )
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }

    public void removeByCategory( CategoryKey categoryKey )
    {
        ContentIndexQuery contentIndexQuery = new ContentIndexQuery( "" );
        contentIndexQuery.setCategoryFilter( Arrays.asList( categoryKey ) );
        doRemoveByQuery( contentIndexQuery );
    }

    public void removeByContentType( ContentTypeKey contentTypeKey )
    {
        ContentIndexQuery contentIndexQuery = new ContentIndexQuery( "" );
        contentIndexQuery.setContentTypeFilter( Arrays.asList( contentTypeKey ) );
        doRemoveByQuery( contentIndexQuery );
    }

    private void doRemoveByQuery( ContentIndexQuery contentIndexQuery )
    {
        final SearchSourceBuilder build;

        try
        {
            build = queryTranslator.build( contentIndexQuery );
        }
        catch ( Exception e )
        {
            throw new ContentIndexException( "Failed to build query: " + contentIndexQuery.toString(), e );
        }

        SearchHits hits = doExecuteSearchRequest( build );

        final int entriesToDelete = hits.getHits().length;

        LOG.fine( "Prepare to delete: " + entriesToDelete + " entries from index " + CONTENT_INDEX_NAME );

        for ( SearchHit hit : hits )
        {
            elasticSearchIndexService.delete( CONTENT_INDEX_NAME, IndexType.Content, new ContentKey( hit.getId() ) );
        }

        LOG.fine( "Deleted from index " + CONTENT_INDEX_NAME + ", " + entriesToDelete + " entries successfully" );
    }


    public void index( ContentDocument doc, boolean deleteExisting )
    {
        ContentIndexData contentIndexData = contentIndexDataFactory.create( doc );

        elasticSearchIndexService.index( CONTENT_INDEX_NAME, contentIndexData );
    }

    public void indexBulk( List<ContentDocument> docs )
    {

        Set<ContentIndexData> contentIndexDatas = Sets.newHashSet();

        for ( ContentDocument doc : docs )
        {
            ContentIndexData contentIndexData = contentIndexDataFactory.create( doc );

            contentIndexDatas.add( contentIndexData );
        }

        elasticSearchIndexService.index( CONTENT_INDEX_NAME, contentIndexDatas );
    }


    public boolean isIndexed( ContentKey contentKey )
    {
        return elasticSearchIndexService.get( CONTENT_INDEX_NAME, IndexType.Content, contentKey );
    }

    public void optimize()
    {
        elasticSearchIndexService.optimize( CONTENT_INDEX_NAME );
    }

    public ContentResultSet query( ContentIndexQuery query )
    {
        final SearchSourceBuilder build;
        try
        {
            build = this.queryTranslator.build( query );
        }
        catch ( Exception e )
        {
            throw new IndexQueryException( "Failed to translate query: " + query.getQuery(), e );
        }

        final SearchHits hits = doExecuteSearchRequest( build );

        LOG.finer( "query: " + build.toString() + " executed with " + hits.getHits().length + " hits of total " + hits.getTotalHits() );

        final int queryResultTotalSize = new Long( hits.getTotalHits() ).intValue();

        if ( query.getIndex() > queryResultTotalSize )
        {
            final ContentResultSetNonLazy rs = new ContentResultSetNonLazy( query.getIndex() );
            rs.addError( "Index greater than result count: " + query.getIndex() + " greater than " + queryResultTotalSize );
            return rs;
        }

        final int fromIndex = Math.max( query.getIndex(), 0 );

        final ArrayList<ContentKey> keys = new ArrayList<ContentKey>();

        for ( SearchHit hit : hits )
        {
            keys.add( new ContentKey( hit.getId() ) );
        }

        return new ContentResultSetLazyFetcher( new ContentEntityFetcherImpl( contentDao ), keys, fromIndex, queryResultTotalSize );
    }

    public IndexValueResultSet query( IndexValueQuery query )
    {
        final SearchSourceBuilder build;

        try
        {
            build = this.indexValueQueryTranslator.build( query );
        }
        catch ( Exception e )
        {
            throw new IndexQueryException( "Failed to translate query: " + query, e );
        }

        IndexValueResultSetImpl resultSet = new IndexValueResultSetImpl( query.getIndex(), query.getCount() );

        LOG.finer(
            "query: " + build.toString() + " executed with " + resultSet.getCount() + " hits of total " + resultSet.getTotalCount() );

        final SearchHits hits = doExecuteSearchRequest( build );

        for ( SearchHit hit : hits )
        {
            resultSet.add( createIndexValueResult( hit ) );
        }

        return resultSet;
    }

    private IndexValueResultImpl createIndexValueResult( SearchHit hit )
    {
        final Map<String, SearchHitField> fields = hit.getFields();

        if ( fields.size() != 1 )
        {
            throw new ContentIndexException( "Expected one field hit for query, found " + fields.size() );
        }

        ContentKey contentKey = new ContentKey( hit.getId() );

        final Map.Entry<String, SearchHitField> next = fields.entrySet().iterator().next();

        final String value = (String) next.getValue().getValue();

        return new IndexValueResultImpl( contentKey, value );
    }


    private SearchHits doExecuteSearchRequest( SearchSourceBuilder searchSourceBuilder )
    {
        final SearchResponse res =
            elasticSearchIndexService.search( CONTENT_INDEX_NAME, IndexType.Content.toString(), searchSourceBuilder );

        return res.getHits();
    }

    public SearchResponse query( String query )
    {
        return elasticSearchIndexService.search( CONTENT_INDEX_NAME, IndexType.Content.toString(), query );
    }

    public void flush()
    {
        elasticSearchIndexService.flush( CONTENT_INDEX_NAME );
    }

    // TODO: We dont implement this one yet
    public AggregatedResult query( AggregatedQuery query )
    {
        throw new RuntimeException(
            "Method not implemented in class " + this.getClass().getName() + ": " + "query( AggregatedQuery query )" );
        //return null;
    }

    @Override
    public void initializeMapping()
    {
        elasticSearchIndexService.deleteMapping( CONTENT_INDEX_NAME, IndexType.Content );
        elasticSearchIndexService.deleteMapping( CONTENT_INDEX_NAME, IndexType.Binaries );
        addMapping();
    }

    @Autowired
    public void setIndexMappingProvider( IndexMappingProvider indexMappingProvider )
    {
        this.indexMappingProvider = indexMappingProvider;
    }

    @Autowired
    public void setContentDao( ContentDao contentDao )
    {
        this.contentDao = contentDao;
    }

    @Autowired
    public void setQueryTranslator( QueryTranslator queryTranslator )
    {
        this.queryTranslator = queryTranslator;
    }

    @Autowired
    public void setElasticSearchIndexService( ElasticSearchIndexService elasticSearchIndexService )
    {
        this.elasticSearchIndexService = elasticSearchIndexService;
    }

    @Override
    public Collection<ContentIndexEntity> getContentIndexedFields( ContentKey contentKey )
    {
        final Map<String, GetField> fields =
                elasticSearchIndexService.search( CONTENT_INDEX_NAME, IndexType.Content, contentKey );

        final ElasticSearchIndexedFieldsTranslator indexFieldsTranslator = new ElasticSearchIndexedFieldsTranslator();
        return indexFieldsTranslator.generateContentIndexFieldSet( contentKey, fields );
    }


}