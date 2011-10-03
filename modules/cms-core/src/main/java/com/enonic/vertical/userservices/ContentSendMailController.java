/*
 * Copyright 2000-2011 Enonic AS
 * http://www.enonic.com/license
 */
package com.enonic.vertical.userservices;

import java.io.IOException;
import java.text.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.enonic.esl.containers.ExtendedMap;
import com.enonic.vertical.engine.VerticalEngineException;

import com.enonic.cms.core.service.UserServicesService;

import com.enonic.cms.business.core.content.CreateContentException;
import com.enonic.cms.business.core.content.command.CreateContentCommand;

import com.enonic.cms.domain.SiteKey;
import com.enonic.cms.domain.content.contentdata.ContentDataParserException;
import com.enonic.cms.domain.content.contentdata.ContentDataParserInvalidDataException;
import com.enonic.cms.domain.content.contentdata.ContentDataParserUnsupportedTypeException;
import com.enonic.cms.domain.content.contentdata.InvalidContentDataException;
import com.enonic.cms.domain.content.contentdata.MissingRequiredContentDataException;
import com.enonic.cms.domain.portal.httpservices.UserServicesException;
import com.enonic.cms.domain.security.user.User;
import com.enonic.cms.domain.security.user.UserEntity;

/**
 * Extension of the standard sendmail servlet. <p/> <p> In addition to sending an email (using the functionality in {@link
 * SendMailController}), content is created in the category specified in the form. The content is created as specified in the appropriate
 * modulebuilder XML found in the database. So this servlet will naturally <i>only</i> work for modules created with the modulebuilder. </p>
 * <p> If this functionality is needed for other modules, the buildContentXML method can be overloaded in a child class. </p>
 */
public class ContentSendMailController
    extends SendMailController
{

    private final static int ERR_MISSING_CATEGORY_KEY = 150;

    protected void handlerCustom( HttpServletRequest request, HttpServletResponse response, HttpSession session, ExtendedMap formItems,
                                  UserServicesService userServices, SiteKey siteKey, String operation )
        throws VerticalUserServicesException, VerticalEngineException, IOException, ClassNotFoundException, IllegalAccessException,
        InstantiationException, ParseException
    {
        if ( operation.equals( "send" ) )
        {

            User oldUser = securityService.getOldUserObject();
            int categoryKey = formItems.getInt( "categorykey", -1 );

            if ( categoryKey == -1 )
            {
                String message = "Category key not specified.";
                VerticalUserServicesLogger.warn( this.getClass(), 0, message, null );
                redirectToErrorPage( request, response, formItems, ERR_MISSING_CATEGORY_KEY, null );
                return;
            }

            CreateContentCommand createContentCommand;
            try
            {
                createContentCommand = parseCreateContentCommand( formItems );
            }
            catch ( ContentDataParserInvalidDataException e )
            {
                String message = e.getMessage();
                VerticalUserServicesLogger.warn( this.getClass(), 0, message, null );
                redirectToErrorPage( request, response, formItems, ERR_PARAMETERS_INVALID, null );
                return;
            }
            catch ( ContentDataParserException e )
            {
                VerticalUserServicesLogger.error( this.getClass(), e.getMessage(), e );
                throw new UserServicesException( ERR_OPERATION_BACKEND );
            }
            catch ( ContentDataParserUnsupportedTypeException e )
            {
                VerticalUserServicesLogger.error( this.getClass(), e.getMessage(), e );
                throw new UserServicesException( ERR_OPERATION_BACKEND );
            }

            UserEntity runningUser = securityService.getUser( oldUser );

            createContentCommand.setAccessRightsStrategy( CreateContentCommand.AccessRightsStrategy.INHERIT_FROM_CATEGORY );

            createContentCommand.setCreator( runningUser );

            try
            {
                contentService.createContent( createContentCommand );
            }
            catch ( CreateContentException e )
            {
                RuntimeException cause = e.getRuntimeExceptionCause();

                if ( cause instanceof MissingRequiredContentDataException )
                {
                    String message = e.getMessage();
                    VerticalUserServicesLogger.warn( this.getClass(), 0, message, null );
                    redirectToErrorPage( request, response, formItems, ERR_PARAMETERS_MISSING, null );
                    return;
                }
                else if ( cause instanceof InvalidContentDataException )
                {
                    String message = e.getMessage();
                    VerticalUserServicesLogger.warn( this.getClass(), 0, message, null );
                    redirectToErrorPage( request, response, formItems, ERR_PARAMETERS_INVALID, null );
                    return;
                }
                else
                {
                    throw cause;
                }
            }
        }

        // call parent method to ensure inherited functionality
        super.handlerCustom( request, response, session, formItems, userServices, siteKey, operation );
    }

}

