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
import grails.converters.JSON
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


    Arrangement createWorkspace(Namespace namespace, Arrangement baseTree, String owner, boolean shared, String title, String description) {
        if (!owner) throw new IllegalArgumentException("owner may not be null");
        if (!title) throw new IllegalArgumentException("title may not be null");
        if (!baseTree) throw new IllegalArgumentException("baseTree may not be null");

        if (baseTree.arrangementType != ArrangementType.P) {
            throw new IllegalArgumentException("baseTree must be a classifcation");
        }

        Event e = basicOperationsService.newEvent namespace, "Create workspace on ${baseTree.label} for ${owner}", owner
        baseTree = DomainUtils.refetchArrangement(baseTree);

        Arrangement ws = basicOperationsService.createWorkspace(e, baseTree, owner, shared, title, description)

        baseTree = DomainUtils.refetchArrangement(baseTree);

        Node checkout = DomainUtils.getSingleSubnode(baseTree.node)

        basicOperationsService.adoptNode(ws.node, checkout, VersioningMethod.V, linkType: DomainUtils.getBoatreeUri('workspace-top-node'));

        ws = DomainUtils.refetchArrangement(ws)

        return ws;
    }

    void deleteWorkspace(Arrangement arrangement) {
        if (!arrangement) throw new IllegalArgumentException("arrangement may not be null");
        if (!arrangement.arrangementType == ArrangementType.U) throw new IllegalArgumentException("arrangement must be a workspace");
        basicOperationsService.deleteArrangement(arrangement);
    }


    void updateWorkspace(Arrangement arrangement, boolean shared, String title, String description) {
        if (!arrangement) throw new IllegalArgumentException("arrangement may not be null");
        if (!arrangement.arrangementType == ArrangementType.U) throw new IllegalArgumentException("arrangement must be a workspace");
        basicOperationsService.updateWorkspace(arrangement, shared, title, description);
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

    def moveWorkspaceSubnodes(Arrangement ws, Node target, Node node) {
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

        if (DomainUtils.isCheckedIn(node)) {
            List<Node> pathToNode = queryService.findPath(ws.node, node)
            if (pathToNode.isEmpty()) throw new IllegalArgumentException("node not in workspace");

            node = basicOperationsService.checkoutNode(ws.node, node);

            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
        }

        Set<Link> links = new HashSet<Link>(node.subLink.findAll { it.subnode.internalType == NodeInternalType.T });

        Link prevLink = null;

        for (Link l : links) {
            l = DomainUtils.refetchLink(l);
            if (DomainUtils.isCheckedIn(l.subnode)) {
                prevLink = basicOperationsService.adoptNode(target, l.subnode, l.versioningMethod, linkType: DomainUtils.getRawLinkTypeUri(l), prevLink: prevLink);
                l = DomainUtils.refetchLink(l);
                basicOperationsService.deleteLink(l.supernode, l.linkSeq);
            } else {
                // this is failing when more than one link needs doing, becaue
                prevLink = basicOperationsService.simpleMoveDraftLink(l, DomainUtils.refetchNode(target), prevLink: prevLink);
            }

            ws = DomainUtils.refetchArrangement(ws);
            target = DomainUtils.refetchNode(target);
            node = DomainUtils.refetchNode(node);
        }

        return [
                target  : target,
                modified: [target, node]
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

        if (target.internalType != NodeInternalType.T) throw new IllegalArgumentException("not a taxonomic node")
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
        if (!node) throw new IllegalArgumentException("null node");
        if (node.checkedInAt) throw new IllegalArgumentException("node not draft");
        if (!node.prev) throw new IllegalArgumentException("node not a checkout");
        if (node.prev.replacedAt) throw new IllegalArgumentException("target checkin is already replaced");

        Event e = basicOperationsService.newEvent(node.namespace(), "checkin of ${node}")
        node = DomainUtils.refetchNode(node);
        basicOperationsService.createCopiesOfAllNonTreeNodes(e, node);
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

    ////////////////////////////////////////////
    // these operations are the two operations required for the NSL-Editor. Yes, we are re-inventing the wheel here.

    String nn(Node n) {
        if (n == null) return 'null';
        else return "${n.id} ${n.name?.simpleName} ${n.checkedInAt ? "" : " (DRAFT)"}"
    }

    String ll(Link l) {
        if (l == null) return 'null';
        else return "${nn(l.supernode)} -> [${l.id}] -> ${nn(l.subnode)}"
    }

    Message placeNameOnTree(Arrangement ws, Name name, Instance instance, Name parentName, Uri placementType) {

        try {

            if (!ws) throw new IllegalArgumentException("null tree");
            if (!name) throw new IllegalArgumentException("null name");
            if (!instance) throw new IllegalArgumentException("null instance");
            if (!placementType) throw new IllegalArgumentException("null placementType");
            if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

            Message error = Message.makeMsg(Msg.placeNameOnTree, [name, ws]);

            /**
             * Ok, to place a name on the tree, that name must not have any synonyms elsewhere on the tree,
             * nor should it have synonyms that are elsewhere on the tree.
             *
             * If the name is placed under some other name, the the other name it is to be placed under
             * must be of higher rank.
             *
             * If the name is being placed under a name that is is generic or below then,
             * then the common part of the names must match unless the name being placed under it is an excluded name.
             *
             * If the name is already on the tree as an accepted name, then this operation is a move of that node.
             *
             * If the name is already on the tree as an accepted name, and the parent name of that placement is the same
             * as the required parent name, then this is simply an update of the node.
             */


            Link currentLink = queryService.findCurrentNslNameInTreeOrBaseTree(ws, name)

            log.debug("current link is ${ll(currentLink)}");

            // CHECK FOR SYNONYMS
            // this query returns the relationship instance
            List<Instance> l = queryService.findSynonymsOfInstanceInTree(ws, instance);

            log.debug("findSynonymsOfInstanceInTree: ${l}");

            if (!l.isEmpty()) {
                Message mm = Message.makeMsg(Msg.HAS_SYNONYM_ALREADY_IN_TREE)
                error.nested.add(mm);
                for (Instance i : l) {
                    if (!currentLink || i.cites.name != name)
                        mm.nested.add(Message.makeMsg(Msg.HAS_SYNONYM_ALREADY_IN_TREE_item, [i.cites, i.instanceType.ofLabel]))
                }
            }

            // CHECK FOR SYNONYMS
            // this query returns the relationship instance
            l = queryService.findInstancesHavingSynonymInTree(ws, instance);

            log.debug("findInstancesHavingSynonymInTree: ${l}");

            if (!l.isEmpty()) {
                Message mm = Message.makeMsg(Msg.IS_SYNONYM_OF_ALREADY_IN_TREE)
                error.nested.add(mm);
                for (Instance i : l) {
                    if (!currentLink || i.citedBy.name != name)
                        mm.nested.add(Message.makeMsg(Msg.IS_SYNONYM_OF_ALREADY_IN_TREE_item, [i.citedBy, i.instanceType.hasLabel]))
                }
            }

            // CHECK FOR NAME COMPATIBILITY

            if (parentName) {
                if (parentName.nameRank.sortOrder >= name.nameRank.sortOrder) {
                    error.nested.add(Message.makeMsg(Msg.CANNOT_PLACE_NAME_UNDER_HIGHER_RANK, [name.nameRank.abbrev, parentName.nameRank.abbrev]))
                }

                if (parentName && "ApcConcept".equals(placementType.idPart)) {
                    check_name_compatibility(error.nested, parentName, name);
                }
            }

            Link newParentLink = null
            if (parentName != null) {
                newParentLink = queryService.findCurrentNslNameInTreeOrBaseTree(ws, parentName)
                if (newParentLink == null) {
                    error.nested.add(Message.makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [ws, parentName, 'Name']))
                }
            } else {
                newParentLink = DomainUtils.getSingleSublink(ws.node);
                if (newParentLink.typeUriIdPart != 'workspace_top_node') throw new IllegalStateException(newParentLink.typeUriIdPart);
            }

            if (!error.nested.isEmpty()) ServiceException.raise(error);

            // oh well. Let's write this dog. At ewqch step we may nned to re-search/re-fetch stuff

            // First, if the node needs to be updated, then check it out and update it.

            if (currentLink != null) {
                log.debug("the name is currently in the tree at ${ll(currentLink)}")
                Node currentNode = currentLink.subnode;
                if (currentNode.name != name) throw new IllegalStateException();

                if (currentNode.instance != instance || DomainUtils.getNodeTypeUri(currentNode) != placementType) {
                    log.debug("the node needs to be edited")
                    // needs to be possibly checked out and then saved.

                    if (DomainUtils.isCheckedIn(currentNode)) {
                        log.debug("checking out ${nn(currentNode)}")
                        currentNode = basicOperationsService.checkoutNode(ws.node, currentNode);
                        log.debug("checked out node is now ${nn(currentNode)}")
                        currentLink = DomainUtils.getDraftNodeSuperlink(currentNode);
                        log.debug("currentLink ${ll(currentLink)}")
                    }

                    basicOperationsService.updateDraftNode(currentNode, nslInstance: instance, nodeType: placementType);
                } else {
                    log.debug("the node does not need to be edited")
                }

                // at this point, the tree may have been disturbed and we need to re-fetch things - copy/paste the code. Note that at this stage
                // the current node may or may not be a draft node

                if (parentName != null) {
                    newParentLink = queryService.findCurrentNslNameInTreeOrBaseTree(ws, parentName)
                    if (newParentLink == null) {
                        error.nested.add(Message.makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [ws, parentName, 'Name']))
                    }
                } else {
                    newParentLink = DomainUtils.getSingleSublink(ws.node);
                    if (newParentLink.typeUriIdPart != 'workspace_top_node') throw new IllegalStateException(newParentLink.typeUriIdPart);
                }


                log.debug("current link is now ${ll(currentLink)}")
                log.debug("link to the new parent is now ${ll(newParentLink)}")

                // next - the placement. If there is going to be a move, then the node's current parent must be checked out
                // and the destination parent must be checked out.

                // do we need to move at all?

                if (currentLink == null || currentLink.supernode != newParentLink.subnode) {
                    log.debug("the node needs to be moved")

                    // both the current and new parent need to be checked out, which they either or both may already be. If
                    // both of them need checking out, AND one of them is below the other, THEN the sequence becomes very critical.
                    // we check out the 'higher' one first and then the lower one, because checking out the lower one will
                    // also check out the higher one which will cause our reference to that higher one to get lost

                    if (currentLink != null && DomainUtils.isCheckedIn(currentLink.supernode) && DomainUtils.isCheckedIn(newParentLink.subnode)
                            && queryService.countPaths(newParentLink.subnode, currentLink.supernode) != 0) {
                        log.debug("both the current parent and the new parent may need to be checked out, in reverse order")
                        // the new parent is above the old parent, so we must check out the new parent first

                        if (DomainUtils.isCheckedIn(newParentLink.subnode)) {
                            log.debug("checking out new parent")
                            Node newNode = basicOperationsService.checkoutNode(ws.node, newParentLink.subnode);
                            newParentLink = DomainUtils.getDraftNodeSuperlink(newNode);
                        } else {
                            log.debug("new parent is already checked out")
                        }

                        if (currentLink != null && DomainUtils.isCheckedIn(currentLink.supernode)) {
                            log.debug("checking out old parent")
                            Node newNode = basicOperationsService.checkoutNode(ws.node, currentLink.supernode);
                            currentLink = Link.findBySupernodeAndLinkSeq(newNode, currentLink.linkSeq);
                        } else {
                            log.debug("no old parent, or old parent is already checked out")
                        }

                    } else {
                        if (currentLink != null && DomainUtils.isCheckedIn(currentLink.supernode)) {
                            Node newNode = basicOperationsService.checkoutNode(ws.node, currentLink.supernode);
                            currentLink = Link.findBySupernodeAndLinkSeq(newNode, currentLink.linkSeq);
                            log.debug("checking out old parent")
                        } else {
                            log.debug("no old parent, or old parent is already checked out")
                        }

                        if (DomainUtils.isCheckedIn(newParentLink.subnode)) {
                            log.debug("checking out new parent")
                            Node newNode = basicOperationsService.checkoutNode(ws.node, newParentLink.subnode);
                            newParentLink = DomainUtils.getDraftNodeSuperlink(newNode);
                        } else {
                            log.debug("new parent is already checked out")
                        }
                    }

                    // once the node's current parent and destination parent are both checked out, then the node is moved either as
                    // a draft node move or as an un-adopt/adopt sequence. Oh - or we have to create it, duh.

                    currentLink = DomainUtils.refetchLink(currentLink)
                    newParentLink = DomainUtils.refetchLink(newParentLink)

                    log.debug("currentLink ${ll(currentLink)}")
                    log.debug("newParentLink ${ll(newParentLink)}")

                    if (currentLink == null) {
                        log.debug("name is not in the tree. creating a new draft node")
                        basicOperationsService.createDraftNode(newParentLink.supernode, VersioningMethod.V, NodeInternalType.T,
                                nslName: name, nslInstance: instance, nodeType: placementType)
                    } else if (DomainUtils.isCheckedIn(currentLink.subnode)) {
                        log.debug("name is not checked out in the tree. Removing from old parent and adopting into the new one")
                        basicOperationsService.deleteLink(currentLink.supernode, currentLink.linkSeq);
                        basicOperationsService.adoptNode(newParentLink.subnode, currentLink.subnode);
                    } else {
                        log.debug("name checked out in the tree. Moving the draft node.")
                        basicOperationsService.simpleMoveDraftLink(currentLink, newParentLink.subnode);
                    }
                }
            } else {
                log.debug("node does not need to be moved")
            }

            return null;

        }
        catch (Throwable t) {
            for (; t != null; t = t.cause) {
                log.error(t.toString());
                for (int i = 0; i < t.getStackTrace().length; i++) {
                    if (t.getStackTrace()[i].className.startsWith("au.org.bio") && t.getStackTrace()[i].lineNumber >= 0) {
                        log.error("  " + t.getStackTrace()[i].toString());
                    }
                }

            }

            throw t;
        }
    }

    private void check_name_compatibility(List errors, Name supername, Name subname) {
        Name a = supername;
        Name b = subname;

        for (; ;) {
            // genus has a sort order of 120
            // TODO: move this important magic number into Name Rank,
            // TODO: perhaps provide "is uninomial" functionality
            if (!a || !b || a.nameRank.sortOrder < 120 || b.nameRank.sortOrder < 120) return;
            if (a.nameRank.sortOrder == b.nameRank.sortOrder) {
                if (a != b) {
                    errors.add(Message.makeMsg(Msg.NAME_CANNOT_BE_PLACED_UNDER_NAME, [supername, subname]))
                }
                return;
            }
            if (a.nameRank.sortOrder > b.nameRank.sortOrder) a = a.parent;
            else b = b.parent;
        }

    }

    Message removeNameFromTree(Arrangement ws, Name name) {
        if (!ws) throw new IllegalArgumentException("null tree");
        if (!name) throw new IllegalArgumentException("null name");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        Message error = Message.makeMsg(Msg.removeNameFromTree, [name, ws]);

        try {
            Link currentLink = queryService.findCurrentNslNameInTreeOrBaseTree(ws, name)
            if (!currentLink) {
                error.nested.add(Message.makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [ws, name, "Name"]));
                ServiceException.raise(error);
            }

            Node currentNode = currentLink.supernode

            if (DomainUtils.isCheckedIn(currentLink.supernode)) {
                currentNode = basicOperationsService.checkoutNode(ws.node, currentNode);
                currentLink = DomainUtils.refetchLink(currentLink);
            }

            // it's a little tricky, but this does cover all possibilities.
            // note that a non-draft node never has a draft node as a subnode

            if (DomainUtils.isCheckedIn(currentLink.subnode)) {
                basicOperationsService.deleteLink(currentNode, currentLink.linkSeq);
            } else {
                basicOperationsService.deleteDraftNode(currentLink.subnode);
            }
        }
        catch (ServiceException ex) {
            ex.printStackTrace();
            if (ex.msg == error)
                throw ex;
            else {
                error.nested.add(ex.msg);
                ServiceException.raise(error);
            }
        }
        return null;
    }

    Message updateValue(Arrangement ws, Name name, ValueNodeUri valueUri, String value) {
        if (!ws) throw new IllegalArgumentException("null tree");
        if (!name) throw new IllegalArgumentException("null name");
        if (!valueUri) throw new IllegalArgumentException("null value uri");

        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");
        if (valueUri.isMultiValued) throw new IllegalArgumentException("${valueUri} is multivalued");

        Message error = Message.makeMsg(Msg.updateValue, [valueUri.title, name, ws]);

        try {
            Link currentNameLink = queryService.findCurrentNslNameInTreeOrBaseTree(ws, name)
            if (!currentNameLink) {
                error.nested.add(Message.makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [ws, name, "Name"]));
                ServiceException.raise(error);
            }

            Node currentNameNode = currentNameLink.subnode

            // find existing value node

            Link currentValueLink = Link.where {
                supernode == currentNameNode &&
                        typeUriNsPart == valueUri.linkUriNsPart &&
                        typeUriIdPart == valueUri.linkUriIdPart &&
                        subnode.internalType == NodeInternalType.V
            }.first();

            if (!currentValueLink && !value) return;
            if (currentValueLink
                    && currentValueLink.subnode.typeUriNsPart == valueUri.nodeUriNsPart
                    && currentValueLink.subnode.typeUriIdPart == valueUri.nodeUriIdPart
                    && currentValueLink.subnode.literal == value) {
                return;
            }

            if (DomainUtils.isCheckedIn(currentNameNode)) {
                log.debug("checking out");
                currentNameNode = basicOperationsService.checkoutNode(ws.node, currentNameNode);
                currentNameNode = DomainUtils.refetchNode(currentNameNode);
                if(currentValueLink != null) {
                    currentValueLink = Link.findBySupernodeAndLinkSeq(currentNameNode, currentValueLink.linkSeq);
                }
            }

            // ok! now use the basic opearions service to update/add values on the node

            if(currentValueLink && !DomainUtils.isCheckedIn(currentValueLink.subnode)) {
                // update the existing draft subnode
                if(value) {
                    basicOperationsService.updateDraftNode(currentValueLink.subnode,
                            nodeType: DomainUtils.getNodeTypeUri(valueUri),
                            literal: value
                    );
                    currentNameNode = DomainUtils.refetchNode(currentNameNode);
                    basicOperationsService.updateDraftNodeLink(currentNameNode, currentValueLink.linkSeq, linkType: DomainUtils.getLinkTypeUri(valueUri));
                }
                else {
                    basicOperationsService.deleteDraftNode(currentValueLink.subnode);
                }
            }
            else {
                // unlink existing persistent subnode (if necessary),
                // crate new draft subnode (if necesary)
                if (currentValueLink) {
                    log.debug("deleting ");
                    basicOperationsService.deleteLink(currentNameNode, currentValueLink.linkSeq);
                    currentNameNode = DomainUtils.refetchNode(currentNameNode);
                    currentValueLink = null;
                }


                if (value) {
                    log.debug("creating new value ");
                    basicOperationsService.createDraftNode(currentNameNode, VersioningMethod.F, NodeInternalType.V,
                            nodeType: DomainUtils.getNodeTypeUri(valueUri),
                            linkType: DomainUtils.getLinkTypeUri(valueUri),
                            literal: value
                    )
                }
            }

        }
        catch (ServiceException ex) {
            ex.printStackTrace();
            if (ex.msg == error)
                throw ex;
            else {
                error.nested.add(ex.msg);
                ServiceException.raise(error);
            }
        }
        return null;
    }

    Message addMultiValue(Arrangement ws, Name name, ValueNodeUri valueUri, String value) {
        if (!ws) throw new IllegalArgumentException("null tree");
        if (!name) throw new IllegalArgumentException("null name");
        if (!valueUri) throw new IllegalArgumentException("null value uri");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        if (!valueUri.isMultiValued) throw new IllegalArgumentException("${valueUri} is not multivalued");

        ServiceException.raise(Message.makeMsg(Msg.TODO, ['Implement addMultiValue']))
    }

    Message removeMultiValue(Arrangement ws, Name name, int linkSeq) {
        if (!ws) throw new IllegalArgumentException("null tree");
        if (!name) throw new IllegalArgumentException("null name");
        if (ws.arrangementType != ArrangementType.U) throw new IllegalArgumentException("ws is not a workspace");

        ServiceException.raise(Message.makeMsg(Msg.TODO, ['Implement removeMultiValue']))
    }
}
