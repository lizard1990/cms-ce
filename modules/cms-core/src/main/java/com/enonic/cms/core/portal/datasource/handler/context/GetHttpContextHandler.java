package com.enonic.cms.core.portal.datasource.handler.context;

import org.jdom.Document;

import com.enonic.cms.core.portal.datasource.handler.DataSourceRequest;
import com.enonic.cms.core.portal.datasource.handler.base.ParamDataSourceHandler;

public final class GetHttpContextHandler
    extends ParamDataSourceHandler
{
    public GetHttpContextHandler()
    {
        super( "getHttpContext" );
    }

    @Override
    public Document handle( final DataSourceRequest req )
        throws Exception
    {
        return null;
    }
}