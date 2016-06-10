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
        if (!owner) throw new IllegalArgumentException("owner may not be null");
        if (!title) throw new IllegalArgumentException("title may not be null");

        Event e = basicOperationsService.newEvent namespace, "Create workspace", owner
        Arrangement ws = basicOperationsService.createWorkspace(e, owner, title, description)

        checkout = DomainUtils.refetchNode(checkout)

        if (checkout) {
            basicOperationsService.adoptNode(ws.node, checkout, VersioningMethod.V, linkType: DomainUtils.getBoatreeUri('workspace-top-node'));
        }

        return ws;
    }

    void deleteWorkspace(Arrangement arrangement) {
        if (!arrangement) throw new IllegalArgumentException("arrangement may not be null");
        if (!arrangement.arrangementType == ArrangementType.U) throw new IllegalArgumentException("arrangement must be a workspace");
        basicOperationsService.deleteArrangement(arrangement);
    }


    void updateWorkspace(Arrangement arrangement, String title, String description) {
        if (!arrangement) throw new IllegalArgumentException("arrangement may not be null");
        if (!arrangement.arrangementType == ArrangementType.U) throw new IllegalArgumentException("arrangement must be a workspace");
        basicOperationsService.updateArrangement(arrangement, title, description);
    }

    def moveWorkspaceNode(Arrangement ws, Node target, Node node) {
        if (target == node) throw new IllegalArgumentException("node == target");

        if (node == ws.node) throw new IllegalArgumentException("node == ws.node");

        List<Node> reversePath = queryService.findPath(node, target)

        if (reversePath && !reversePath.isEmpty()) throw new IllegalArgumentException("node is supernode of target");

        if (DomainUtils.isCheckedIn(target)) {
            List<Node> pathToTarget = queryService.findPath(ws.node, target)
            if (pathToTarget.isEmpty()) throw new IllegalArgumentException("target not in workspace");

            target = basicOperationsService.checkoutNode(ws.node, target);

            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
        }


        List<Link> pathToNode = queryService.findPathLinks(ws.node, node);

        if (pathToNode.isEmpty()) throw new IllegalArgumentException("node not in workspace");

        Link parentLink = pathToNode.last();
        Node currentParent = parentLink.supernode;

        if (DomainUtils.isCheckedIn(currentParent)) {
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

        return [
                target  : target,
                modified: [target, currentParent]
        ]

    }

    def adoptNode(Arrangement ws, Node target, Node node) {
        if (target == node) throw new IllegalArgumentException("node == target");

        if (node == ws.node) throw new IllegalArgumentException("node == ws.node");

        if (!DomainUtils.isCheckedIn(node)) throw new IllegalArgumentException("cannot adopt draft node");
        if (DomainUtils.isReplaced(node)) throw new IllegalArgumentException("cannot adopt outdated node");

        List<Node> reversePath = queryService.findPath(node, target)

        if (reversePath && !reversePath.isEmpty()) throw new IllegalArgumentException("node is supernode of target");

        if (DomainUtils.isCheckedIn(target)) {
            List<Node> pathToTarget = queryService.findPath(ws.node, target)
            if (pathToTarget.isEmpty()) throw new IllegalArgumentException("target not in workspace");

            target = basicOperationsService.checkoutNode(ws.node, target);

            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
        }

        List<Link> pathToNode = queryService.findPathLinks(ws.node, node);

        if (!pathToNode.isEmpty()) throw new IllegalArgumentException("node already in workspace");

        basicOperationsService.adoptNode(target, node, VersioningMethod.V);

        return [
                target  : target,
                modified: [target]
        ]
    }


    def addNamesToNode(Arrangement ws, Node focus, List<?> names) {
        log.debug('addNamesToNode');
        if (!ws) throw new IllegalArgumentException("root may not be null");
        if (!focus) throw new IllegalArgumentException("focus may not be null");

        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("root must belong to a workspace");

        if (ws.node.checkedInAt) {
            throw new IllegalStateException("Workspace root nodes are never checked in");
        }

        if (focus.checkedInAt) {
            log.debug("about to checkout ${focus} in ${ws}");
            focus = basicOperationsService.checkoutNode(ws.node, focus);
            log.debug("checkout ok. New node is ${focus}");
        } else {
            log.debug("${focus} is already checked out");
        }

        names.each {
            log.debug('refetch root');
            ws = DomainUtils.refetchArrangement(ws);
            log.debug('refetch focus');
            focus = DomainUtils.refetchNode(focus);

            log.debug('check type of it');
            log.debug(it.class);

            if (it instanceof Name) {
                // TODO: DO NOT IGNORE THIS TODO
                // TODO: find if this name is already in the tree. If it is, check out the supernode and delete the link.
                log.debug('its a name. refetch it');
                Name n = DomainUtils.refetchName((Name) it);
                log.debug('name refetched. create the node');
                log.debug("oh by the way - now I have refethed it, it liks like this: ${n}");
                basicOperationsService.createDraftNode(focus, VersioningMethod.V, NodeInternalType.T, nslName: n)
                log.debug('all ok');
            } else if (it instanceof Instance) {
                // TODO: DO NOT IGNORE THIS TODO
                // TODO: find if this name is already in the tree. If it is, check out the supernode and delete the link.
                log.debug('its an instance. refetch it');
                Instance inst = DomainUtils.refetchInstance((Instance) it);
                log.debug('instance refetched. create the node');
                log.debug("oh by the way - now I have refethed it, it liks like this: ${inst}");
                basicOperationsService.createDraftNode(focus, VersioningMethod.V, NodeInternalType.T, nslInstance: inst)
                log.debug('all ok');
            } else {
                log.debug('I don\'t know what this is. throw an exception');
                throw new IllegalArgumentException("dont know how to add a ${it.class} to a node")
            }
            log.debug('added element ok');

        }
        log.debug('added all elements ok');

        return {
            target:
            focus
            modified:
            []
        }
    }

    def replaceDraftNodeWith(Node target, Node replacement) {
        log.debug('replaceDraftNodeWith');
        if (!target) throw new IllegalArgumentException("target may not be null");
        if (!replacement) throw new IllegalArgumentException("replacement may not be null");

        if (DomainUtils.isCheckedIn(target)) throw new IllegalArgumentException("target must be a draft node");
        if (target.root.node == target) throw new IllegalArgumentException("target must not be the root of a workspace");
        if (target.root.arrangementType != ArrangementType.U) throw new IllegalArgumentException("target must belong to a workspace");

        if (!DomainUtils.isCurrent(replacement)) throw new IllegalArgumentException("replacement must be current");
        if (DomainUtils.isEndNode(replacement)) throw new IllegalArgumentException("replacement must not be the end node");

        Link existingLink = DomainUtils.getDraftNodeSuperlink(target);

        Node supernode = existingLink.supernode;
        VersioningMethod vm = existingLink.getVersioningMethod();
        int seq = existingLink.getLinkSeq();
        Uri type = DomainUtils.getLinkTypeUri(existingLink);

        if (existingLink == null) {
            throw new IllegalStateException("draft node ${target} has no superlink!")
        }

        basicOperationsService.deleteDraftTree(target);

        supernode = DomainUtils.refetchNode(supernode);
        replacement = DomainUtils.refetchNode(replacement);
        type = DomainUtils.refetchUri(type);

        return basicOperationsService.adoptNode(
                supernode,
                replacement,
                vm,
                seq: seq,
                linkType: type
        );
    }

    def removeLink(Arrangement ws, Link link) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!link) throw new IllegalArgumentException("null link");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");
        if (ws.node.checkedInAt) {
            throw new IllegalStateException("Workspace root nodes are never checked in");
        }

        int ct = queryService.countPaths(ws.node, link.supernode);
        if (ct != 1) throw new IllegalArgumentException("supernode must appear only once in the workspace");

        Node focus = link.supernode;
        if (DomainUtils.isCheckedIn(focus)) {
            log.debug("about to checkout ${focus} in ${ws}");
            focus = basicOperationsService.checkoutNode(ws.node, focus);
            log.debug("checkout ok. New node is ${focus}");

            link = DomainUtils.refetchLink(link);
        } else {
            log.debug("${focus} is already checked out");
        }

        if (DomainUtils.isCheckedIn(link.subnode)) {
            basicOperationsService.deleteLink(focus, link.linkSeq);
        } else {
            basicOperationsService.deleteDraftTree(link.subnode);
        }

        focus = DomainUtils.refetchNode(focus);
        return focus;
    }

    def emptyCheckout(Arrangement ws, Node target) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!target) throw new IllegalArgumentException("null target");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if (DomainUtils.isCheckedIn(target)) {
            int paths = queryService.countPaths(ws.node, target);
            if (paths == 0) throw new IllegalArgumentException("target not in workspace");
            if (paths > 1) throw new IllegalArgumentException("target in workspace multiple places");
            target = basicOperationsService.checkoutNode(ws.node, target);
            target = DomainUtils.refetchNode(target);
        }

        return [
                target  : target,
                modified: [target]
        ]
    }


    def changeNodeName(Arrangement ws, Node target, Name name) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!target) throw new IllegalArgumentException("null target");
        if (!name) throw new IllegalArgumentException("null name");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if (DomainUtils.isCheckedIn(target)) {
            int paths = queryService.countPaths(ws.node, target);
            if (paths == 0) throw new IllegalArgumentException("target not in workspace");
            if (paths > 1) throw new IllegalArgumentException("target in workspace multiple places");
            target = basicOperationsService.checkoutNode(ws.node, target);
            target = DomainUtils.refetchNode(target);
            name = DomainUtils.refetchName(name);
        }


        basicOperationsService.updateDraftNode(target, nslName: name, nslInstance: null)

        return [
                target  : target,
                modified: [target]
        ]
    }

    def addNodeSubname(Arrangement ws, Node target, Name name) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!target) throw new IllegalArgumentException("null target");
        if (!name) throw new IllegalArgumentException("null name");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if (DomainUtils.isCheckedIn(target)) {
            int paths = queryService.countPaths(ws.node, target);
            if (paths == 0) throw new IllegalArgumentException("target not in workspace");
            if (paths > 1) throw new IllegalArgumentException("target in workspace multiple places");
            target = basicOperationsService.checkoutNode(ws.node, target);
            target = DomainUtils.refetchNode(target);
            name = DomainUtils.refetchName(name);
        }

        Node n = basicOperationsService.createDraftNode(target, VersioningMethod.V, NodeInternalType.T,
                // TODO: NODE TYPE
                nslName: name, nodeType: DomainUtils.uri('apc-voc', 'ApcConcept')
        );

        target = DomainUtils.refetchNode(target);

        return [
                target  : target,
                modified: [n]
        ]
    }


    def changeNodeInstance(Arrangement ws, Node target, Instance instance) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!target) throw new IllegalArgumentException("null target");
        if (!instance) throw new IllegalArgumentException("null instance");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if (DomainUtils.isCheckedIn(target)) {
            int paths = queryService.countPaths(ws.node, target);
            if (paths == 0) throw new IllegalArgumentException("target not in workspace");
            if (paths > 1) throw new IllegalArgumentException("target in workspace multiple places");
            target = basicOperationsService.checkoutNode(ws.node, target);
            target = DomainUtils.refetchNode(target);
            instance = DomainUtils.refetchInstance(instance);
        }

        basicOperationsService.updateDraftNode(target, nslName: instance.name, nslInstance: instance)
        target = DomainUtils.refetchNode(target);

        return [
                target  : target,
                modified: [target]
        ]
    }

    def addNodeSubinstance(Arrangement ws, Node target, Instance instance) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!target) throw new IllegalArgumentException("null target");
        if (!instance) throw new IllegalArgumentException("null instance");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if (DomainUtils.isCheckedIn(target)) {
            int paths = queryService.countPaths(ws.node, target);
            if (paths == 0) throw new IllegalArgumentException("target not in workspace");
            if (paths > 1) throw new IllegalArgumentException("target in workspace multiple places");
            target = basicOperationsService.checkoutNode(ws.node, target);
            target = DomainUtils.refetchNode(target);
            instance = DomainUtils.refetchInstance(instance);
        }

        Node n = basicOperationsService.createDraftNode(target, VersioningMethod.V, NodeInternalType.T,
                // TODO: NODE TYPE
                nslName: instance.name,
                nslInstance: instance,
                nodeType: DomainUtils.uri('apc-voc', 'ApcConcept')
        );

        target = DomainUtils.refetchNode(target);

        return [
                target  : target,
                modified: [n]
        ]
    }

    def setNodeType(Arrangement ws, Node target, Uri nodeType) {
        if (!ws) throw new IllegalArgumentException("null ws");
        if (!target) throw new IllegalArgumentException("null target");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if(target.internalType != NodeInternalType.T) throw new IllegalArgumentException("not a taxonomic node")
        if (!nodeType) nodeType = DomainUtils.getDefaultNodeTypeUriFor(target.internalType);

        if (DomainUtils.isCheckedIn(target)) {
            int paths = queryService.countPaths(ws.node, target);
            if (paths == 0) throw new IllegalArgumentException("target not in workspace");
            if (paths > 1) throw new IllegalArgumentException("target in workspace multiple places");
            target = basicOperationsService.checkoutNode(ws.node, target);
            target = DomainUtils.refetchNode(target);
        }

        basicOperationsService.updateDraftNode(target, nodeType: nodeType)

        target = DomainUtils.refetchNode(target);

        return [
                target  : target,
                modified: [target]
        ]

    }

    def performCheckin(Node node) {
        if(!node) throw new IllegalArgumentException("null node");
        if(node.checkedInAt) throw new IllegalArgumentException("node not draft");
        if(!node.prev) throw new IllegalArgumentException("node not a checkout");
        if(node.prev.replacedAt) throw new IllegalArgumentException("target checkin is already replaced");

        Event e = basicOperationsService.newEvent(node.namespace(), "checkin of ${node}")
        node = DomainUtils.refetchNode(node);
        basicOperationsService.persistNode(e, node);
        node = DomainUtils.refetchNode(node);
        Map<Node, Node> v = versioningService.getCheckinVersioningMap(node.root, node.prev.root, node);
        versioningService.performVersioning(e, v, node.prev.root);
        node = DomainUtils.refetchNode(node);
        basicOperationsService.moveNodeSubtreeIntoArrangement(node.root, node.prev.root, node);
        node = DomainUtils.refetchNode(node);

        // I am not going to attempt to display what has been changed.
        // The client is just going to have to refresh everything.

        return [
                target  : node,
                modified: [node]
        ]

    }
}
