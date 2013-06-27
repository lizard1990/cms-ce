/*
 * Copyright 2000-2013 Enonic AS
 * http://www.enonic.com/license
 */

package com.enonic.cms.core.security.userstore.config;


import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import com.enonic.cms.core.user.field.UserFieldType;

import static org.junit.Assert.*;

public class UserStoreConfigTest
{
    @Test
    public void getUserFieldTypes()
    {
        // setup
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( new UserStoreUserFieldConfig( UserFieldType.NICK_NAME ) );
        userStoreConfig.addUserFieldConfig( new UserStoreUserFieldConfig( UserFieldType.TITLE ) );

        // exercise
        List<UserFieldType> configuredTypesOnly = Lists.newArrayList( userStoreConfig.getUserFieldTypes() );

        // verify
        assertEquals( 2, configuredTypesOnly.size() );
        assertEquals( "nick-name", configuredTypesOnly.get( 0 ).getName() );
        assertEquals( "title", configuredTypesOnly.get( 1 ).getName() );
    }

    @Test
    public void getRemoteOnlyUserFieldTypes()
    {
        // setup
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserFieldConfig( UserFieldType.NICK_NAME, "remote" ) );
        userStoreConfig.addUserFieldConfig( new UserStoreUserFieldConfig( UserFieldType.TITLE ) );

        // exercise
        List<UserFieldType> configuredTypesOnly = Lists.newArrayList( userStoreConfig.getRemoteOnlyUserFieldTypes() );

        // verify
        assertEquals( 1, configuredTypesOnly.size() );
        assertEquals( "nick-name", configuredTypesOnly.get( 0 ).getName() );
    }

    @Test
    public void getLocalOnlyUserFieldTypes()
    {
        // setup
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserFieldConfig( UserFieldType.NICK_NAME, "remote" ) );
        userStoreConfig.addUserFieldConfig( new UserStoreUserFieldConfig( UserFieldType.TITLE ) );

        // exercise
        List<UserFieldType> configuredTypesOnly = Lists.newArrayList( userStoreConfig.getLocalOnlyUserFieldTypes() );

        // verify
        assertEquals( 1, configuredTypesOnly.size() );
        assertEquals( "title", configuredTypesOnly.get( 0 ).getName() );
    }

    private UserStoreUserFieldConfig createUserFieldConfig( UserFieldType type, String attributes )
    {
        UserStoreUserFieldConfig config = new UserStoreUserFieldConfig( type );
        if ( attributes.contains( "read-only" ) )
        {
            config.setReadOnly( true );
        }
        if ( attributes.contains( "remote" ) )
        {
            config.setRemote( true );
        }
        return config;
    }

}
