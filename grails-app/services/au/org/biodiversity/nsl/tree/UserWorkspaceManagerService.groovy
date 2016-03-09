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


    Arrangement createWorkspace(Namespace namespace, String owner, String title, String description, Node checkout) {
        if(!owner) throw new IllegalArgumentException("owner may not be null");
        if(!title) throw new IllegalArgumentException("title may not be null");

        Event e = basicOperationsService.newEvent namespace, "Create workspace", owner
        Arrangement ws = basicOperationsService.createWorkspace(e, owner, title, description)

        if(checkout) {
            basicOperationsService.adoptNode(ws.workingRoot, checkout, VersioningMethod.V, linkType: DomainUtils.getBoatreeUri('workspace-top-node'));
        }

        return ws;
    }

    void deleteWorkspace(Arrangement arrangement) {
        if(!arrangement) throw new IllegalArgumentException("arrangement may not be null");
        if(!arrangement.arrangementType == ArrangementType.U)  throw new IllegalArgumentException("arrangement must be a workspace");
        basicOperationsService.deleteArrangement(arrangement);
    }


    void updateWorkspace(Arrangement arrangement, String title, String description) {
        if(!arrangement) throw new IllegalArgumentException("arrangement may not be null");
        if(!arrangement.arrangementType == ArrangementType.U)  throw new IllegalArgumentException("arrangement must be a workspace");
        basicOperationsService.updateArrangement(arrangement, title, description);
    }

    Node addNamesToNode(Node root, Node focus, List<?> names) {
        log.debug('addNamesToNode');
        if(!root) throw new IllegalArgumentException("root may not be null");
        if(!focus) throw new IllegalArgumentException("focus may not be null");

        if(root.checkedInAt) throw new IllegalArgumentException("root must be a draft node");

        if(root.root.arrangementType != ArrangementType.U) throw new IllegalArgumentException("root must belong to a workspace");

        if(focus.checkedInAt) {
            log.debug("about to checkout ${focus} in ${root}");
            focus = basicOperationsService.checkoutNode(root, focus);
            log.debug("checkout ok. New node is ${focus} in ${root}");
        }
        else {
            log.debug("${focus} is already checked out");
        }

        names.each {
            log.debug('refetch root');
            root = DomainUtils.refetchNode(root);
            log.debug('refetch focus');
            focus = DomainUtils.refetchNode(focus);

            log.debug('check type of it');
            log.debug(it.class);

            if(it instanceof Name) {
                // TODO: DO NOT IGNORE THIS TODO
                // TODO: find if this name is already in the tree. If it is, check out the supernode and delete the link.
                log.debug('its a name. refetch it');
                Name n = DomainUtils.refetchName((Name)it);
                log.debug('name refetched. create the node');
                log.debug("oh by the way - now I have refethed it, it liks like this: ${n}");
                basicOperationsService.createDraftNode(focus, VersioningMethod.V, NodeInternalType.T, nslName: n)
                log.debug('all ok');
            }
            else if(it instanceof Instance) {
                // TODO: DO NOT IGNORE THIS TODO
                // TODO: find if this name is already in the tree. If it is, check out the supernode and delete the link.
                log.debug('its an instance. refetch it');
                Instance inst = DomainUtils.refetchInstance((Instance)it);
                log.debug('instance refetched. create the node');
                log.debug("oh by the way - now I have refethed it, it liks like this: ${inst}");
                basicOperationsService.createDraftNode(focus, VersioningMethod.V, NodeInternalType.T, nslInstance: inst)
                log.debug('all ok');
            }
            else {
                log.debug('I don\'t know what this is. throw an exception');
                throw new IllegalArgumentException("dont know how to add a ${it.class} to a node")
            }
            log.debug('added element ok');

        }
        log.debug('added all elements ok');

        return focus;
    }
}
