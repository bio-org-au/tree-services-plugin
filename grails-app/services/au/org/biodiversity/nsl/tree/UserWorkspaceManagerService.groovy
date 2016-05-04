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

        checkout = DomainUtils.refetchNode(checkout)

        if(checkout) {
            basicOperationsService.adoptNode(ws.node, checkout, VersioningMethod.V, linkType: DomainUtils.getBoatreeUri('workspace-top-node'));
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

    Node moveWorkspaceNode(Arrangement ws, Node target, Node node) {
        if(target == node) throw new IllegalArgumentException("node == target");

        if(node == ws.node) throw new IllegalArgumentException("node == ws.node");

        List<Node> reversePath = queryService.findPath(node, target)

        if(reversePath && !reversePath.isEmpty()) throw new IllegalArgumentException("node is supernode of target");

        if(DomainUtils.isCheckedIn(target)) {
            List<Node> pathToTarget = queryService.findPath(ws.node, target)
            if(pathToTarget.isEmpty()) throw new IllegalArgumentException("target not in workspace");

            target = basicOperationsService.checkoutNode(ws.node, target);

            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
        }


        List<Link> pathToNode =  queryService.findPathLinks(ws.node, node);

        if(pathToNode.isEmpty()) throw new IllegalArgumentException("node not in workspace");

        Link parentLink = pathToNode.last();
        Node currentParent = parentLink.supernode;

        if(DomainUtils.isCheckedIn(currentParent)) {
            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
            currentParent = DomainUtils.refetchNode(currentParent);
            parentLink = DomainUtils.refetchLink(parentLink);

            currentParent = basicOperationsService.checkoutNode(ws.node, currentParent);
            currentParent = DomainUtils.refetchNode(currentParent);
            parentLink = DomainUtils.refetchLink(parentLink);

            parentLink = Link.findBySupernodeAndLinkSeq(currentParent, parentLink.linkSeq);

        }

        basicOperationsService.simpleMoveDraftLink(parentLink, target);
    }

    Node adoptNode(Arrangement ws, Node target, Node node) {
        if(target == node) throw new IllegalArgumentException("node == target");

        if(node == ws.node) throw new IllegalArgumentException("node == ws.node");

        if(!DomainUtils.isCheckedIn(node)) throw new IllegalArgumentException("cannot adopt draft node");
        if(DomainUtils.isReplaced(node)) throw new IllegalArgumentException("cannot adopt outdated node");

        List<Node> reversePath = queryService.findPath(node, target)

        if(reversePath && !reversePath.isEmpty()) throw new IllegalArgumentException("node is supernode of target");

        if(DomainUtils.isCheckedIn(target)) {
            List<Node> pathToTarget = queryService.findPath(ws.node, target)
            if(pathToTarget.isEmpty()) throw new IllegalArgumentException("target not in workspace");

            target = basicOperationsService.checkoutNode(ws.node, target);

            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
        }

        List<Link> pathToNode =  queryService.findPathLinks(ws.node, node);

        if(!pathToNode.isEmpty()) throw new IllegalArgumentException("node already in workspace");

        basicOperationsService.adoptNode(target, node, VersioningMethod.V);

        return target
    }



    Node addNamesToNode(Arrangement ws, Node focus, List<?> names) {
        log.debug('addNamesToNode');
        if(!ws) throw new IllegalArgumentException("root may not be null");
        if(!focus) throw new IllegalArgumentException("focus may not be null");

        if(ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("root must belong to a workspace");

        if(ws.node.checkedInAt) {
            throw new IllegalStateException("Workspace root nodes are never checked in");
        }

        if(focus.checkedInAt) {
            log.debug("about to checkout ${focus} in ${ws}");
            focus = basicOperationsService.checkoutNode(ws.node, focus);
            log.debug("checkout ok. New node is ${focus}");
        }
        else {
            log.debug("${focus} is already checked out");
        }

        names.each {
            log.debug('refetch root');
            ws = DomainUtils.refetchArrangement(ws);
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
