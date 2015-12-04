/*
    Copyright 2015 Australian National Botanic Gardens

    This file is part of NSL tree services plugin project.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy
    of the License at http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.*
import grails.transaction.Transactional
import org.hibernate.SessionFactory

import static ServiceException.makeMsg
import static ServiceException.raise

/*
 * TODO: Some of these operations are secured, some aren't. This service should probably be split into two.
 */

/**
 * Tree Operations Service works on a classification Arrangement consisting of an Arrangement with a top node connected
 * via a TrackingLink to a root node under which all the tree nodes reside.
 *
 * This is a Name tree with associated taxon data. Operations that change the tree work on name URIs.
 *
 * @see VersioningMethod
 */
@Transactional(rollbackFor = [ServiceException])
public class TreeOperationsService {
    static datasource = 'nsl'

    SessionFactory sessionFactory_nsl

    def grailsApplication
    BasicOperationsService basicOperationsService
    VersioningService versioningService
    QueryService queryService

    UriNs nslNameNs() {
        return UriNs.findByLabel(grailsApplication.config.nslTreePlugin.nslNameNamespace as String)
    }

    UriNs nslInstanceNs() {
        return UriNs.findByLabel(grailsApplication.config.nslTreePlugin.nslInstanceNamespace as String)
    }

    /**
     * Find a List of Current Nodes that match the nameUri in the classification. In a correctly configured tree there
     * should only ever be one Current Node that matches the nameUri
     *
     * @param classification
     * @param nameUri
     * @return Collection of Node
     * @deprecated moved to QueryService
     */
    @SuppressWarnings("GroovyUnusedDeclaration")
    Collection<Node> findCurrentName(Arrangement classification, Uri nameUri) {
        mustHave(Arrangement: (classification), Uri: (nameUri && nameUri.idPart)) {
            queryService.findCurrentName(classification, nameUri)
        } as Collection<Node>
    }

    /**
     * Find a List of Current Nodes that match the taxonUri in the classification. In a correctly configured tree there
     * should only ever be one Current Node that matches the taxonUri
     *
     * @param classification
     * @param taxonUri
     * @return Collection of Node
     * @deprecated moved to QueryService
     */
    @SuppressWarnings("GroovyUnusedDeclaration")
    Collection<Node> findCurrentTaxon(Arrangement classification, Uri taxonUri) {
        mustHave(Arrangement: (classification), Uri: (taxonUri && taxonUri.idPart)) {
            queryService.findCurrentTaxon(classification, taxonUri)
        } as Collection<Node>
    }

    private Node findSingleName(Arrangement a, Uri uri, String msg) {
        if (!uri) return null
        Collection<Node> f = queryService.findCurrentName(a, uri)
        if (f.isEmpty()) raise makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [a, uri, msg])
        if (f.size() > 1) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT_MULTIPLE_TIMES, [a, uri, msg])
        return f.first()
    }

    private Node findSingleNslName(Arrangement a, Name name, String msg) {
        if (!name) return null
        Collection<Node> f = queryService.findCurrentNslName(a, name)
        if (f.isEmpty()) raise makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [a, name, msg])
        if (f.size() > 1) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT_MULTIPLE_TIMES, [a, name, msg])
        return f.first()
    }

    private Node findSingleNslInstance(Arrangement a, Instance instance, String msg) {
        if (!instance) return null
        Collection<Node> f = queryService.findCurrentNslInstance(a, instance)
        if (f.isEmpty()) raise makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [a, instance, msg])
        if (f.size() > 1) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT_MULTIPLE_TIMES, [a, instance, msg])
        return f.first()
    }

    private void checkCurrentNameNotInTree(Arrangement a, Uri uri, String msg) {
        if (!uri) return
        Collection<Node> f = queryService.findCurrentName(a, uri)
        if (!f.isEmpty()) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT, [a, uri, msg])
    }

    private void checkCurrentNslNameNotInTree(Arrangement a, Name name, String msg) {
        if (!name) return
        Collection<Node> f = queryService.findCurrentNslName(a, name)
        if (!f.isEmpty()) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT, [a, name, msg])
    }

    private void checkCurrentTaxonNotInTree(Arrangement a, Uri uri, String msg) {
        if (!uri) return
        Collection<Node> f = queryService.findCurrentTaxon(a, uri)
        if (!f.isEmpty()) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT, [a, uri, msg])
    }

    private void checkCurrentNslInstanceNotInTree(Arrangement a, Instance instance, String msg) {
        if (!instance) return
        Collection<Node> f = queryService.findCurrentNslInstance(a, instance)
        if (!f.isEmpty()) raise makeMsg(Msg.THING_FOUND_IN_ARRANGEMENT, [a, instance, msg])
    }

    private static void checkArrangementIsTree(Arrangement a) {
        // an arrangement
        if (!a) throw new IllegalArgumentException("arrangement is null")
        if (!a.node) throw new IllegalArgumentException("arrangement ${a} does not have a top node")
        if (a.node.root != a) throw new IllegalArgumentException("arrangement ${a} top node ${a.node} belongs to arrangement ${a.node.root}")

        if (DomainUtils.getNodeTypeUri(a.node) != DomainUtils.getBoatreeUri('classification-node'))
            throw new IllegalArgumentException("arrangement ${a} top node ${a.node} type is ${DomainUtils.getNodeTypeUri(a.node)}")

        if (a.node.supLink.size() != 0) throw new IllegalArgumentException("arrangement ${a} top node ${a.node} has ${a.node.supLink.size()} supernodes")
        if (a.node.subLink.size() != 1) throw new IllegalArgumentException("arrangement ${a} top node ${a.node} has ${a.node.subLink.size()} subnodes")

        Link l = a.node.subLink.first()

        if (l.versioningMethod != VersioningMethod.T) throw new IllegalArgumentException("arrangement ${a} top node ${a.node} link ${l} has versioning method ${l.versioningMethod}")
        if (DomainUtils.getLinkTypeUri(l) != DomainUtils.getBoatreeUri('classification-root-link'))
            throw new IllegalArgumentException("arrangement ${a} top node ${a.node} link ${l} type is ${DomainUtils.getLinkTypeUri(l)}")

        if (DomainUtils.getNodeTypeUri(l.subnode) != DomainUtils.getBoatreeUri('classification-root'))
            throw new IllegalArgumentException("arrangement ${a} top node ${a.node} link ${l} subnode type is ${DomainUtils.getNodeTypeUri(l.subnode)}")
    }

    /**
     * Add a new name + taxon pair node to the classification under another name called the supername.
     *
     * Optional parameters
     * nodeTypeUri - if left out will default to a 'placement' type
     * linkTypeUri - if left out will default to a 'subnode' type
     * linkSeq - if not set will be set to the next sequence for the set of subnodes to the supername node. If set it
     * will re-sequence the other subnodes.
     *
     * @param params - optional parameters nodeTypeUri, linkTypeUri, linkSeq
     * @param classification - the tree to add the node too
     * @param nameUri - the nameUri of the new Node to add. This name must not be in the tree already
     * @param supernameUri - the optional URI of the name under which this name should go
     * @param taxonUri - the optional taxonUri to add to the new node
     * @return the checked in added Node
     */
    Node addName(
            Map params = [:],
            Arrangement classification,
            Uri nameUri,
            Uri supernameUri,
            Uri taxonUri,
            Map<Uri, Map> profileItems = [:]) {
        addName(params, classification, nameUri, supernameUri, taxonUri, null, profileItems)
    }

    Node addName(
            Map params = [:],
            Arrangement classification,
            Uri nameUri,
            Uri supernameUri,
            Uri taxonUri,
            String authUser,
            Map<Uri, Map> profileItems = [:]) {

        log.debug "addName(${classification}, ${nameUri}, ${supernameUri}, ${taxonUri}, ${profileItems}, ${params})"

        mustHave(Arrangement: classification, Name: nameUri) {
            clearAndFlush {
                classification = DomainUtils.refetchArrangement(classification)

                nameUri = DomainUtils.refetchUri(nameUri)
                supernameUri = DomainUtils.refetchUri(supernameUri)
                taxonUri = DomainUtils.refetchUri(taxonUri)
                profileItems = DomainUtils.refetchMap(profileItems)
                params = DomainUtils.refetchMap(params)

                Name name = null

                if (nameUri.nsPart == nslNameNs()) {
                    name = Name.get(nameUri.idPart as int)
                    if (name == null) {
                        throw new IllegalArgumentException("Name ${nameUri} not found")
                    }
                }

                Instance instance = null

                if (taxonUri && taxonUri.nsPart == nslInstanceNs()) {
                    instance = Instance.get(taxonUri.idPart as int)
                    if (name == null) {
                        throw new IllegalArgumentException("Instance ${taxonUri} not found")
                    }
                }

                log.debug "checks"

                checkArrangementIsTree(classification)
                checkCurrentNameNotInTree(classification, nameUri, 'name')
                checkCurrentTaxonNotInTree(classification, taxonUri, 'taxon')

                Node superNode

                log.debug "supername"
                if (supernameUri && supernameUri.idPart) {
                    superNode = findSingleName(classification, supernameUri, 'supername')
                } else {
                    superNode = DomainUtils.getSingleSubnode(classification.node)
                    if (!superNode) throw new IllegalArgumentException("Classification ${classification} is not a properly-formed tree")
                }

                return addNameToClassification(superNode, nameUri, taxonUri, name, instance, params, profileItems,
                        supernameUri.toString(), classification, authUser)

            } as Node
        } as Node
    }

    /**
     * Add a new name + taxon pair node to the classification under another name called the supername.
     *
     * Optional parameters
     * nodeTypeUri - if left out will default to a 'placement' type
     * linkTypeUri - if left out will default to a 'subnode' type
     * linkSeq - if not set will be set to the next sequence for the set of subnodes to the supername node. If set it
     * will re-sequence the other subnodes.
     *
     * @param params - optional parameters nodeTypeUri, linkTypeUri, linkSeq
     * @param classification - the tree to add the node too
     * @param nameUri - the nameUri of the new Node to add. This name must not be in the tree already
     * @param supernameUri - the optional URI of the name under which this name should go
     * @param taxonUri - the optional taxonUri to add to the new node
     * @return the checked in added Node
     */
    @SuppressWarnings("GroovyUnusedDeclaration")
    Node addNslName(Map params = [:],
                    Arrangement classification,
                    Name name,
                    Name supername,
                    Instance instance,
                    Map<Uri, Map> profileItems = [:]) {
        addNslName(params, classification, name, supername, instance, null, profileItems)
    }

    Node addNslName(Map params = [:],
                    Arrangement classification,
                    Name name,
                    Name supername,
                    Instance instance,
                    String authUser,
                    Map<Uri, Map> profileItems = [:]) {

        log.debug "addName(${classification}, ${name}, ${supername}, ${instance}, ${profileItems}, ${params})"

        mustHave(Arrangement: classification, Name: name) {
            clearAndFlush {
                classification = DomainUtils.refetchArrangement(classification)
                name = DomainUtils.refetchName(name);
                supername = DomainUtils.refetchName(supername);
                instance = DomainUtils.refetchInstance(instance);

                Uri nameUri = new Uri(nslNameNs(), name.id);
                Uri taxonUri = instance ? new Uri(nslInstanceNs(), instance.id) : null;
                profileItems = DomainUtils.refetchMap(profileItems)
                params = DomainUtils.refetchMap(params)

                log.debug "checks"
                checkArrangementIsTree(classification)
                checkCurrentNslNameNotInTree(classification, name, 'name')
                checkCurrentNslInstanceNotInTree(classification, instance, 'instance')

                Node superNode

                log.debug "supername"
                if (supername) {
                    superNode = findSingleNslName(classification, supername, 'supername')
                } else {
                    superNode = DomainUtils.getSingleSubnode(classification.node)
                    if (!superNode) throw new IllegalArgumentException("Classification ${classification} is not a properly-formed tree")
                }
                return addNameToClassification(superNode, nameUri, taxonUri, name, instance, params, profileItems,
                        supername.id.toString(), classification, authUser)
            } as Node
        } as Node
    }

    private Node addNameToClassification(Node superNode,
                                         Uri nameUri,
                                         Uri taxonUri,
                                         Name name,
                                         Instance instance,
                                         Map params,
                                         Map<Uri, Map> profileItems,
                                         String supername,
                                         Arrangement classification,
                                         String authUser) {
        log.debug "temp arrangement"
        Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(classification.namespace)

        log.debug "adopt"
        Link link = basicOperationsService.adoptNode(tempSpace.node, superNode, VersioningMethod.F)

        log.debug "checkout"
        basicOperationsService.checkoutLink(link)
        link = DomainUtils.refetchLink(link)

        log.debug "create draft"
        Node newNode = basicOperationsService.createDraftNode(link.subnode,
                VersioningMethod.V,
                NodeInternalType.T,
                name: nameUri,
                taxon: taxonUri,
                nslName: name,
                nslInstance: instance,
                nodeType: params.nodeTypeUri,
                linkType: params.linkTypeUri,
                seq: params.linkSeq)

        log.debug "create profile items: ${profileItems}"

        profileItems.each { Uri k, Map v ->
            if (!v) {
                v = null;
            } else if (!v['resource'] && !v['literal']) {
                v = null;
            }

            if (v) {
                log.debug "create profile item: ${k} = ${v}"

                basicOperationsService.createDraftNode(
                        newNode,
                        VersioningMethod.F,
                        NodeInternalType.V,
                        nodeType: v['type'] ?: DomainUtils.getDefaultNodeTypeUriFor(NodeInternalType.V),
                        linkType: k,
                        resource: v['resource'],
                        literal: v['literal']
                )
            }
        }

        log.debug "persist"
        Event addNodeEvent = basicOperationsService.newEvent(superNode.root.namespace, "add name ${name?.id ?: nameUri} to name ${supername}", authUser)
        basicOperationsService.persistNode(addNodeEvent, link.subnode)

        Map<Node, Node> replacementMap = new HashMap<Node, Node>()
        replacementMap.put(superNode, link.subnode)

        log.debug "version"
        versioningService.performVersioning(addNodeEvent, replacementMap, classification)

        log.debug "cleanup"
        basicOperationsService.moveFinalNodesFromTreeToTree(tempSpace, classification)
        basicOperationsService.deleteArrangement(tempSpace)

        newNode.refresh()
        log.debug "done"
        return newNode
    }

    /**
     * This method lets you update and exisitng Node. You can do any or all of:
     * <ol>
     *   <li> move the node in the classification to reside under another supername</li>
     *   <li> change the node type or change the taxon</li>
     *   <li> change the link type to the supernode or change the link sequence number</li>
     * </ol>
     *
     * NOTE: This method probably does too much and we should separate the above operations. They will still be higher
     * level than the basicOperations.
     *
     * @param params
     * @param classification
     * @param nameUri
     * @param supernameUri
     * @param taxonUri
     * @param profileItems key: property (link type), value: [type (optional uri), resource (optional uri), literal (primitive literal)]
     * @return the new name node, or the old one if no changes were needed
     */
    Node updateName(Map params = [:],
                    Arrangement classification,
                    Uri nameUri,
                    Uri supernameUri,
                    Uri taxonUri,
                    Map<Uri, Map> profileItems = [:]) {
        updateName(params, classification, nameUri, supernameUri, taxonUri, null, profileItems)
    }

    Node updateName(Map params = [:],
                    Arrangement classification,
                    Uri nameUri,
                    Uri supernameUri,
                    Uri taxonUri,
                    String authUser,
                    Map<Uri, Map> profileItems = [:]) {

        log.debug "updateName(${classification}, ${nameUri}, ${supernameUri}, ${taxonUri}, ${profileItems}, ${params})"

        mustHave(Arrangement: classification, Name: nameUri) {
            clearAndFlush {
                classification = DomainUtils.refetchArrangement(classification)
                nameUri = DomainUtils.refetchUri(nameUri)
                supernameUri = DomainUtils.refetchUri(supernameUri)
                taxonUri = DomainUtils.refetchUri(taxonUri)
                profileItems = DomainUtils.refetchMap(profileItems)
                params = DomainUtils.refetchMap(params)

                Instance instance = null

                if (taxonUri && taxonUri.nsPart == nslInstanceNs()) {
                    instance = Instance.get(taxonUri.idPart as int)
                    if (instance == null) {
                        throw new IllegalArgumentException("Instance ${instance} not found")
                    }
                }

                log.debug "checks"
                checkArrangementIsTree(classification)
                Node existingNode = findSingleName(classification, nameUri, 'name')
                Link existingNodeSingleSuperlink = DomainUtils.getSingleSuperlink(existingNode)

                Uri nodeTypeUri = params.nodeTypeUri as Uri ?: DomainUtils.getRawNodeTypeUri(existingNode)
                Uri linkTypeUri = params.linkTypeUri as Uri ?: DomainUtils.getRawLinkTypeUri(existingNodeSingleSuperlink)
                Integer linkSeq = params.linkSeq as Integer
                // we explicitly handle this being null, because this being null means something specific.

                Node existingMoveTo
                Node existingMoveFrom = DomainUtils.getSingleSupernode(existingNode)

                log.debug "supername"
                if (supernameUri && supernameUri.idPart) {
                    existingMoveTo = findSingleName(classification, supernameUri, 'supername')
                } else {
                    existingMoveTo = DomainUtils.getSingleSubnode(classification.node)
                }

                if (DomainUtils.getTaxonUri(existingNode) != taxonUri) {
                    checkCurrentTaxonNotInTree(classification, taxonUri, 'taxon')
                }


                return updateNameInClassification(existingNode, existingNodeSingleSuperlink, existingMoveFrom, existingMoveTo,
                        taxonUri, nodeTypeUri, linkTypeUri, linkSeq, profileItems, instance,
                        classification, authUser)
            } as Node
        } as Node
    }

    /**
     * This method lets you update and exisitng Node. You can do any or all of:
     * <ol>
     *   <li> move the node in the classification to reside under another supername</li>
     *   <li> change the node type or change the taxon</li>
     *   <li> change the link type to the supernode or change the link sequence number</li>
     * </ol>
     *
     * NOTE: This method probably does too much and we should separate the above operations. They will still be higher
     * level than the basicOperations.
     *
     * @param params
     * @param classification
     * @param nameUri
     * @param supernameUri
     * @param taxonUri
     * @param profileItems key: property (link type), value: [type (optional uri), resource (optional uri), literal (primitive literal)]
     * @return the new name node, or the old one if no changes were needed
     */
    @SuppressWarnings("GroovyUnusedDeclaration")
    Node updateNslName(Map params = [:],
                       Arrangement classification,
                       Name name,
                       Name supername,
                       Instance instance,
                       Map<Uri, Map> profileItems = [:]) {
        updateNslName(params, classification, name, supername, instance, null, profileItems)
    }

    Node updateNslName(Map params = [:],
                       Arrangement classification,
                       Name name,
                       Name supername,
                       Instance instance,
                       String authUser,
                       Map<Uri, Map> profileItems = [:]) {

        log.debug "updateNslName(${classification}, ${name}, ${supername}, ${instance}, ${profileItems}, ${params})"

        mustHave(Arrangement: classification, Name: name) {
            clearAndFlush {
                classification = DomainUtils.refetchArrangement(classification)
                name = DomainUtils.refetchName(name)
                supername = DomainUtils.refetchName(supername)
                instance = DomainUtils.refetchInstance(instance)

                Uri taxonUri = instance ? new Uri(nslInstanceNs(), instance.id) : null
                profileItems = DomainUtils.refetchMap(profileItems)
                params = DomainUtils.refetchMap(params)

                log.debug "checks"
                checkArrangementIsTree(classification)
                Node existingNode = findSingleNslName(classification, name, 'name')
                Link existingNodeSingleSuperlink = DomainUtils.getSingleSuperlink(existingNode)

                Uri nodeTypeUri = params.nodeTypeUri as Uri ?: DomainUtils.getRawNodeTypeUri(existingNode)
                Uri linkTypeUri = params.linkTypeUri as Uri ?: DomainUtils.getRawLinkTypeUri(existingNodeSingleSuperlink)
                Integer linkSeq = params.linkSeq as Integer
                // we explicitly handle this being null, because this being null means something specific.

                Node existingMoveTo
                Node existingMoveFrom = DomainUtils.getSingleSupernode(existingNode)

                log.debug "supername"
                if (supername) {
                    existingMoveTo = findSingleNslName(classification, supername, 'supername')
                } else {
                    existingMoveTo = DomainUtils.getSingleSubnode(classification.node)
                }

                if (existingNode.instance != instance) {
                    checkCurrentNslInstanceNotInTree(classification, instance, 'instance')
                }

                return updateNameInClassification(existingNode, existingNodeSingleSuperlink, existingMoveFrom, existingMoveTo,
                        taxonUri, nodeTypeUri, linkTypeUri, linkSeq, profileItems, instance,
                        classification, authUser)
            } as Node
        } as Node
    }

    private Node updateNameInClassification(Node existingNode,
                                            Link existingNodeSingleSuperlink,
                                            Node existingMoveFrom,
                                            Node existingMoveTo,
                                            Uri taxonUri,
                                            Uri nodeTypeUri,
                                            Uri linkTypeUri,
                                            Integer linkSeq,
                                            Map<Uri, Map> profileItems,
                                            Instance instance,
                                            Arrangement classification,
                                            String authUser) {
        log.debug "tempspace"
        Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(classification.namespace)

        existingNode = DomainUtils.refetchNode(existingNode)
        existingNodeSingleSuperlink = DomainUtils.refetchLink(existingNodeSingleSuperlink)
        existingMoveFrom = DomainUtils.refetchNode(existingMoveFrom)
        existingMoveTo = DomainUtils.refetchNode(existingMoveTo)

        // ok. If the taxon is being changed, then we need to checkout the node.
        // if the parent is being changed, we need to checkout the old parent and the new parent

        boolean changeNode = (DomainUtils.getTaxonUri(existingNode) != taxonUri) ||
                (DomainUtils.getRawNodeTypeUri(existingNode) != nodeTypeUri)

        boolean moveNode = DomainUtils.getSingleSupernode(existingNode) != existingMoveTo

        boolean changeLink = (DomainUtils.getRawLinkTypeUri(existingNodeSingleSuperlink) != linkTypeUri) ||
                (linkSeq && linkSeq != existingNodeSingleSuperlink.linkSeq)

        boolean changeProfileItems = false

        profileItems.each { Uri k, Map v ->
            Link l = existingNode.subLink.find { l -> DomainUtils.getLinkTypeUri(l) == k }
            if (!l) {
                // what happens if you 'break' inside a groovy closure? I don't know. I will just do this.
                if (v) {
                    changeProfileItems = true
                }
            } else {
                if (!v) {
                    changeProfileItems = true
                } else {
                    Node oldVal = l ? l.subnode : null
                    if (v['type'] && v['type'] != DomainUtils.getNodeTypeUri(oldVal)) changeProfileItems = true
                    if (v['resource'] && v['resource'] != DomainUtils.getResourceUri(oldVal)) changeProfileItems = true
                    if (v['literal'] && v['literal'] != oldVal.literal) changeProfileItems = true
                }
            }
        }


        if (!changeNode && !moveNode && !changeLink && !changeProfileItems) {
            log.debug "noop - done"
            return existingNode
        }

        // checkedOutSupernode link is the link from the checked out supernode to the name in question. To put it another way,
        // it is the placement of the name in our temporary draft tree
        // note that the supernode will be checked out, the subnode is not checked out

        Link checkedOutSupernodeLink = null

        if (moveNode) {
            log.debug "checkout supernodes and move"
            checkedOutSupernodeLink = moveNodeCheckOut(existingMoveFrom, existingMoveTo, tempSpace, existingNode)
        } else if (changeLink) {
            log.debug "checkout supernode without moving"
            checkedOutSupernodeLink = changeLinkCheckOut(tempSpace, existingMoveFrom, existingNode)
        } else if (changeLink) {
            log.debug "update supernode link"
            basicOperationsService.updateDraftNodeLink(checkedOutSupernodeLink.supernode, checkedOutSupernodeLink.linkSeq, linkType: linkTypeUri, seq: linkSeq)
        }

        Node finalNode

        if (!changeNode && !changeProfileItems) {
            finalNode = existingNode;
        } else {
            if (!checkedOutSupernodeLink) {
                log.debug "adopt node - no supernode checkout"
                checkedOutSupernodeLink = basicOperationsService.adoptNode(tempSpace.node, existingNode, VersioningMethod.F)
            }

            log.debug "checkout node"
            basicOperationsService.checkoutLink(checkedOutSupernodeLink)
            checkedOutSupernodeLink = DomainUtils.refetchLink(checkedOutSupernodeLink)
            Node editNode = checkedOutSupernodeLink.subnode

            finalNode = editNode

            if (changeNode) {
                log.debug "update node"
                basicOperationsService.updateDraftNode(editNode, taxon: taxonUri, nslInstance: instance, nodeType: nodeTypeUri)
                editNode = DomainUtils.refetchNode(editNode)
            }

            if (changeProfileItems) {
                // note that this cache will contain unattached items, but that's ok.
                Map<Uri, Link> items = DomainUtils.getProfileItemsAsMap(editNode)

                log.debug "update profile items"
                profileItems.each { Uri k, Map v ->
                    boolean changeProfileItem

                    Link oldLink = DomainUtils.refetchLink(items.get(k))
                    Node oldVal = oldLink?.subnode

                    if (!v) {
                        v = null;
                    } else if (!v['resource'] && !v['literal']) {
                        v = null;
                    }

                    if (!v || !oldVal) {
                        changeProfileItem = v || oldVal
                    } else {
                        changeProfileItem = false
                        if (v['type'] && v['type'] != DomainUtils.getNodeTypeUri(oldVal)) changeProfileItem = true
                        if (v['resource'] && v['resource'] != DomainUtils.getResourceUri(oldVal)) changeProfileItem = true
                        if (v['literal'] && v['literal'] != oldVal.literal) changeProfileItem = true
                    }

                    if (changeProfileItem) {
                        if (oldVal) {
                            log.debug "delete old value"

                            basicOperationsService.deleteLink(editNode, oldLink.linkSeq)
                        }

                        if (v) {
                            log.debug "create new value ${k} = ${v}"

                            basicOperationsService.createDraftNode(
                                    editNode,
                                    VersioningMethod.F,
                                    NodeInternalType.V,
                                    nodeType: v['type'] ?: DomainUtils.getDefaultNodeTypeUriFor(NodeInternalType.V),
                                    linkType: k,
                                    resource: v['resource'],
                                    literal: v['literal']
                            )
                        }
                    }
                }
            }
        }

        log.debug "event"
        existingNode = DomainUtils.refetchNode(existingNode)
        Event event = basicOperationsService.newEvent(existingNode.root.namespace, "update name ${existingNode.nameId ?: DomainUtils.getNameUri(existingNode)}", authUser)

        log.debug "persist"
        basicOperationsService.persistNode(event, tempSpace.node)

        log.debug "version"
        Map<Node, Node> v = versioningService.getStandardVersioningMap(tempSpace, classification)

        versioningService.performVersioning(event, v, classification)
        log.debug "cleanup"
        basicOperationsService.moveFinalNodesFromTreeToTree(tempSpace, classification)
        basicOperationsService.deleteArrangement(tempSpace)
        log.debug "done"

        return DomainUtils.refetchNode(finalNode)
    }

    private Link changeLinkCheckOut(Arrangement tempSpace, Node existingMoveFrom, Node existingNode) {
        // we need to checkout the supernode, but there is no moving around involved
        Node moveFromCheckout = adoptAndCheckOut(tempSpace, existingMoveFrom)
        Link checkedOutSupernodeLink = DomainUtils.getSublinksAsArray(moveFromCheckout)[DomainUtils.getSingleSuperlink(existingNode).linkSeq]
        return checkedOutSupernodeLink
    }

    private Link moveNodeCheckOut(Node existingMoveFrom, Node existingMoveTo, Arrangement tempSpace, Node existingNode) {
        // ok. If the 'move node from' and the 'move node to' have a ancestor/descendant relationship, then
        // they need to be managed carefully. Otherwise, we just adopt each separately.

        Node moveFromCheckout
        Node moveToCheckout

        if (higherThan(existingMoveFrom, existingMoveTo)) {
            // the move from is higher up n the tree than the move to
            moveFromCheckout = adoptAndCheckOut(tempSpace, existingMoveFrom)
            moveToCheckout = basicOperationsService.checkoutNode(moveFromCheckout, existingMoveTo)

        } else {
            if (higherThan(existingMoveTo, existingMoveFrom)) {
                // the move to is higher up in the tree than the move from
                moveToCheckout = adoptAndCheckOut(tempSpace, existingMoveTo)
                moveFromCheckout = basicOperationsService.checkoutNode(moveToCheckout, existingMoveFrom)
            } else {
                // to and from are not in the same branch
                moveFromCheckout = adoptAndCheckOut(tempSpace, existingMoveFrom)
                moveToCheckout = adoptAndCheckOut(tempSpace, existingMoveTo)
            }
        }

        existingNode = DomainUtils.refetchNode(existingNode)
        moveFromCheckout = DomainUtils.refetchNode(moveFromCheckout)
        moveToCheckout = DomainUtils.refetchNode(moveToCheckout)

        Link checkedOutSupernodeLink = DomainUtils.getSublinksAsArray(moveFromCheckout)[DomainUtils.getSingleSuperlink(existingNode).linkSeq]

        basicOperationsService.updateDraftNodeLink(checkedOutSupernodeLink.supernode, checkedOutSupernodeLink.linkSeq, supernode: moveToCheckout)

        checkedOutSupernodeLink = DomainUtils.refetchLink(checkedOutSupernodeLink)
        return checkedOutSupernodeLink
    }

    private Node adoptAndCheckOut(Arrangement tempSpace, Node node) {
        Link link = basicOperationsService.adoptNode(tempSpace.node, node, VersioningMethod.F)
        basicOperationsService.checkoutLink(link)
        link = DomainUtils.refetchLink(link)
        return link.subnode
    }

    /**
     * Is Node A higher in the tree than Node B
     * @param a
     * @param b
     * @return true if a higher than b (a>b)
     */
    private static boolean higherThan(Node a, Node b) {
        Node pointer = b
        while (pointer && pointer != a) {
            pointer = DomainUtils.getSingleSupernode(pointer)
        }
        return pointer != null
    }

    void deleteName(Arrangement arrangement, Uri nameUri, Uri replacementNameUri) {
        deleteName(arrangement, nameUri, replacementNameUri, null)
    }

    void deleteName(Arrangement arrangement, Uri nameUri, Uri replacementNameUri, String authUser) {

        mustHave(arrangement: arrangement, name: nameUri) {
            clearAndFlush {
                arrangement = DomainUtils.refetchArrangement(arrangement)
                nameUri = DomainUtils.refetchUri(nameUri)
                replacementNameUri = DomainUtils.refetchUri(replacementNameUri)

                checkArrangementIsTree(arrangement)

                Node existingReplacement = findSingleName(arrangement, replacementNameUri, 'replacement name')
                if (replacementNameUri && !existingReplacement) {
                    // this never happens - findSingleName throws an exception if the name is not found
                    throw new IllegalArgumentException("replacement name ${replacementNameUri} not found")
                }

                Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(arrangement.namespace)

                Node existingNode = findSingleName(arrangement, nameUri, 'name')

                deleteNodeFromClassification(existingNode, tempSpace, existingReplacement, arrangement, authUser)
            }
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    void deleteNslName(Arrangement arrangement, Name name, Name replacementNslName) {
        deleteNslName(arrangement, name, replacementNslName, null)
    }

    void deleteNslName(Arrangement arrangement, Name name, Name replacementNslName, String authUser) {

        mustHave(arrangement: arrangement, name: name) {
            clearAndFlush {
                arrangement = DomainUtils.refetchArrangement(arrangement)
                name = DomainUtils.refetchName(name)
                replacementNslName = DomainUtils.refetchName(replacementNslName)

                checkArrangementIsTree(arrangement)

                Node existingReplacement = findSingleNslName(arrangement, replacementNslName, 'replacement name')
                if (replacementNslName && !existingReplacement) {
                    // this never happens - findSingleName throws an exception if the name is not found
                    throw new IllegalArgumentException("replacement name ${replacementNslName.id} not found")
                }

                Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(arrangement.namespace)

                Node existingNode = findSingleNslName(arrangement, name, 'name')

                deleteNodeFromClassification(existingNode, tempSpace, existingReplacement, arrangement, authUser)
            }
        }
    }

    private void deleteNodeFromClassification(Node existingNode,
                                              Arrangement tempSpace,
                                              Node existingReplacement,
                                              Arrangement arrangement,
                                              String authUser) {

        Node existingMoveFrom = DomainUtils.getSingleSupernode(existingNode)

        Node moveFromCheckout = adoptAndCheckOut(tempSpace, existingMoveFrom)
        basicOperationsService.deleteLink(moveFromCheckout, DomainUtils.getSingleSuperlink(existingNode).linkSeq)

        Map<Node, Node> v = new HashMap<Node, Node>()
        v.put(existingMoveFrom, moveFromCheckout)
        v.put(existingNode, existingReplacement ?: DomainUtils.getEndNode())

        Event e = basicOperationsService.newEvent(existingNode.root.namespace, "remove NSL node ${existingNode.id}", authUser)

        basicOperationsService.persistNode e, tempSpace.node
        versioningService.performVersioning e, v, arrangement
        basicOperationsService.moveFinalNodesFromTreeToTree tempSpace, arrangement
        basicOperationsService.deleteArrangement tempSpace
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    void deleteNslInstance(Arrangement arrangement, Instance instance, Instance replacementInstance) {
        deleteNslInstance(arrangement, instance, replacementInstance, null)
    }

    void deleteNslInstance(Arrangement arrangement, Instance instance, Instance replacementInstance, String authUser) {

        mustHave(arrangement: arrangement, name: instance) {
            clearAndFlush {
                arrangement = DomainUtils.refetchArrangement(arrangement)
                instance = DomainUtils.refetchInstance(instance)
                replacementInstance = DomainUtils.refetchInstance(replacementInstance)

                checkArrangementIsTree(arrangement)

                Node existingReplacement = findSingleNslInstance(arrangement, replacementInstance, 'replacement instance')
                if (replacementInstance && !existingReplacement) {
                    // this never happens - findSingleName throws an exception if the name is not found
                    throw new IllegalArgumentException("replacement instance ${replacementInstance.id} not found")
                }

                Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(arrangement.namespace)

                Node existingNode = findSingleNslInstance(arrangement, instance, 'instance')

                deleteNodeFromClassification(existingNode, tempSpace, existingReplacement, arrangement, authUser)
            }
        }
    }


    @SuppressWarnings("GroovyUnusedDeclaration")
    void setProfileData(Arrangement arrangement, Uri nameUri, Uri property, Uri datatype, String data) {
        log.debug "setProfileData ${nameUri} ${datatype} ${data}"
        mustHave(arrangement: arrangement, name: nameUri, property: property) {
            clearAndFlush {
                checkArrangementIsTree(arrangement)

                log.debug "find name and item"
                if (!datatype) datatype = DomainUtils.uri('xs', 'string')

                Node existingName = findSingleName(arrangement, nameUri, 'name')

                addProfileData(existingName, datatype, property, nameUri.toString(), data, arrangement)
            }
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setNslProfileData(Arrangement arrangement, Name name, Uri property, Uri datatype, String data) {
        log.debug "setProfileData ${name} ${datatype} ${data}"
        mustHave(arrangement: arrangement, name: name, property: property) {
            clearAndFlush {
                checkArrangementIsTree(arrangement)

                log.debug "find name and item"
                if (!datatype) datatype = DomainUtils.uri('xs', 'string')

                Node existingName = findSingleNslName(arrangement, name, 'name')

                addProfileData(existingName, datatype, property, name.id.toString(), data, arrangement)
            }
        }
    }

    private void addProfileData(Node existingName,
                                Uri datatype,
                                Uri property,
                                String nameID,
                                String data,
                                Arrangement arrangement) {

        Collection<Link> existingLinks = existingName.subLink.findAll { Link link ->
            DomainUtils.getLinkTypeUri(link) == property
        }

        if (existingLinks.size() > 1) {
            raise makeMsg(Msg.CANNOT_UPDATE_PROFILE_ITEM, [
                    existingName,
                    property,
                    makeMsg(Msg.MULTIPLE_PROFILE_VALUES_FOUND, property)
            ])
        }

        Link existingLink = existingLinks.empty ? null : existingLinks[0]

        log.debug "check existing link ${existingLink}"

        if (!data && !existingLink?.subnode?.literal) return
        if (data == existingLink?.subnode?.literal) return

        // Value nodes are never versioned. That is, the literal value '2' is never a replacemnt
        // for the literal value '3'. Perhaps one day I will organise for all
        // values to be shared. But at present, the very fact that they are not tells you something intersting.

        log.debug "temp space and adopt"

        Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(arrangement.namespace)
        Node newName = adoptAndCheckOut(tempSpace, existingName)

        Map<Node, Node> v = new HashMap<Node, Node>()
        v.put(existingName, newName)

        if (existingLink) {
            log.debug "unlink old value"
            basicOperationsService.deleteLink(newName, existingLink.linkSeq)
        }

        if (data) {
            log.debug "create new value"

            basicOperationsService.createDraftNode(
                    newName,
                    VersioningMethod.F,
                    NodeInternalType.V,
                    nodeType: datatype,
                    linkType: property,
                    literal: data
            )
        }

        log.debug "persist, version, cleanup"
        Event e = basicOperationsService.newEvent existingName.root.namespace, "setNslProfileData ${nameID}.${property}"

        basicOperationsService.persistNode e, tempSpace.node
        versioningService.performVersioning e, v, arrangement
        basicOperationsService.moveFinalNodesFromTreeToTree tempSpace, arrangement
        basicOperationsService.deleteArrangement tempSpace
    }

    private static mustHave(Map things, Closure work) {
        things.each { k, v ->
            if (!v) {
                throw new IllegalArgumentException("$k must not be null")
            }
        }
        return work()
    }

    private clearAndFlush(Closure work) {
        if (sessionFactory_nsl.getCurrentSession().isDirty()) {
            throw new IllegalStateException("Changes to the classification trees may only be done via BasicOperationsService");
        }
        sessionFactory_nsl.getCurrentSession().clear();
        // I don't use a try/catch because if an exception is thrown then meh
        Object ret = work();
        sessionFactory_nsl.getCurrentSession().flush();
        sessionFactory_nsl.getCurrentSession().clear();
        return DomainUtils.refetchObject(ret);
    }
}
