package au.org.biodiversity.nsl.tree

/**
 * Created by ibis on 29/01/2016.
 */


import au.org.biodiversity.nsl.*
import au.org.biodiversity.nsl.Link
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Name
import au.org.biodiversity.nsl.Instance
import grails.transaction.Transactional
import groovy.sql.Sql

import javax.sql.DataSource

@Transactional(rollbackFor = [ServiceException])
class UserWorkspaceManagerService {
    QueryService queryService;
    TreeOperationsService treeOperationsService;
    BasicOperationsService basicOperationsService;
    VersioningService versioningService;
    DataSource dataSource_nsl;


    Arrangement createWorkspace(String username, String description) {
        if(!username) throw new IllegalArgumentException("Username mak not be null");



        return null;
    }

}
