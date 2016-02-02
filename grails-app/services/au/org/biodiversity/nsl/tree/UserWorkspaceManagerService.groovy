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


    Arrangement createWorkspace(Namespace namespace, String owner, String title, String description) {
        if(!owner) throw new IllegalArgumentException("owner may not be null");
        if(!title) throw new IllegalArgumentException("title may not be null");

        Event e = basicOperationsService.newEvent namespace, "Create workspace", owner
        return basicOperationsService.createWorkspace(e, owner, title, description)
    }

}
