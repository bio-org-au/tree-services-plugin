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

import au.org.biodiversity.nsl.*;
import grails.transaction.Transactional
import org.hibernate.SessionFactory
import org.apache.shiro.SecurityUtils;

import java.security.Principal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp

import static au.org.biodiversity.nsl.tree.HibernateSessionUtils.*

/**
 * This service provides the basic operations that manipulate the tree. It sits directly on top of the database and 
 * calls native SQL.
 *
 * These methods do not check that hibernate is in good order before calling native queries. Beware!
 * (They used to, but it was severely impacting performance). This means that if the hibernate objects you pass in
 * do not correctly mirror the database, then these operations may leave the data in an incorrect state.
 *
 * Most of the methods here leave hibernate in an incorrect state. Be sure to evict and refresh everything after
 * you are done working with the data using these methods.
 *
 *
 * <strong>OK! I have made this change to fix our hibernate/native issues</strong>
 *
 * With the exception of methods that have been moved into QueryService, <strong>every method</strong> clears the
 * session on entry and flushes the session before exit. This means that after doing any basic operation, your hibernate
 * objects are stale and you need to refetch them. Return values from the methods themselves are ok.
 *
 * Each method, after doing the initial clear, refetches its arguments. THis means that it is ok to pass a stale object
 * to these methods - the only thing we really use are the ids.
 *
 * It also means that *BEFORE* you call any basic operation, you must *FLUSH* anything that you have touched. This is
 * not usually a problem, because you are not supposed to do anything to the tree other than by using the basic
 * operations.
 *
 * It's horrible, but its the only way.
 *
 * @author ibis
 */

/*
 * The basic problem I am having is that although for many of these operations, using a prepared statement and embedded 
 * SQL is very inappropriate, there is no other way to do certain things.
 * 
 * The situation is most acute in cases like this:
 *
 * insert into tree_temp_id(id)
 * select n.id from (
 * 	with nn(id) as (
 * 		select tree_node.id from tree_node where id = ?
 * 	union all
 * 		select tree_node.id
 * 	from
 * 		nn, tree_link, tree_node
 * 	where
 * 		nn.id = tree_link.supernode_id
 * 		and	tree_link.subnode_id =  tree_node.id
 * 		and tree_node.CHECKED_IN_AT_ID is null
 * 	)
 * 	select id from nn
 * ) n
 * 
 * Oracle will not permit a with clause on an insert statement, so the recursive bit has to be jammed into a subquery 
 * in the from clause. But hibernate does not see substitution parameters in the from clause, so there is no way to pass 
 * in the root node of the tree.
 * 
 * Rather than have some methods use one technique and some methods another, which would mean that hibernate 'sees' 
 * some things and does not 'see' others, I have simply uniformly used embedded native oracle SQL across all of these 
 * methods, even simple inserts of one row. The purpose of this class is to encapsulate that.
 * 
 * I use the hibernate methods to perform read-only checks, but the actual work gets done in a doWork closure.
 */

@Transactional(rollbackFor = [ServiceException])
class BasicOperationsService {
    static datasource = 'nsl'

    def messageSource
    SessionFactory sessionFactory_nsl
    QueryService queryService
    def grailsApplication

    UriNs nslNameNs() {
        return UriNs.findByLabel(grailsApplication.config.nslTreePlugin.nslNameNamespace as String)
    }

    UriNs nslInstanceNs() {
        return UriNs.findByLabel(grailsApplication.config.nslTreePlugin.nslInstanceNamespace as String)
    }

    /**
     * @deprecated moved to QueryService
     */

    Long getNextval() {
        return queryService.getNextval()
    }

    /**
     * @deprecated moved to QueryService
     */

    Timestamp getTimestamp() {
        return queryService.getTimestamp()
    }

    def checkAndSave(def thing, List errors) {
        if (thing.validate()) {
            thing.save()
        } else {
            log.error "Error adding ${thing.dump()}"
            def locale = java.util.Locale.getDefault()
            for (fieldErrors in thing.errors) {
                for (error in fieldErrors.allErrors) {
                    String message = messageSource.getMessage(error, locale)
                    log.error message
                    errors.add(message)
                }
            }
        }
        return thing
    }

    private static String getPrincipal() {
        return SecurityUtils.subject?.principal as String ?: 'No principal'
    }

    /**
     * Create a new Event. A user is required to add a new event, we'll get it from spring security if you're logged in
     * if not, it will throw an exception
     * @param note
     * @return
     */
    Event newEvent(Namespace namespace, String note) {
        newEvent(namespace, note, getPrincipal())
    }

    Event newEvent(Namespace namespace, String note, String authUser) {
        newEventTs(namespace, new Timestamp(new Date().time), authUser, note);
    }

    Event newEventTs(Namespace namespace, Timestamp ts, String authUser, String note) {
        if (!namespace) throw new IllegalArgumentException("null namespace");
        if (!ts) throw new IllegalArgumentException("null timestamp");
        if (!authUser) {
            authUser = getPrincipal()
        }

        clearAndFlush {
            Event event = new Event(namespace: namespace, note: note, authUser: authUser, timeStamp: ts)
            event.save()
            return event;
        } as Event
    }

    Arrangement createTemporaryArrangement(Namespace namespace) {
        if (!namespace) throw new IllegalArgumentException("null namespace");
        clearAndFlush {
            // temporary arangments do not belong to any shard
            Arrangement tempArrangement = new Arrangement(arrangementType: ArrangementType.Z, synthetic: 'Y', namespace: namespace, owner: 'INTERNAL')
            tempArrangement.save()
            Node tempNode = new Node(
                    root: tempArrangement,
                    internalType: NodeInternalType.Z,
                    typeUriNsPart: UriNs.get(1),
                    typeUriIdPart: 'temp-arrangement-root',
                    synthetic: true
            )
            tempNode.save()
            tempArrangement.node = tempNode
            tempArrangement.save()
            return tempArrangement
        } as Arrangement
    }

    /**
     * A classification has a checked-in classification node with a tracking link to a checked-in root node. Changes to a classification
     * are made by checking out the root and versioning it.
     * @param label
     * @param description
     * @return
     */

    Arrangement createClassification(Event event, String label, String description, boolean shared) {
        mustHave(event: event, label: label) {
            clearAndFlush {
                event = DomainUtils.refetchEvent(event)

                Arrangement classification = new Arrangement(
                        namespace: event.namespace,
                        arrangementType: ArrangementType.P,
                        synthetic: 'N',
                        label: label,
                        description: description,
                        owner: event.authUser,
                        shared: shared
                )
                classification.save();

                Node classificationNode = new Node(
                        root: classification,
                        internalType: NodeInternalType.S,
                        typeUriNsPart: UriNs.get(1),
                        typeUriIdPart: 'classification-node',
                        synthetic: false,
                        checkedInAt: event
                )
                classificationNode.save();

                Node classificationRoot = new Node(
                        root: classification,
                        internalType: NodeInternalType.T,
                        typeUriNsPart: UriNs.get(1),
                        typeUriIdPart: 'classification-root',
                        synthetic: false,
                        checkedInAt: event
                )

                classificationRoot.save()

                // I have to do this here and not earlier because GORM is too fsking clever
                // it thinks that because Arrangement only has one attribute of type
                // Node, that when you save a node with its arrangment set, the arrangemnt
                // node also has to be set. Grr.

                classification.node = classificationNode
                classification.save();

                Link classificationRootLink = new Link(
                        supernode: classificationNode,
                        subnode: classificationRoot,
                        typeUriNsPart: UriNs.get(1),
                        typeUriIdPart: 'classification-root-link',
                        versioningMethod: VersioningMethod.T,
                        linkSeq: 1,
                        synthetic: false
                )

                classificationRootLink.save()
                classificationNode.addToSubLink(classificationRootLink)
                classificationNode.save()

                return classification
            } as Arrangement
        } as Arrangement
    }

    /**
     * A user workspace has no trackihng node. When it is created, the top node is in DRAFT state.
     * @param label
     * @param description
     * @return
     */

    Arrangement createWorkspace(Event event, String owner, boolean shared, String title, String description) {
        mustHave(event: event, owner: owner, title: title) {
            clearAndFlush {
                event = DomainUtils.refetchEvent(event)

                Arrangement workspace = new Arrangement(
                        namespace: event.namespace,
                        arrangementType: ArrangementType.U,
                        synthetic: 'N',
                        owner: owner,
                        shared: shared,
                        title: title,
                        description: description)
                workspace.save();

                Node workspaceRoot = new Node(
                        root: workspace,
                        internalType: NodeInternalType.S,
                        typeUriNsPart: UriNs.get(1),
                        typeUriIdPart: 'workspace-root',
                        synthetic: false,
                )

                workspaceRoot.save()

                // I have to do this here and not earlier because GORM is too fsking clever
                // it thinks that because Arrangement only has one attribute of type
                // Node, that when you save a node with its arrangment set, the arrangemnt
                // node also has to be set. Grr.

                workspace.node = workspaceRoot
                workspace.save();

                return workspace
            } as Arrangement
        } as Arrangement
    }

    void updateWorkspace(Arrangement arrangement, boolean shared, String title, String description) {
        mustHave(arrangement: arrangement) {
            clearAndFlush {
                arrangement = DomainUtils.refetchArrangement(arrangement)

                if(arrangement.arrangementType != ArrangementType.U) throw new IllegalArgumentException("Not a workspace");

                if (!title) {
                    throw new IllegalArgumentException("Workspaces must have a title");
                }

                arrangement.shared = shared;
                arrangement.title = title;
                arrangement.description = description;
                arrangement.save();
            }
        }
    }

    void updateArrangement(Arrangement arrangement, String title, String description) {
        mustHave(arrangement: arrangement) {
            clearAndFlush {
                arrangement = DomainUtils.refetchArrangement(arrangement)

                switch (arrangement.arrangementType) {
                    case ArrangementType.E:
                        throw new IllegalArgumentException("The end tree cannot be modified");
                        break;

                    case ArrangementType.P:
                        break;

                    case ArrangementType.U:
                        if (!title) {
                            throw new IllegalArgumentException("Workspaces must have a title");
                        }
                        break;

                    case ArrangementType.Z:
                        throw new IllegalArgumentException("Temp trees cannot be modified");
                        break;
                }

                arrangement.title = title;
                arrangement.description = description;
                arrangement.save();
            }
        }
    }

    /**
     *
     * @param params[nodeType] Uri
     * @param params[linkType] Uri
     * @param params[name] Uri
     * @param params[taxon] Uri
     * @param supernode
     * @param versioningMethod
     * @param linkSeq
     * @return
     */

    Node createDraftNode(Map params = [:], Node supernode, VersioningMethod versioningMethod, NodeInternalType internalType) {
        mustHave(supernode: supernode, versioningMethod: versioningMethod, internalType: internalType) {
            clearAndFlush {
                supernode = DomainUtils.refetchNode(supernode)
                params = DomainUtils.refetchMap(params)

                Uri nodeType = params.nodeType as Uri
                Uri linkType = params.linkType as Uri
                Uri name = params.name as Uri
                Uri taxon = params.taxon as Uri
                Uri resource = params.resource as Uri
                String literal = params.literal as String
                Integer linkSeq = params.seq as Integer
                Name nslName = params.nslName as Name
                Instance nslInstance = params.nslInstance as Instance;

                if (nslName && name == null) {
                    name = new Uri(nslNameNs(), nslName.id)
                }
                if (nslInstance && taxon == null) {
                    taxon = new Uri(nslInstanceNs(), nslInstance.id)
                }

                checkWeCanCreateThisHere(supernode, versioningMethod, internalType, literal, name, nslName, taxon, nslInstance, resource, linkSeq)

                Node node = new Node(
                        internalType: internalType,
                        typeUriNsPart: nodeType?.nsPart ?: UriNs.get(0),
                        typeUriIdPart: nodeType?.idPart,
                        name: nslName,
                        nameUriNsPart: name?.nsPart,
                        nameUriIdPart: name?.idPart,
                        instance: nslInstance,
                        taxonUriNsPart: taxon?.nsPart,
                        taxonUriIdPart: taxon?.idPart,
                        resourceUriNsPart: resource?.nsPart,
                        resourceUriIdPart: resource?.idPart,
                        literal: literal,
                        synthetic: false
                )
                node.root = supernode.root
                node.save()

                Link maxSeqLink = supernode.subLink.max { it.linkSeq }
                Integer nextSeq = linkSeq ?: (maxSeqLink ? maxSeqLink.linkSeq + 1 : 1)

                Link appendLink = new Link(
                        subnode: node,
                        typeUriNsPart: linkType?.nsPart ?: UriNs.get(0),
                        typeUriIdPart: linkType?.idPart ?: null,
                        linkSeq: nextSeq,
                        versioningMethod: versioningMethod,
                        synthetic: false
                )

                supernode.addToSubLink(appendLink)
                supernode.save()
                return node
            } as Node
        } as Node
    }

    private void checkWeCanCreateThisHere(Node supernode, VersioningMethod versioningMethod, NodeInternalType internalType,
                                          String literal, Uri name, Name nslName, Uri taxon, Instance nslInstance, Uri resource, Integer linkSeq) {
        if (!supernode) {
            throw new IllegalArgumentException('null supernode')
        }

        if (!versioningMethod) {
            throw new IllegalArgumentException('null versioningMethod')
        }

        if (supernode.internalType == NodeInternalType.V) {
            ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.LITERAL_NODE_MAY_NOT_HAVE_SUBNODES, [supernode])])
        }

        if (DomainUtils.isCheckedIn(supernode)) {
            ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, [supernode])])
        }

        checkInternalType(internalType, supernode, literal, name, taxon, resource, versioningMethod)

        if (nslName) {
            if (!name || name.nsPart != nslNameNs() || name.idPart != (nslName.id as String)) {
                ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.NAME_URI_DOESNT_MATCH, [name, nslName])])

            }
            if (supernode.root.namespace != nslName.namespace) {
                ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [supernode.root.namespace, nslName.namespace])])
            }
        }

        if (nslInstance) {
            if (!taxon || taxon.nsPart != nslInstanceNs() || taxon.idPart != (nslInstance.id as String)) {
                ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.INSTANCE_URI_DOESNT_MATCH, [taxon, nslInstance])]);
            }
            if (supernode.root.namespace != nslInstance.namespace) {
                ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [supernode.root.namespace, nslInstance.namespace])])
            }
        }

        if (literal != null && (name != null || taxon != null || resource != null)) {
            ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.CANNOT_MIX_LITERAL_AND_URI)])
        }

        if (linkSeq != null) {
            if (linkSeq.intValue() <= 0) {
                throw new IllegalArgumentException("Link sequence ${linkSeq} must be positive or null")
            }

            Link existingLink = supernode.subLink.find { it.linkSeq == linkSeq }
            if (existingLink) {
                throw new IllegalArgumentException("Link sequence ${linkSeq} is already be used")
            }
        }
    }

    private
    static void checkInternalType(NodeInternalType internalType, Node supernode, String literal, Uri name, Uri taxon, Uri resource, VersioningMethod versioningMethod) {
        switch (internalType) {
            case NodeInternalType.S:
                ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.CANNOT_CREATE_SYSTEM_NODES)])
                break

            case NodeInternalType.Z:
                ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.CANNOT_CREATE_TEMP_NODES)])
                break

            case NodeInternalType.T:
                if (literal != null) {
                    ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.CANNOT_CREATE_TAXONOMIC_NODE_WITH_A_LITERAL_VALUE)])
                }
                break

            case NodeInternalType.D:
                if (name != null || taxon != null) {
                    ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.CANNOT_CREATE_DOCUMENT_NODE_WITH_A_NAME_OR_TAXON)])
                }
                break

            case NodeInternalType.V:
                if (name != null || taxon != null) {
                    ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.CANNOT_CREATE_VALUE_NODE_WITH_A_NAME_OR_TAXON)])
                }
                if (!resource && !literal) {
                    ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.VALUE_NODES_MUST_HAVE_RESOURCE_OR_LITERAL)])
                }
                if (resource != null && literal != null) {
                    ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.VALUE_NODES_MUST_HAVE_RESOURCE_OR_LITERAL)])
                }
                if (versioningMethod != VersioningMethod.F) {
                    ServiceException.raise ServiceException.makeMsg(Msg.createDraftNode, [supernode, ServiceException.makeMsg(Msg.VALUE_NODES_MUST_USED_FIXED_LINK)])
                }
                break
        }
    }

/**
 * @param params[nodeType] Uri
 * @param params[name] Uri
 * @param params[taxon] Uri
 * @param n
 */

    public void updateDraftNode(Map params = [:], Node n) {
        mustHave(node: n) {
            clearAndFlush {
                n = DomainUtils.refetchNode(n)
                params = DomainUtils.refetchMap(params)

                if (DomainUtils.isCheckedIn(n)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [n, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, n)])
                }

                if (
                (params.containsKey('literal') ? params['literal'] : n.literal) //
                        && ((params.containsKey('name') ? params['name'] : DomainUtils.getNameUri(n))//
                        || (params.containsKey('taxon') ? params['taxon'] : DomainUtils.getTaxonUri(n))//
                        || (params.containsKey('resource') ? params['resource'] : DomainUtils.getResourceUri(n))
                )) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [n, ServiceException.makeMsg(Msg.CANNOT_MIX_LITERAL_AND_URI)])
                }

                if ((params.containsKey('literal') ? params['literal'] : n.literal) && !n.subLink.isEmpty()) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [
                            n,
                            ServiceException.makeMsg(Msg.LITERAL_NODE_MAY_NOT_HAVE_SUBNODES, supernode)
                    ])
                }

                if (params.containsKey('nodeType')) {
                    Uri u = params['nodeType'] as Uri
                    if (!u) u = DomainUtils.getDefaultNodeTypeUriFor(n.internalType)
                    n.typeUriNsPart = u.nsPart
                    n.typeUriIdPart = u.idPart
                }

                Uri name = params['name'] as Uri
                Name nslName = params['nslName'] as Name
                Uri taxon = params['taxon'] as Uri
                Instance nslInstance = params['nslInstance'] as Instance

                if (nslName && n.root.namespace != nslName.namespace) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [
                            n,
                            ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [n.root.namespace, nslName.namespace])
                    ])
                }

                if (nslInstance && n.root.namespace != nslInstance.namespace) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [
                            n,
                            ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [n.root.namespace, nslInstance.namespace])
                    ])
                }

                if (nslName && name == null) {
                    name = new Uri(nslNameNs(), nslName.id)
                }
                if (nslInstance && taxon == null) {
                    taxon = new Uri(nslInstanceNs(), nslInstance.id)
                }

                if (nslName) {
                    if (!name || name.nsPart != nslNameNs() || name.idPart != (nslName.id as String)) {
                        ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [n, ServiceException.makeMsg(Msg.NAME_URI_DOESNT_MATCH, [name, nslName])]);
                    }

                    n.name = nslName;
                }

                if (nslInstance) {
                    if (!taxon || taxon.nsPart != nslInstanceNs() || taxon.idPart != (nslInstance.id as String)) {
                        ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNode, [n, ServiceException.makeMsg(Msg.INSTANCE_URI_DOESNT_MATCH, [taxon, nslInstance])]);
                    }

                    n.instance = nslInstance;
                }

                if (name != null) {
                    n.nameUriNsPart = name.nsPart
                    n.nameUriIdPart = name.idPart
                }

                if (taxon != null) {
                    n.taxonUriNsPart = taxon.nsPart
                    n.taxonUriIdPart = taxon.idPart
                }

                if (params.containsKey('resource')) {
                    Uri u = params['resource'] as Uri
                    n.resourceUriNsPart = u?.nsPart
                    n.resourceUriIdPart = u?.idPart
                }

                if (params.containsKey('literal')) {
                    n.literal = params['literal'] as String
                }

                n.save()
            }
        }
    }

/**
 * @param params[supernode] Node - new location for the draft node
 * @param params[versioningMethod] VersioningMethod
 * @param params[linkType] Uri
 * @param params[seq] Integer
 * @param node
 * @param nodeSeq
 */

    public void updateDraftNodeLink(Map params = [:], Node node, int seq) {
        // ok, we have two separate things to do. We want to update the node properties, if there
        // are any to update, and we maybe want to change the position of the node

        mustHave(node: node) {
            clearAndFlush {
                node = DomainUtils.refetchNode(node)
                params = DomainUtils.refetchMap(params)

                if (DomainUtils.isCheckedIn(node)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink, [node, seq, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, node)])
                }

                Link l = Link.findBySupernodeAndLinkSeq(node, seq)

                if (l == null) {
                    ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink, [node, seq, ServiceException.makeMsg(Msg.NO_LINK_SEQ_N, [node, seq])])
                }

                if (params['seq']) {
                    int newSeq = params['seq'] as Integer
                    if (seq <= 0) {
                        throw new IllegalArgumentException("Seq must be positive: ${newSeq}")
                    }
                }

                if (params.containsKey('supernode')) {
                    Node superNode = params['supernode'] as Node

                    if (!superNode) {
                        throw new IllegalArgumentException("new supernode is null")
                    }

                    if (superNode.internalType == NodeInternalType.V) {
                        ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink2, [
                                l,
                                ServiceException.makeMsg(Msg.LITERAL_NODE_MAY_NOT_HAVE_SUBNODES, [superNode, node])
                        ])
                    }
                    if (superNode.root != node.root) {
                        ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink2, [
                                l,
                                ServiceException.makeMsg(Msg.NEW_SUPERNODE_BELONGS_TO_DIFFERENT_DRAFT_TREE, [superNode])
                        ])
                    }

                    if (DomainUtils.isCheckedIn(superNode)) {
                        ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink2, [l, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, [superNode])])
                    }

                    for (Node nn = superNode; nn; nn = DomainUtils.getDraftNodeSupernode(nn)) {
                        if (DomainUtils.isCheckedIn(nn)) {
                            // this never happens
                            ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink2, [l, "Draft node is a subnode of persistent node ${nn}"])
                        }
                        if (nn.supLink.size() > 1) {
                            // neither does this (well, not yet)
                            ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink2, [
                                    l,
                                    "Draft node ${nn} has more than one attachment point"
                            ])
                        }
                        if (nn == l.subnode) {
                            ServiceException.raise ServiceException.makeMsg(Msg.updateDraftNodeLink2, [l, ServiceException.makeMsg(Msg.LOOP_DETECTED, [superNode, l.subnode])])
                        }
                    }
                }

                doWork(sessionFactory_nsl) { Connection cnct ->
                    if (params['versioningMethod'] || params.containsKey('linkType')) {
                        VersioningMethod m = params['versioningMethod'] ? (VersioningMethod) params['versioningMethod'] : l.versioningMethod
                        Uri linkType = params.containsKey('linkType') ? (Uri) params['linkType'] : l.linkTypeUri

                        withQ cnct, '''update tree_link set versioning_method=?, type_uri_ns_part_id=?, type_uri_id_part=? where id= ?''', { PreparedStatement qry ->
                            qry.setString(1, m.name())
                            qry.setLong(2, linkType ? linkType.nsPart.id : 0L)
                            qry.setString(3, linkType ? linkType.idPart : null)
                            qry.setLong(4, l.id)
                            qry.executeUpdate()
                        }
                    }

                    if (params.containsKey('supernode') || params['seq']) {

                        // 1 - move the link to one after the end
                        // 2 - close up the gap, moving  the link to the end.
                        // 3 - open up a gap
                        // 4 - move the link into the gap

                        Node newN = params.containsKey('supernode') ? (Node) params['supernode'] : node

                        // find destination max
                        Integer max = withQ(cnct, 'select max(link_seq) as n from TREE_LINK where supernode_id = ?') { PreparedStatement qry ->
                            qry.setLong(1, newN.id)
                            ResultSet rs = qry.executeQuery()
                            rs.next()
                            int foo = rs.getInt(1)
                            rs.close()
                            foo
                        } as Integer

                        withQ(cnct, 'update tree_link set supernode_id = ?, link_seq = ?, lock_version = lock_version+1 where id = ?') { PreparedStatement qry ->
                            qry.setLong(1, newN.id)
                            qry.setInt(2, max + 1)
                            qry.setLong(3, l.id)
                            qry.executeUpdate()
                        }

                        /* don't actually need this, and it's confusing postgres
                 withQ cnct, 'update tree_link set link_seq = link_seq - 1 where supernode_id = ? and link_seq >= ?', { PreparedStatement qry ->
                 qry.setLong(1, n.id)
                 qry.setInt(2, seq)
                 qry.executeUpdate()
                 }
                 */

                        // perhaps this should be a simpler "roll everything between a and b" algorithm.

                        int newSeq = params['seq'] ? (Integer) params['seq'] : max

                        // if the newSeq is >= max, then the operations above suffice to reorder things.
                        if (newSeq < max) {
                            boolean linkSeqIsUsed =
                                    withQ cnct, 'select count(*) as ct from tree_link where SUPERNODE_ID = ? and LINK_SEQ = ?', { isLinkSeqUsed ->
                                        isLinkSeqUsed.setLong(1, newN.id)
                                        isLinkSeqUsed.setInt(2, newSeq)
                                        ResultSet rs = isLinkSeqUsed.executeQuery()
                                        rs.next()
                                        boolean isItUsed = rs.getInt(1) != 0
                                        rs.close()
                                        return isItUsed // that is - return this value from the closure
                                    }

                            if (linkSeqIsUsed) {
                                withQ cnct, '''
							update tree_link
							set link_seq = link_seq + 1
							where 
								SUPERNODE_ID = ?
								and LINK_SEQ >= ?
								and LINK_SEQ <= (
									select min(l1.link_seq)
									from tree_link l1
									where 
										l1.SUPERNODE_ID = ?
										and l1.LINK_SEQ >= ?
										and not exists (
											select 1
											from tree_link l2
											where l2.SUPERNODE_ID = ?
											and l2.link_seq = l1.link_seq+1
										)
									)
							''', { PreparedStatement incrementSeq ->
                                    incrementSeq.setLong(1, (Long) newN.id)
                                    incrementSeq.setInt(2, (Integer) newSeq)
                                    incrementSeq.setLong(3, (Long) newN.id)
                                    incrementSeq.setInt(4, (Integer) newSeq)
                                    incrementSeq.setLong(5, (Long) newN.id)
                                    incrementSeq.executeUpdate()
                                }
                            }

                            withQ cnct, 'update tree_link set link_seq = ? where id = ?', { PreparedStatement qry ->
                                qry.setInt(1, newSeq)
                                qry.setLong(2, l.id)
                                qry.executeUpdate()
                            }
                        }

                        // finally, because links belong to the supernode, an update to a link is an update to the supernode

                        withQ cnct, 'update tree_node set lock_version=lock_version+1 where id in (?, ?)', { PreparedStatement qry ->
                            qry.setLong(1, node.id)
                            qry.setLong(2, newN.id)
                            qry.executeUpdate()
                        }
                    }
                }
            }
        }
    }

    public void simpleMoveDraftLink(Link l, Node newSupernode) {
        mustHave(link: l, newSupernode: newSupernode) {
            l = DomainUtils.refetchLink(l)
            newSupernode = DomainUtils.refetchNode(newSupernode)

            if (l.supernode.id == newSupernode.id) return;


            if (DomainUtils.isCheckedIn(l.supernode)) throw new IllegalArgumentException("existing link is checked in");
            if (DomainUtils.isCheckedIn(newSupernode)) throw new IllegalArgumentException("target is checked in");
            if (l.supernode.root.id != newSupernode.root.id) throw new IllegalArgumentException("target is from a different tree");

            for (Node sup = newSupernode; sup; sup = DomainUtils.getSingleSupernode(sup)) {
                if (sup.id == l.subnode.id) {
                    throw new IllegalArgumentException("new supernode is a subnode of the link");
                }
            }

            int linkSeq = 0;
            for (Link ll : newSupernode.subLink) {
                linkSeq = Math.max(linkSeq, ll.linkSeq);
            }


            l.supernode = newSupernode;
            l.linkSeq = linkSeq + 1;

            l.save();
        }
    }

/**
 * Deletes a link from a draft node to a persistent node. This operation un-adopts a node, or modifies the child noes of a checked out node.
 * @param params no params defined
 * @param n
 * @param nodeSeq
 */

    public void deleteLink(Node n, int seq) {
        mustHave(node: n) {
            clearAndFlush {
                n = DomainUtils.refetchNode(n)
                // ok, we have two separate things to do. We want to update the node properties, if there
                // are any to update, and we maybe want to change the position of the node

                // TODO: this operation can create orphans

                if (DomainUtils.isCheckedIn(n)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftNodeLink, [n, seq, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, n)])
                }

                Link l = Link.findBySupernodeAndLinkSeq(n, seq)

                if (l == null) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftNodeLink2, [l, ServiceException.makeMsg(Msg.NO_LINK_SEQ_N, [n, seq])])
                }

                if (!DomainUtils.isCheckedIn(l.subnode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftNodeLink2, [l, ServiceException.makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, l.subnode)])
                }

                doWork(sessionFactory_nsl) { Connection cnct ->
                    // a link belngs to its supernode, so increment the supernode
                    withQ cnct, '''update tree_node set lock_version=lock_version+1 where id = ?''', { PreparedStatement qry ->
                        qry.setLong(1, l.supernode.id)
                        qry.executeUpdate()
                    }
                    withQ cnct, '''delete from tree_link where id = ?''', { PreparedStatement qry ->
                        qry.setLong(1, l.id)
                        qry.executeUpdate()
                    }
                }
            }
        }
    }

/** delete a draft node that does not have subnodes. */

    public void deleteDraftNode(Node n) {
        mustHave(node: n) {
            clearAndFlush {
                n = DomainUtils.refetchNode(n)

                if (DomainUtils.isCheckedIn(n)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftNode, [n, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, n)])
                }

                if (n.root.node == n) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftNode, [n, ServiceException.makeMsg(Msg.ROOT_NODE_NOT_PERMITTED, n)])
                }

                if (n.supLink.size() != 1) {
                    // this never happens
                    throw new IllegalStateException("Draft node ${n} does not have one supernode!")
                }

                if (n.subLink.size() != 0) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftNode, [n, ServiceException.makeMsg(Msg.NODE_WITH_SUBNODES_NOT_PERMITTED, n)])
                }

                doWork(sessionFactory_nsl) { Connection cnct ->
                    // a link belngs to its supernode, so increment the supernode
                    withQ cnct, '''update tree_node set lock_version=lock_version+1 where id in
                            (select supernode_id from tree_link where subnode_id = ?) ''', { PreparedStatement qry ->
                        qry.setLong(1, n.id)
                        qry.executeUpdate()
                    }
                    withQ cnct, '''delete from tree_link where subnode_id = ?''', { PreparedStatement qry ->
                        qry.setLong(1, n.id)
                        qry.executeUpdate()
                    }
                    withQ cnct, '''delete from tree_node where id = ?''', { PreparedStatement qry ->
                        qry.setLong(1, n.id)
                        qry.executeUpdate()
                    }
                }
            }
        }
    }

/** recursively delete a draft node and all of its subnodes */

    public void deleteDraftTree(Node n) {
        mustHave(node: n) {
            clearAndFlush {
                n = DomainUtils.refetchNode(n)

                if (DomainUtils.isCheckedIn(n)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftTree, [n, ServiceException.makeMsg(Msg.ROOT_NODE_NOT_PERMITTED, n)])
                }

                if (n.root.node == n) {
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteDraftTree, [n, ServiceException.makeMsg(Msg.ROOT_NODE_NOT_PERMITTED, n)])
                }

                /* all right. we make use of session-scoped temp tables to do this work.
                 * they act slightly differently in oracle and postgres, which means that this code
                 * will need to be fiddled with if we move to postgres
                 */

                doWork(sessionFactory_nsl) { Connection cnct ->
                    // a link belngs to its supernode, so increment the supernode
                    withQ cnct, '''update tree_node set lock_version=lock_version+1 where id in
					(select supernode_id from tree_link where subnode_id = ?) 
					''', { PreparedStatement qry ->
                        qry.setLong(1, n.id)
                        qry.executeUpdate()
                    }
                }

                delete_node_tree(n.id)
            }
        }
    }

    public void deleteArrangement(Arrangement arrangement) {
        mustHave(arrangement: arrangement) {
            clearAndFlush {
                arrangement = DomainUtils.refetchArrangement(arrangement)

                // TODO - security checks!

                // if any other trees make use of nodes belonging to the arrangement, then we cannot delete it.

                List<Link> sharedLinks = Link.executeQuery(
                        'select l from Link l where l.subnode.root = :arrangement and l.supernode.root <> :arrangement',
                        [arrangement: arrangement])
                if (sharedLinks) {
                    Message message = ServiceException.makeMsg(Msg.NODES_ARE_USED_IN_OTHER_TREES, [arrangement])
                    message.nested << sharedLinks
                    ServiceException.raise ServiceException.makeMsg(Msg.deleteArrangement, [arrangement, message])
                }

                arrangement.node = null
                arrangement.save(flush: true) // must do this before we can delete the arangement node
                Link.executeUpdate('delete from Link where supernode in (select n from Node n where n.root = :arrangement)', [arrangement: arrangement])
                Node.executeUpdate('delete from Node where root = :arrangement', [arrangement: arrangement])
                arrangement.delete()
            }
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    public void persistAll(Event e, Arrangement a) {
        mustHave(Event: (e), Arrangement: a) {

            if (e.namespace != a.namespace) {
                ServiceException.raise ServiceException.makeMsg(Msg.persistAll, [
                        a,
                        ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [e.namespace, a.namespace])
                ])
            }

            clearAndFlush {
                e = DomainUtils.refetchEvent(e)
                a = DomainUtils.refetchArrangement(a)

                doWork(sessionFactory_nsl) { Connection cnct ->
                    /* in the course of normal workflow, a draft node will not have a draft node subnode that comes from another tree.
                 however, it's important to check for this anyway, because if a draft node like that is checked in it will break one of the main
                 constraints of the system - a persistent node *never* has a draft subnode
                */

                    Message cannotPersist = new Message(Msg.persistAll, [a])

                    withQ cnct, '''
						select tree_link.supernode_id, tree_link.subnode_id
						from tree_node a
							join tree_link on a.id = tree_link.supernode_id
							join tree_node b on tree_link.subnode_id = b.id
						where
							a.tree_arrangement_id = ?
							and a.checked_in_at_id is null
							and b.tree_arrangement_id <> a.tree_arrangement_id
							and b.checked_in_at_id is null
					''', { PreparedStatement qry ->
                        qry.setLong(1, a.id)
                        ResultSet rs = qry.executeQuery()
                        while (rs.next()) {
                            cannotPersist.nested.add new Message(Msg.DRAFT_NODE_HAS_A_DRAFT_SUBNODE_IN_ANOTHER_ARRANGEMENT, [Node.get(rs.getLong(1)), Node.get(rs.getLong(2))])
                        }
                        rs.close()
                    }

                    if (!cannotPersist.nested.isEmpty()) {
                        ServiceException.raise cannotPersist
                    }

                    // and, well, that's really the only check we need.

                    // I'll add the bit that does not persist the top node,
                    // but I belive at this point that that is wrong, that classification top nodes
                    // should be persistent.

                    withQ cnct, '''
						update tree_node
						set checked_in_at_id = ?,
							lock_version = lock_version + 1
						where
							tree_arrangement_id = ?
							and checked_in_at_id is null
							and id <> (select node_id from tree_arrangement a where a.id = tree_node.tree_arrangement_id) 
					''', { PreparedStatement qry ->
                        qry.setLong(1, e.id)
                        qry.setLong(2, a.id)
                        int ct = qry.executeUpdate()
                        log.debug "Checked in ${ct} nodes in ${a}"
                    }
                }
            }
        }
    }

/**
 * Check In a node
 * @param e is an event and is basically a timestamp
 * @param n
 */

/*
 * TODO - given that we have tracking links, should I remove the stuff that keeps the root in a draft state?
 */

    public void persistNode(Event e, Node n) {
        mustHave(Event: (e), Node: n) {
            clearAndFlush {
                e = DomainUtils.refetchEvent(e)
                n = DomainUtils.refetchNode(n)

                if (e.namespace != n.root.namespace) {
                    ServiceException.raise ServiceException.makeMsg(Msg.persistAll, [
                            n,
                            ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [e.namespace, n.root.namespace])
                    ])
                }

                if (DomainUtils.isCheckedIn(n)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.persistNode, [
                            n,
                            ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, n)]
                    )
                }

                /* We want to be able to go "persist the whole tree", but this operation should not
                * persist a root node of a tree. To make the queries simpler, I'll just un-persist
                * it when I'm done, if necessary
                */

                /* Very unhappy with this design. A total wart. */

                boolean need_to_uncheck_root_node = (n.root.node == n) && (!DomainUtils.isCheckedIn(n))

                doWork(sessionFactory_nsl) { Connection cnct ->

                    // ok. Find all nodes needing to be checked in. I am turning this into a loop
                    // because the recursive query seems to be slow.

                    create_tree_temp_id cnct
                    create_tree_temp_id2 cnct
                    create_tree_temp_id3 cnct

                    withQ cnct, 'insert into tree_temp_id3(id) values ( ? ) ', { PreparedStatement stmt ->
                        stmt.setLong(1, n.id)
                        stmt.executeUpdate()
                    }


                    PreparedStatement step1 = cnct.prepareStatement('INSERT INTO tree_temp_id(id) SELECT id FROM tree_temp_id3')
                    PreparedStatement step2 = cnct.prepareStatement('DELETE FROM tree_temp_id2')
                    PreparedStatement step3 = cnct.prepareStatement('INSERT INTO tree_temp_id2(id) SELECT id FROM tree_temp_id3')
                    PreparedStatement step4 = cnct.prepareStatement('DELETE FROM tree_temp_id3')
                    PreparedStatement step5 = cnct.prepareStatement('''
				INSERT INTO tree_temp_id3(id)
				SELECT tree_link.subnode_id
				FROM tree_temp_id2
					JOIN tree_link ON tree_temp_id2.id = tree_link.supernode_id
					JOIN tree_node ON tree_link.subnode_id = tree_node.id
				WHERE tree_node.checked_in_at_id IS NULL
			''')

                    for (; ;) {
                        step1.executeUpdate();
                        step2.executeUpdate();
                        step3.executeUpdate();
                        step4.executeUpdate();
                        int step5N = step5.executeUpdate();
                        log.debug "found ${step5N} needing to be persisted"
                        if (step5N == 0) break;
                    }

                    // every draft node under this node should belong to the same tree

                    withQ cnct, '''
				select count(*)
				from tree_temp_id join tree_node on tree_temp_id.id = tree_node.id 
				where tree_node.tree_arrangement_id <> ?
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, n.root.id)
                                ResultSet rs = qry.executeQuery()
                                rs.next()
                                int ct = rs.getInt(1)
                                rs.close()
                                if (ct != 0) {
                                    throw new IllegalStateException("Draft node ${n} seems to have draft nodes from another tree attached to it")
                                }
                            }

                    // this next check should never be necessary. Its bulletproofing - we test the integrity of the data structure

                    withQ cnct, '''
				select count(*)
				from tree_temp_id join tree_node on tree_temp_id.id = tree_node.id 
				where tree_node.CHECKED_IN_AT_ID is not null
				''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                rs.next()
                                int ct = rs.getInt(1)
                                rs.close()
                                if (ct != 0) {
                                    throw new IllegalStateException("this never happens - I am incorrectly dragging in non-draft nodes to be persisted")
                                }
                            }

                    // ok! Let's proceed.
                    // don't need the root_id, but I'll copy/paste the subquery anyway.


                    withQ cnct, '''
				update tree_node set CHECKED_IN_AT_ID = ?, lock_version=lock_version+1 where id in (
					select id from tree_temp_id
				)
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, e.id)
                                qry.executeUpdate()
                            }

                    if (need_to_uncheck_root_node) {
                        withQ cnct, '''update tree_node set CHECKED_IN_AT_ID = null, lock_version=lock_version-1 where id = ?''',
                                { PreparedStatement qry ->
                                    qry.setLong(1, n.id)
                                    qry.executeUpdate()
                                }

                    }
                }
            }
        }
    }

    public Link adoptNode(Map params = [:], Node supernode, Node subnode, VersioningMethod versioningMethod) {
        mustHave(supernode: supernode, subnode: subnode, versioningMethod: versioningMethod) {
            clearAndFlush {
                supernode = DomainUtils.refetchNode(supernode)
                subnode = DomainUtils.refetchNode(subnode)
                params = DomainUtils.refetchMap(params)

                if (DomainUtils.isEndNode(supernode) || DomainUtils.isEndNode(subnode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.adoptNode, [
                            supernode,
                            subnode,
                            ServiceException.makeMsg(Msg.END_NODE_NOT_PERMITTED)
                    ])
                }

                if (supernode.root.namespace != subnode.root.namespace) {
                    ServiceException.raise ServiceException.makeMsg(Msg.adoptNode, [
                            supernode,
                            subnode,
                            ServiceException.makeMsg(Msg.NAMESPACE_MISMATCH, [supernode.root.namespace, subnode.root.namespace])
                    ])
                }

                if (supernode.internalType == NodeInternalType.V) {
                    ServiceException.raise ServiceException.makeMsg(Msg.persistNode, [
                            supernode,
                            subnode,
                            ServiceException.makeMsg(Msg.LITERAL_NODE_MAY_NOT_HAVE_SUBNODES, [supernode, subnode])
                    ])
                }

                if (DomainUtils.isCheckedIn(supernode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.adoptNode, [
                            supernode,
                            subnode,
                            ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, supernode)
                    ])
                }

                if (!DomainUtils.isCheckedIn(subnode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.adoptNode, [
                            supernode,
                            subnode,
                            ServiceException.makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, subnode)
                    ])
                }

                if (versioningMethod != versioningMethod.F && !DomainUtils.isCurrent(subnode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.adoptNode, [
                            supernode,
                            subnode,
                            ServiceException.makeMsg(Msg.OLD_NODE_NOT_PERMITTED, subnode)
                    ])
                }

                Uri linkType = params.linkType as Uri
                Integer linkSeq = params.seq as Integer

                if (linkSeq != null && linkSeq.intValue() <= 0) {
                    throw new IllegalArgumentException("Link sequence ${linkSeq} must be positive or null")
                }

                long linkId = queryService.getNextval()

                // OK! everything looks sweet.

                doWork(sessionFactory_nsl) { Connection cnct ->

                    // this is a big blob of copy/pasted code and needs to be refactored

                    if (linkSeq == null) {
                        cnct
                        withQ cnct, '''
					insert into tree_link (
					 ID,					   --NOT NULL NUMBER(38)
					 LOCK_VERSION,			   --NOT NULL NUMBER(38)
					 SUPERNODE_ID,			   --NOT NULL NUMBER(38)
					 SUBNODE_ID,			   --NOT NULL NUMBER(38)
					 TYPE_URI_NS_PART_ID,	   -- NUMBER(38)
					 TYPE_URI_ID_PART,		   -- VARCHAR2(255)
					 LINK_SEQ,				   --NOT NULL NUMBER(38)
					 VERSIONING_METHOD,		   --NOT NULL CHAR(1)
					 IS_SYNTHETIC			   --NOT NULL CHAR(1)
					)
					values (
						?,
						1,
						?,
						?,
						?,
						?,
						coalesce ( (select max(LINK_SEQ) from tree_link where SUPERNODE_ID = ? ), 0) + 1,
						?,
						'N'
					)		
			''', { PreparedStatement appendLink ->
                            appendLink.setLong(1, linkId)
                            appendLink.setLong(2, supernode.id)
                            appendLink.setLong(3, subnode.id)
                            appendLink.setLong(4, linkType ? linkType.nsPart.id : 0L)
                            appendLink.setString(5, linkType ? linkType.idPart : null)
                            appendLink.setLong(6, supernode.id)
                            appendLink.setString(7, versioningMethod.name())
                            appendLink.executeUpdate()
                        }
                    } else {
                        boolean linkSeqIsUsed =
                                withQ cnct, 'select count(*) as ct from tree_link where SUPERNODE_ID = ? and LINK_SEQ = ?', { isLinkSeqUsed ->
                                    isLinkSeqUsed.setLong(1, supernode.id)
                                    isLinkSeqUsed.setLong(2, linkSeq)
                                    ResultSet rs = isLinkSeqUsed.executeQuery()
                                    rs.next()
                                    boolean isItUsed = rs.getInt(1) != 0
                                    rs.close()
                                    return isItUsed // that is - return this value from the closure
                                }

                        if (linkSeqIsUsed) {
                            withQ cnct, '''
						update tree_link
						set link_seq = link_seq + 1
						where 
							SUPERNODE_ID = ?
							and LINK_SEQ >= ?
							and LINK_SEQ <= (
								select min(l1.link_seq)
								from tree_link l1
								where 
									l1.SUPERNODE_ID = ?
									and l1.LINK_SEQ >= ?
									and not exists (
										select 1
										from tree_link l2
										where l2.SUPERNODE_ID = ?
										and l2.link_seq = l1.link_seq+1
									)
								)
						''', { PreparedStatement incrementSeq ->
                                incrementSeq.setLong(1, (Long) supernode.id)
                                incrementSeq.setInt(2, (Integer) linkSeq)
                                incrementSeq.setLong(3, (Long) supernode.id)
                                incrementSeq.setInt(4, (Integer) linkSeq)
                                incrementSeq.setLong(5, (Long) supernode.id)
                                incrementSeq.executeUpdate()
                            }
                        }

                        withQ cnct, '''
					insert into tree_link (
					 ID,					   --NOT NULL NUMBER(38)
					 lock_version,				   --NOT NULL NUMBER(38)
					 SUPERNODE_ID,			   --NOT NULL NUMBER(38)
					 SUBNODE_ID,			   --NOT NULL NUMBER(38)
					 TYPE_URI_NS_PART_ID,	   -- NUMBER(38)
					 TYPE_URI_ID_PART,		   -- VARCHAR2(255)
					 LINK_SEQ,				   --NOT NULL NUMBER(38)
					 VERSIONING_METHOD,		   --NOT NULL CHAR(1)
					 IS_SYNTHETIC			   --NOT NULL CHAR(1)
					)
					values (
						?,
						1,
						?,
						?,
						?,
						?,
						?,
						?,
						'N'
					)
					''', { PreparedStatement setLink ->
                            setLink.setLong(1, linkId)
                            setLink.setLong(2, (Long) supernode.id)
                            setLink.setLong(3, (Long) subnode.id)
                            setLink.setLong(4, (Long) (linkType ? linkType.nsPart.id : 0L))
                            setLink.setString(5, linkType ? linkType.idPart : null)
                            setLink.setInt(6, (Integer) linkSeq)
                            setLink.setString(7, versioningMethod.name())
                            setLink.executeUpdate()
                        }
                    }

                    // because links belong-to the supernode, we increment the supernode version

                    withQ cnct, 'update tree_node set lock_version = lock_version+1 where id = ?', { PreparedStatement setLink ->
                        setLink.setLong(1, (Long) supernode.id)
                        setLink.executeUpdate()
                    }

                }
                return Link.get(linkId)
            } as Link
        } as Link
    }

/**
 * Checkout a node. This operation does a tree walk and creates a new draft node for each node needing to be checked out.
 * If the node appears more than once in the tree, then this method will fail, because a draft node may only have one supernode.
 * In this situation, you will need to use checkout link for finer-grained control.
 * @param n Node to be checked out
 * @param a Arrangemt in which to check it out
 * @return The checked-out node.
 */

    public Node checkoutNode(Node supernode, Node targetnode) {
        mustHave(supernode: supernode, targetnode: targetnode) {
            clearAndFlush {
                supernode = DomainUtils.refetchNode(supernode)
                targetnode = DomainUtils.refetchNode(targetnode)

                if (DomainUtils.isCheckedIn(supernode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                            supernode,
                            targetnode,
                            ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, supernode)
                    ])
                }

                if (!DomainUtils.isCheckedIn(targetnode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                            supernode,
                            targetnode,
                            ServiceException.makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, targetnode)
                    ])
                }

                if(queryService.countPaths(supernode, targetnode) != 1) {
                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                            supernode,
                            targetnode,
                            ServiceException.makeMsg(Msg.CHECKOUT_MUST_APPEAR_ONCE)
                    ])
                }

                Long newTargetId = null

                doWork(sessionFactory_nsl) { Connection cnct ->
                    // OK! find all the nodes that connect the target node to the selected supernode!
                    // note that we can't filter this by current nodes only

                    create_tree_temp_id cnct

                    // ok. Do the treewalk and extract all entangled links into tree_temp_id
                    withQ cnct, '''
				insert into tree_temp_id(id)
				select id from (
					with recursive linksup(id, supernode_id, subnode_id) as
					(
					    select l.id, l.supernode_id, l.subnode_id from tree_link l 
					    where l.subnode_id = ?
					union all
					    select l.id, l.supernode_id, l.subnode_id 
						from linksup join tree_link l on  l.subnode_id = linksup.supernode_id
					    where linksup.supernode_id <> ?
					),
					linksdown(id, supernode_id, subnode_id) as (
					    select linksup.id, linksup.supernode_id, linksup.subnode_id from linksup where linksup.supernode_id = ?
					union all
					    select linksup.id, linksup.supernode_id, linksup.subnode_id 
						from linksdown join linksup on linksup.supernode_id = linksdown.subnode_id
					)
					select distinct id from linksdown
				) links_of_interest
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, targetnode.id) // starting point
                                qry.setLong(2, supernode.id) // clip the initial search
                                qry.setLong(3, supernode.id) // starting point of the second pass
                                qry.executeUpdate()
                            }

                    // if our link list is empty, then the target node is not in the supernode tree at all
                    withQ cnct, '''select count(*) from tree_temp_id''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                rs.next()
                                int ct = rs.getInt(1)
                                rs.close()
                                if (ct == 0) {
                                    throw new IllegalArgumentException("Node ${targetnode} is not in draft tree ${supernode}")
                                }
                            }

                    // Ok - is the target node (or any of the final nodes above it) in the supernode tree more than once? If so, we can't
                    // do a checkout.
                    // actually, it *is* possible, but it creates draft nodes that have more than one parent node, which complicates the question of
                    // deleting a draft subtree. So we maintain a rule that a draft mnode only ever has one parent node.
                    // in terms of taxonomy, this makes sense. You would not put a genus under two different families in the same classification.

                    withQ cnct, '''
				select l.subnode_id
				from tree_temp_id join tree_link l on l.id = tree_temp_id.id
				group by l.subnode_id
				having count(*) > 1
				''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()

                                if (rs.next()) {
                                    // todo - search for more than one
                                    rs.close()
                                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                                            supernode,
                                            targetnode,
                                            ServiceException.makeMsg(Msg.NODE_APPEARS_IN_MULTIPLE_LOCATIONS_IN, [targetnode, supernode])
                                    ])
                                }
                                rs.close()
                            }

                    // ok. We now know that the path from supernode to targetnode has just the one 'thread' on it, and we no longer need to
                    // filter out distinct nodes.

                    // first, zap anything that is just a link from a draft node to another draft node
                    // checking the subnode suffices

                    withQ cnct, '''
				delete from tree_temp_id
					where (select n.CHECKED_IN_AT_ID from tree_node n join tree_link l on l.subnode_id = n.id where l.id = tree_temp_id.id) is null
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // so now our entire set of links is one link from a draft node, to a persistent node, and all the rest are links
                    // down from one persistent node to the next, down to the target. We are going to need to create new subnodes for all of them.

                    withQ cnct, '''
				update tree_temp_id set id2 = nextval('nsl_global_seq')
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // make a note of the node id that was created for the target node. We need this now because if the target is directly
                    // beneath the bottom draft node, then that link will get updated later on and we will lose track of the id

                    newTargetId = withQresult(cnct, '''
						select id2 from tree_link join tree_temp_id  on tree_temp_id.id = tree_link.id 
						where tree_link.subnode_id = ?''')
                            { PreparedStatement qry ->
                                qry.setLong(1, targetnode.id)
                            } as Long

                    // copy all draft nodes. This means copying tree_node, and copying tree_link where the supernode is tree_node

                    withQ cnct, '''insert into tree_node(
				 ID,
				 internal_type,
				 lock_version,
				 PREV_NODE_ID,
				 NEXT_NODE_ID,
				 TREE_ARRANGEMENT_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 NAME_URI_NS_PART_ID,
				 NAME_URI_ID_PART,
				 TAXON_URI_NS_PART_ID,
				 TAXON_URI_ID_PART,
				 RESOURCE_URI_NS_PART_ID,
				 RESOURCE_URI_ID_PART,
				 LITERAL,
				 NAME_ID,
                 INSTANCE_ID,
				 IS_SYNTHETIC
				)
				select 
				 tree_temp_id.id2, 	-- ID
				 n.internal_type,
				 1,	 				--VERSION
				 n.id, 				--PREV_NODE_ID
				 null, 				--NEXT_NODE_ID
				 ?, 				--TREE_ARRANGEMENT_ID
				 n.TYPE_URI_NS_PART_ID,
				 n.TYPE_URI_ID_PART,
				 n.NAME_URI_NS_PART_ID,
				 n.NAME_URI_ID_PART,
				 n.TAXON_URI_NS_PART_ID,
				 n.TAXON_URI_ID_PART,
				 n.RESOURCE_URI_NS_PART_ID,
				 n.RESOURCE_URI_ID_PART,
				 n.LITERAL,
				 n.NAME_ID,
				 n.INSTANCE_ID,
				 'Y' 				-- IS_SYNTHETIC
				from 
				tree_temp_id 
					join tree_link superlink on tree_temp_id.id = superlink.id
					join tree_node n on superlink.subnode_id = n.id
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, supernode.root.id)
                                qry.executeUpdate()
                            }

                    // copy all links

                    withQ cnct, '''insert into tree_link (
				 ID,
				 lock_version,
				 SUPERNODE_ID,
				 SUBNODE_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 LINK_SEQ,
				 VERSIONING_METHOD,
				 IS_SYNTHETIC		 
				)
				select
				 nextval('nsl_global_seq'), 	--ID
				 1, 							--VERSION
				 tree_temp_id.id2, 				--SUPERNODE_ID
				 l.SUBNODE_ID,
				 l.TYPE_URI_NS_PART_ID,
				 l.TYPE_URI_ID_PART,
				 l.LINK_SEQ,
				 l.VERSIONING_METHOD,
				 'Y' 							--IS_SYNTHETIC		  
				from 
				tree_temp_id 
				join tree_link superlink on tree_temp_id.id = superlink.id
				join tree_link l on superlink.subnode_id = l.supernode_id
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // ok. update the old draft node (there should only be one) to point from the old ode to the newly created node

                    withQ cnct, '''
				update tree_link
				-- set subnode to the new node id for the link
				set subnode_id = (
					select id2 
						from tree_link superlink
						join tree_temp_id on tree_temp_id.id = superlink.id 
					where superlink.subnode_id = tree_link.subnode_id
				)
				-- where we are in the link supernodes but not i the link subnodes
				-- ie: the top draft node
				where supernode_id in (
					select superlink.supernode_id 
					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
				)
				and supernode_id not in (
					select superlink.subnode_id 
					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
				)
				-- and the link in question is to one of the nodes we have made a new node for
				and subnode_id in (
					select superlink.subnode_id 
					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
				)
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // and update the relevant links from the created new nodes
                    withQ cnct, '''
				update tree_link
				-- set subnode to the new node id for the link
				set subnode_id = (
					select id2 from tree_link superlink join tree_temp_id on tree_temp_id.id = superlink.id
					where  superlink.subnode_id = tree_link.subnode_id
				)
				-- where the supernode is any of our newly created nodes
				where supernode_id in (
					select id2 from tree_temp_id
				)
				-- and the link in question is to one of the nodes we have made a new node for
				and subnode_id in (
					select superlink.subnode_id 
					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
				)
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // ok, and mark that top draft node as having been touched.
                    withQ cnct, '''update tree_node
				set lock_version = lock_version+1
				where id in (
					select superlink.supernode_id 
					from tree_temp_id 
					join tree_link superlink on tree_temp_id.id = superlink.id
				)
				and id not in (
					select superlink.subnode_id 
					from tree_temp_id
					join tree_link superlink on tree_temp_id.id = superlink.id
				)
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }
                }

                return Node.get(newTargetId)
            } as Node
        } as Node
    }

/**
 * This action checks out multiple nodes simultaneously. It only works on current nodes, which may
 * cause difficulty for trees that include fixed links.
 *
 *
 * @param supernode
 * @param targetNodes
 */
    public void massCheckout(Node supernode, Collection<Node> targetNodes) {
        mass_checkout_impl(supernode, targetNodes, false);
    }

/**
 * This action checks out multiple nodes simultaneously. It only works on current nodes, which may
 * cause difficulty for trees that include fixed links. All subnodes are checked out,
 * meaning that this method is the core of a "copy tree" operation.
 * @param supernode
 * @param targetNodes
 */


    public void massCheckoutWithSubnodes(Node supernode, Collection<Node> targetNodes) {
        mass_checkout_impl(supernode, targetNodes, true);

    }

/*
 * This code is mostly a copy-and-paste of the single node checkout. I am not 100% sure that it is correct as I have not
 * mentally walked through it yet.
 */

    private void mass_checkout_impl(Node supernode, Collection<Node> targetNodes, boolean withSubnodes) {
        mustHave(supernode: supernode) {
            clearAndFlush {
                if (!targetNodes) return;

                supernode = DomainUtils.refetchNode(supernode)
                targetNodes = DomainUtils.refetchCollection(targetNodes)

                if (DomainUtils.isCheckedIn(supernode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                            supernode,
                            targetnode,
                            ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, supernode)
                    ])
                }

                // TODO: accumulate these errors in a bucket.

                targetNodes.each { Node targetnode ->
                    if (!DomainUtils.isCheckedIn(targetnode)) {
                        ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                                supernode,
                                targetnode,
                                ServiceException.makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, targetnode)
                        ])
                    }
                }


                doWork(sessionFactory_nsl) { Connection cnct ->
                    // OK! find all the nodes that connect the target node to the selected supernode!
                    // note that we can't filter this by current nodes only

                    create_tree_temp_id cnct
                    create_tree_temp_id2 cnct
                    create_link_treewalk cnct

                    // ok. First job - load all the target nodes into tree_temp_id2

                    log.debug "putting ${targetNodes.size()} into tree_temp_id2"

                    withQ cnct, 'insert into tree_temp_id2(id) select ?', { PreparedStatement stmt ->
                        targetNodes.each { Node targetNode ->
                            stmt.setLong(1, targetNode.id)
                            stmt.executeUpdate();
                        }
                    }

                    // Now treewalk to find all entangled LINKS

                    log.debug "treewalk to find entangled links"
                    Integer ct = null

                    withQ cnct, '''
				insert into link_treewalk(id, supernode_id, subnode_id)
				select l.id, l.supernode_id, l.subnode_id 
						from tree_temp_id2
					    	join tree_link l  on tree_temp_id2.id = l.subnode_id
							join tree_node on tree_temp_id2.id = tree_node.id
						where tree_node.replaced_at_id is null''',
                            { PreparedStatement qry ->
                                ct = qry.executeUpdate()
                            }

                    log.debug "${ct} initial nodes"

                    withQ cnct, '''
				insert into link_treewalk(id, supernode_id, subnode_id)
					    select distinct l.id, l.supernode_id, l.subnode_id 
						from link_treewalk 
							join tree_link l on  l.subnode_id = link_treewalk.supernode_id
							join tree_node on l.supernode_id = tree_node.id
							left outer join link_treewalk already_present on l.id = already_present.id
					    where link_treewalk.supernode_id <> ?
							and tree_node.replaced_at_id is null
							and already_present.id is null
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, supernode.id) // clip the initial search
                                for (; ;) {
                                    ct = qry.executeUpdate()
                                    log.debug "found ${ct} superlinks"
                                    if (ct == 0) break;
                                }
                            }

                    log.debug "filtering back down"

                    // I am not using a loop for this as I expect it to be far quicker

                    withQ cnct, '''
				insert into tree_temp_id(id)
				select id from (
					with recursive linksdown(id, supernode_id, subnode_id) as (
					    select id, supernode_id, subnode_id from link_treewalk where supernode_id = ?
					union all
					    select link_treewalk.id, link_treewalk.supernode_id, link_treewalk.subnode_id 
						from linksdown join link_treewalk on link_treewalk.supernode_id = linksdown.subnode_id
					)
					select distinct id from linksdown
				) links_of_interest
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, supernode.id) // starting point of the second pass
                                qry.executeUpdate()
                            }

                    // mass checkout does not test to determine if any links are entangled at all, and will silently do nothing.

                    // Ok - is any node in the supernode tree more than once? If so, we can't
                    // do a checkout.
                    // actually, it *is* possible, but it creates draft nodes that have more than one parent node, which complicates the question of
                    // deleting a draft subtree. So we maintain a rule that a draft mnode only ever has one parent node.
                    // in terms of taxonomy, this makes sense. You would not put a genus under two different families in the same classification.

                    log.debug "checking that all nodes ar only entangled via one link"

                    withQ cnct, '''
				select l.subnode_id
				from tree_temp_id join tree_link l on l.id = tree_temp_id.id
				group by l.subnode_id
				having count(*) > 1
				''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()

                                if (rs.next()) {
                                    // todo - search for more than one
                                    rs.close()
                                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutNode, [
                                            supernode,
                                            targetnode,
                                            ServiceException.makeMsg(Msg.NODE_APPEARS_IN_MULTIPLE_LOCATIONS_IN, [targetnode, '(TODO: multipl nodes)'])
                                    ])
                                }
                                rs.close()
                            }

                    // ok. We now know that each path from supernode to targetnode has just the one 'thread' on it, and we no longer need to
                    // filter out distinct nodes.

                    // first, zap anything that is just a link from a draft node to another draft node
                    // checking the subnode suffices

                    log.debug "pruning existing draft supertrees"

                    withQ cnct, '''
				delete from tree_temp_id
					where (select n.CHECKED_IN_AT_ID from tree_node n join tree_link l on l.subnode_id = n.id where l.id = tree_temp_id.id) is null
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // NEW Addition 2015-06-12: walk subtree.

                    if (withSubnodes) {
                        log.debug "treewalking to get all subnodes"
                        // tree_temp_id2 contains our initial nodes. I will walk down from there.

                        withQ cnct, '''
insert into tree_temp_id
select link_id from (
  with recursive treelinks(link_id) as (
      select tree_link.id as link_id from tree_temp_id2 join tree_link on tree_temp_id2.id = tree_link.supernode_id
    union all
      select tl_sublink.id as link_id
        from treelinks
        join tree_link as tl_treelink on treelinks.link_id = tl_treelink.id
        join tree_link as tl_sublink on tl_treelink.subnode_id = tl_sublink.supernode_id
  )
  select distinct link_id
  from treelinks
  where link_id not in (
    select id from tree_temp_id
  )
) as subq
				''',
                                { PreparedStatement qry ->
                                    qry.executeUpdate()
                                }

                    }

                    // so now our entire set of links is a set of disjoint trees made up of persistent nodes each up to one draft supernode.
                    // time to create new nodes.
                    // first, assign the ids

                    log.debug "assigning new node ids"

                    withQ cnct, '''
				update tree_temp_id set id2 = nextval('nsl_global_seq')
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    log.debug "creating new nodes"

                    withQ cnct, '''insert into tree_node(
				 ID,
				 internal_type,
				 lock_version,
				 PREV_NODE_ID,
				 NEXT_NODE_ID,
				 TREE_ARRANGEMENT_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 NAME_URI_NS_PART_ID,
				 NAME_URI_ID_PART,
				 TAXON_URI_NS_PART_ID,
				 TAXON_URI_ID_PART,
				 RESOURCE_URI_NS_PART_ID,
				 RESOURCE_URI_ID_PART,
				 LITERAL,
				 NAME_ID,
				 INSTANCE_ID,
				 IS_SYNTHETIC	
				)
				select 
				 tree_temp_id.id2, 	-- ID
				 n.internal_type,
				 1,	 				--VERSION
				 n.id, 				--PREV_NODE_ID
				 null, 				--NEXT_NODE_ID
				 ?, 				--TREE_ARRANGEMENT_ID
				 n.TYPE_URI_NS_PART_ID,
				 n.TYPE_URI_ID_PART,
				 n.NAME_URI_NS_PART_ID,
				 n.NAME_URI_ID_PART,
				 n.TAXON_URI_NS_PART_ID,
				 n.TAXON_URI_ID_PART,
				 n.RESOURCE_URI_NS_PART_ID,
				 n.RESOURCE_URI_ID_PART,
				 n.LITERAL,
                 n.NAME_ID,
                 n.INSTANCE_ID,
				 'Y' 				-- IS_SYNTHETIC
				from 
				tree_temp_id 
					join tree_link superlink on tree_temp_id.id = superlink.id
					join tree_node n on superlink.subnode_id = n.id
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, supernode.root.id)
                                qry.executeUpdate()
                            }

                    // copy all links

                    log.debug "copying links"

                    withQ cnct, '''insert into tree_link (
				 ID,
				 lock_version,
				 SUPERNODE_ID,
				 SUBNODE_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 LINK_SEQ,
				 VERSIONING_METHOD,
				 IS_SYNTHETIC		 
				)
				select
				 nextval('nsl_global_seq'), 	--ID
				 1, 							--VERSION
				 tree_temp_id.id2, 				--SUPERNODE_ID
				 l.SUBNODE_ID,
				 l.TYPE_URI_NS_PART_ID,
				 l.TYPE_URI_ID_PART,
				 l.LINK_SEQ,
				 l.VERSIONING_METHOD,
				 'Y' 							--IS_SYNTHETIC		  
				from 
				tree_temp_id 
				join tree_link superlink on tree_temp_id.id = superlink.id
				join tree_link l on superlink.subnode_id = l.supernode_id
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // ok. update all the draft nodes that are the roots of our set of disjoint trees
                    // to point from the old ode to the newly created node

                    log.debug "update draft tree graft points"

//			withQ cnct, '''
//				update tree_link
//				-- set subnode to the new node id for the link
//				set subnode_id = (
//					select id2 
//						from tree_link superlink
//						join tree_temp_id on tree_temp_id.id = superlink.id 
//					where superlink.subnode_id = tree_link.subnode_id
//				)
//				-- where we are in the link supernodes but not i the link subnodes
//				-- ie: top draft nodes
//				where supernode_id in (
//					select superlink.supernode_id 
//					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
//				)
//				and supernode_id not in (
//					select superlink.subnode_id 
//					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
//				)
//				-- and the link in question is to one of the nodes we have made a new node for
//				and subnode_id in (
//					select superlink.subnode_id 
//					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
//				)
//				''',
//			{ PreparedStatement qry ->
//				qry.executeUpdate()
//			}

                    withQ cnct, '''
				update tree_link
				-- set subnode to the new node id for the link
				set subnode_id = (
					select id2 
						from tree_link superlink
						join tree_temp_id on tree_temp_id.id = superlink.id 
					where superlink.subnode_id = tree_link.subnode_id
				)
				where id in (select id from tree_temp_id)
				and (select checked_in_at_id from tree_node where tree_node.id = tree_link.supernode_id) is null
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    log.debug "resolve created checked out node interlinks"

                    // and update the relevant links from the created new nodes
                    withQ cnct, '''
				update tree_link
				-- set subnode to the new node id for the link
				set subnode_id = (
					select id2 from tree_link superlink join tree_temp_id on tree_temp_id.id = superlink.id
					where  superlink.subnode_id = tree_link.subnode_id
				)
				-- where the supernode is any of our newly created nodes
				where supernode_id in (
					select id2 from tree_temp_id
				)
				-- and the link in question is to one of the nodes we have made a new node for
				and subnode_id in (
					select superlink.subnode_id 
					from tree_temp_id join tree_link superlink on tree_temp_id.id = superlink.id
				)
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    log.debug "mark graft points as touched"

                    // ok, and mark that top draft node as having been touched.
                    withQ cnct, '''update tree_node
				set lock_version = lock_version+1
				where id in (
					select superlink.supernode_id 
					from tree_temp_id 
					join tree_link superlink on tree_temp_id.id = superlink.id
				)
				and id not in (
					select superlink.subnode_id 
					from tree_temp_id
					join tree_link superlink on tree_temp_id.id = superlink.id
				)
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    log.debug "done mass checkout"
                }
            }
        }
    }

/**
 * Looks for a node in a tree, finding either the node itself or a checked-out version of the node. This is a common
 * thing needing to be done.
 * @deprecated moved to QueryService
 */

    @SuppressWarnings("GroovyUnusedDeclaration")
    Link findNodeCurrentOrCheckedout(Node supernode, Node findNode) {
        return queryService.findNodeCurrentOrCheckedout(supernode, findNode)
    }

/**
 * Checkout a node under a link. The supernode must be a draft node, and the subnode a persistent node.
 * @param link
 * @return
 */

    public void checkoutLink(Link link) {
        mustHave(link: link) {
            clearAndFlush {
                link = DomainUtils.refetchLink(link)

                if (DomainUtils.isCheckedIn(link.supernode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutLink, [link, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, link.supernode)])
                }

                if (!DomainUtils.isCheckedIn(link.subnode)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.checkoutLink, [link, ServiceException.makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, link.subnode)])
                }

                doWork(sessionFactory_nsl) { Connection cnct ->
                    Long newId = queryService.getNextval()

                    withQ cnct, '''insert into tree_node(
				 ID,
				 lock_version,
				 PREV_NODE_ID,
				 NEXT_NODE_ID,
				 TREE_ARRANGEMENT_ID,
				INTERNAL_TYPE,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 NAME_URI_NS_PART_ID,
				 NAME_URI_ID_PART,
				 TAXON_URI_NS_PART_ID,
				 TAXON_URI_ID_PART,
				 RESOURCE_URI_NS_PART_ID,
				 RESOURCE_URI_ID_PART,
				 LITERAL,
                 NAME_ID,
                 INSTANCE_ID,
				 IS_SYNTHETIC
				)
				select 
				 ?, 	-- ID
				 1,	 				--VERSION
				 n.id, 				--PREV_NODE_ID
				 null, 				--NEXT_NODE_ID
				 ?, 				--TREE_ARRANGEMENT_ID
				 n.INTERNAL_TYPE,
				 n.TYPE_URI_NS_PART_ID,
				 n.TYPE_URI_ID_PART,
				 n.NAME_URI_NS_PART_ID,
				 n.NAME_URI_ID_PART,
				 n.TAXON_URI_NS_PART_ID,
				 n.TAXON_URI_ID_PART,
				 n.RESOURCE_URI_NS_PART_ID,
				 n.RESOURCE_URI_ID_PART,
				 n.LITERAL,
				 n.NAME_ID,
				 n.INSTANCE_ID,
				 'Y' 				-- IS_SYNTHETIC
				from tree_node n
				where n.id = ?
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, newId)
                                qry.setLong(2, link.supernode.root.id)
                                qry.setLong(3, link.subnode.id)
                                qry.executeUpdate()
                            }

                    // copy all links

                    withQ cnct, '''insert into tree_link (
				 ID,
				 lock_version,
				 SUPERNODE_ID,
				 SUBNODE_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 LINK_SEQ,
				 VERSIONING_METHOD,
				 IS_SYNTHETIC		 
				)
				select
				 nextval('nsl_global_seq'), 	--ID
				 1, 							--VERSION
				 ?, 				--SUPERNODE_ID
				 l.SUBNODE_ID,
				 l.TYPE_URI_NS_PART_ID,
				 l.TYPE_URI_ID_PART,
				 l.LINK_SEQ,
				 l.VERSIONING_METHOD,
				 'Y' 							--IS_SYNTHETIC		  
				from 
				tree_link l
				where 
				l.supernode_id = ?
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, newId)
                                qry.setLong(2, link.subnode.id)
                                qry.executeUpdate()
                            }

                    // and update the link to point at the new node

                    withQ cnct, '''
				update tree_link set subnode_id = ? where id = ?
			''', { PreparedStatement qry ->
                        qry.setLong(1, newId)
                        qry.setLong(2, link.id)
                        qry.executeUpdate()
                    }

                }
            }
        }
    }

/**
 * Undo a checkout of a node
 * @param n A node that is a checked-out draft node
 * @return The original node.
 */
    public Node undoCheckout(Node n) {
        mustHave(Node: n) {
            clearAndFlush {
                n = DomainUtils.refetchNode(n)

                if (DomainUtils.isCheckedIn(n)) {
                    ServiceException.raise ServiceException.makeMsg(Msg.undoCheckout, [n, ServiceException.makeMsg(Msg.PERSISTENT_NODE_NOT_PERMITTED, n)])
                }

                if (!n.prev) {
                    ServiceException.raise ServiceException.makeMsg(Msg.undoCheckout, [n, ServiceException.makeMsg(Msg.NODE_HAS_NO_PREV, n)])
                }

                if (n.root.node == n) {
                    ServiceException.raise ServiceException.makeMsg(Msg.undoCheckout, [n, ServiceException.makeMsg(Msg.ROOT_NODE_NOT_PERMITTED, n)])
                }

                if (n.supLink.size() != 1) {
                    ServiceException.raise ServiceException.makeMsg(Msg.undoCheckout, [n, ServiceException.makeMsg(Msg.NODE_HAS_MORE_THAN_ONE_SUPERNODE, n)])
                }

                Node prevNode = n.prev // the value that We are going to return
                Link currentLink = DomainUtils.getDraftNodeSuperlink(n)

                doWork(sessionFactory_nsl) { Connection cnct ->
                    // a link belngs to its supernode, so increment the supernode
                    withQ cnct, '''update tree_node set lock_version=lock_version+1 where id = ?''',
                            { PreparedStatement qry ->
                                qry.setLong(1, currentLink.supernode.id)
                                qry.executeUpdate()
                            }
                    withQ cnct, '''update tree_link set lock_version=lock_version+1, subnode_id=? where id = ?''',
                            { PreparedStatement qry ->
                                qry.setLong(1, prevNode.id)
                                qry.setLong(2, currentLink.id)
                                qry.executeUpdate()
                            }
                }
                delete_node_tree(n.id)

                return prevNode
            } as Node
        } as Node
    }

/**
 * Move all *final* *current* nodes in "from" to "to".
 * TODO - this opertion needs to be looked at when we restrict our notion of "orphan" to "orphan in the same tree".
 * @param from
 * @param to
 * @return
 */

    public Integer moveFinalNodesFromTreeToTree(Arrangement from, Arrangement to) {
        mustHave(from: from, to: to) {
            clearAndFlush {
                from = DomainUtils.refetchArrangement(from)
                to = DomainUtils.refetchArrangement(to)

                if (from.equals(to)) {
                    throw new IllegalArgumentException("from == to")
                }

                Node.executeUpdate('update Node set root = :to where root = :from and checkedInAt is not null and next is null', [to: to, from: from])
            } as Integer
        } as Integer
    }

/**
 * Unconditionally move all the nodes. The 'from' arrangement may be deleted after this.
 * TODO - this opertion needs to be looked at when we restrict our notion of "orphan" to "orphan in the same tree".
 * @param from
 * @param to
 * @return
 */

    @SuppressWarnings("GroovyUnusedDeclaration")
    public Integer moveAllNodesFromTreeToTree(Arrangement from, Arrangement to) {
        mustHave(from: from, to: to) {
            clearAndFlush {
                from = DomainUtils.refetchArrangement(from)
                to = DomainUtils.refetchArrangement(to)

                if (from.equals(to)) {
                    throw new IllegalArgumentException("from == to")
                }

                return doWork(sessionFactory_nsl) { Connection cnct ->
                    // a link belngs to its supernode, so increment the supernode
                    withQ(cnct, '''update tree_node set tree_arrangement_id = ?
				where tree_arrangement_id = ?''')
                            { PreparedStatement qry ->
                                qry.setLong(1, to.id)
                                qry.setLong(2, from.id)
                                return qry.executeUpdate()
                            } as Integer
                } as Integer
            } as Integer
        } as Integer
    }

    /**
     * Ok, this is a bit of a wart. It's a big lumpy operation that is specific to one particular process.
     * It's being put here in basicOperationsService because it's going to involve quite a bit of SQL.
     *
     * It's like this:
     *
     * When checking in workspace node back into the tree that it came from, the workspace may contain nodes
     * that belong to other trees. Those nodes should be replaced in the workspace with copies. Those copies will be
     * moved into the target tree by moveNodeSubtreeIntoArrangement.
     *
     * So:
     * recursively descend into the links.
     * find all links where the subnode is not in the workspace AND not in the workspace target tree
     * make copies of all of those subnodes (not that we can stop recursing when we encounter the target tree)
     * everyone of the links where the supernode is in the draft tree, update it to point at the new node
     * every one of the new nodes, if it has a link that is a copy of one of the found links
     *     then update that links subnode to point at the newly created node
     * These two steps re-link the tree.
     *
     * @param e
     * @param n
     */

    public void createCopiesOfAllNonTreeNodes(Event e, Node n) {
        mustHave(e: e, node: n) {
            // these messages are unhelpful to the user because we should not have gotten this far at all
            if(DomainUtils.isCheckedIn(n)) {
                throw new IllegalArgumentException('DomainUtils.isCheckedIn(n)')
            }

            if(n.root.arrangementType != ArrangementType.U) {
                throw new IllegalArgumentException('n.root.arrangementType != ArrangementType.U')
            }
            if(!n.prev) {
                throw new IllegalArgumentException('!n.prev')
            }
            if(!DomainUtils.isCurrent(n.prev)) {
                throw new IllegalArgumentException('!DomainUtils.isCurrent(n.prev)')
            }
            if(n.prev.root.arrangementType != ArrangementType.P) {
                throw new IllegalArgumentException('n.prev.root.arrangementType != ArrangementType.P')
            }

            // ok. let's do this.

            doWork(sessionFactory_nsl) { Connection cnct ->
                withQ cnct, '''
                    create temporary table if not exists link_treewalk_replace_subnode (
                        id bigint primary key,
                        supernode_id bigint not null,
                        subnode_id bigint not null,
                        subnode_tree_arrangement_id bigint not null,
                        new_subnode_id bigint
                    )
                    on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

                withQ cnct, '''delete from link_treewalk_replace_subnode''', { PreparedStatement qry -> qry.executeUpdate() }

                withQ cnct, '''
                    insert into link_treewalk_replace_subnode(id, supernode_id, subnode_id, subnode_tree_arrangement_id)
                    select id, supernode_id, subnode_id, tree_arrangement_id from (
                        with recursive
                            treewalk as (
                              select tree_link.id, tree_link.supernode_id, tree_link.subnode_id, tree_node.tree_arrangement_id
                                  from tree_link join tree_node on tree_link.subnode_id = tree_node.id
                                  where tree_link.supernode_id = ?
                                  and tree_node.tree_arrangement_id <> ?
                               union all
                              select  tree_link.id, tree_link.supernode_id, tree_link.subnode_id, tree_node.tree_arrangement_id
                                  from treewalk
                                  join tree_link on treewalk.subnode_id = tree_link.supernode_id
                                  join tree_node on tree_link.subnode_id = tree_node .id
                                  where tree_node.tree_arrangement_id <> ?
                            )
                        select id, supernode_id, subnode_id, tree_arrangement_id
                            from treewalk
                            where treewalk.tree_arrangement_id <> ?
                    ) withq
                ''', { PreparedStatement qry ->
                    qry.setLong(1, n.id) // start walk
                    qry.setLong(2, n.prev.root.id) // filter out source nodes on root
                    qry.setLong(3, n.prev.root.id) // filter out source nodes on walk
                    qry.setLong(4, n.root.id) // filter out draft tree after walk
                    qry.executeUpdate()
                }

                withQ cnct, '''
                    update link_treewalk_replace_subnode w set new_subnode_id = nextval('nsl_global_seq')
                ''', { PreparedStatement qry ->
                    qry.executeUpdate()
                }

                // copy all draft nodes. This means copying tree_node, and copying tree_link where the supernode is tree_node

                withQ cnct, '''insert into tree_node(
				 ID,
				 internal_type,
				 lock_version,
				 PREV_NODE_ID,
				 NEXT_NODE_ID,
				 TREE_ARRANGEMENT_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 NAME_URI_NS_PART_ID,
				 NAME_URI_ID_PART,
				 TAXON_URI_NS_PART_ID,
				 TAXON_URI_ID_PART,
				 RESOURCE_URI_NS_PART_ID,
				 RESOURCE_URI_ID_PART,
				 LITERAL,
				 NAME_ID,
                 INSTANCE_ID,
				 IS_SYNTHETIC
				)
				select
				 w.new_subnode_id, 	-- ID
				 n.internal_type,
				 1,	 				--VERSION
				 n.id, 				--PREV_NODE_ID
				 null, 				--NEXT_NODE_ID
				 ?, 				--TREE_ARRANGEMENT_ID
				 n.TYPE_URI_NS_PART_ID,
				 n.TYPE_URI_ID_PART,
				 n.NAME_URI_NS_PART_ID,
				 n.NAME_URI_ID_PART,
				 n.TAXON_URI_NS_PART_ID,
				 n.TAXON_URI_ID_PART,
				 n.RESOURCE_URI_NS_PART_ID,
				 n.RESOURCE_URI_ID_PART,
				 n.LITERAL,
				 n.NAME_ID,
				 n.INSTANCE_ID,
				 'Y' 				-- IS_SYNTHETIC
				from
				link_treewalk_replace_subnode w
					join tree_node n on w.subnode_id = n.id
				''',
                        { PreparedStatement qry ->
                            qry.setLong(1, n.root.id)
                            qry.executeUpdate()
                        }

                // copy all sublinks
                // if a sublink is one of the links for which we are replacing the subnode, then use the new subnode
                // as the link subnode. Otherwise, just copy the link as is.


                withQ cnct, '''insert into tree_link (
				 ID,
				 lock_version,
				 SUPERNODE_ID,
				 SUBNODE_ID,
				 TYPE_URI_NS_PART_ID,
				 TYPE_URI_ID_PART,
				 LINK_SEQ,
				 VERSIONING_METHOD,
				 IS_SYNTHETIC
				)
				select
				 nextval('nsl_global_seq'), 	--ID
				 1, 							--VERSION
				 w.supernode_id, 				--SUPERNODE_ID
				 case when ww.id is null then l.SUBNODE_ID else ww.new_subnode_id end ,
				 l.TYPE_URI_NS_PART_ID,
				 l.TYPE_URI_ID_PART,
				 l.LINK_SEQ,
				 l.VERSIONING_METHOD,
				 'Y' 							--IS_SYNTHETIC
				from
				link_treewalk_replace_subnode w
				join tree_link l on w.subnode_id = l.supernode_id
				left join link_treewalk_replace_subnode ww on l.id = ww.id
				''',
                        { PreparedStatement qry ->
                            qry.executeUpdate()
                        }

                // finally, update any links whose supernode is one of our draft nodes

                withQ cnct, '''
                    update tree_link
                    set subnode_id = (
                        select new_subnode_id
                        from link_treewalk_replace_subnode w
                        where tree_link.id = w.id
                    )
                    where id in
                    (select w.id
                    from link_treewalk_replace_subnode w
                    join tree_node on w.supernode_id = tree_node.id
                    where tree_node.tree_arrangement_id = ?)
                ''',
                { PreparedStatement qry ->
                    qry.setLong(1, n.root.id)
                    qry.executeUpdate()
                }
            }
        }
    }

    /*
    Move nodes from one tree to another starting from a given parent node.
     */

    public void moveNodeSubtreeIntoArrangement(Arrangement from, Arrangement to, Node node) {
        mustHave(from: from, to: to, node: node) {
            clearAndFlush {
                from = DomainUtils.refetchArrangement(from)
                to = DomainUtils.refetchArrangement(to)
                node = DomainUtils.refetchNode(node)

                if (from.equals(to)) {
                    throw new IllegalArgumentException("from == to")
                }

                if (!node.root.equals(from)) {
                    throw new IllegalArgumentException("node.root != from")
                }

                return doWork(sessionFactory_nsl) { Connection cnct ->
                    // a link belngs to its supernode, so increment the supernode
                    withQ(cnct, '''
update tree_node
set tree_arrangement_id = ?
where tree_node.id in (
  with recursive n as (
    select id from tree_node nn where id = ? and tree_arrangement_id = ?
    union all
    select nn.id
    from n
      join tree_link l on n.id = l.supernode_id
      join tree_node nn on l.subnode_id = nn.id
      where nn.tree_arrangement_id = ?
  )
  select id from n
)
''')
                            { PreparedStatement qry ->
                                qry.setLong(1, to.id)
                                qry.setLong(2, node.id)
                                qry.setLong(3, from.id)
                                qry.setLong(4, from.id)
                                return qry.executeUpdate()
                            } as Integer
                }
            }
        }

    }

// physical deletion of a node tree. No checks are done.

    private void delete_node_tree(long node_id) {
        doWork(sessionFactory_nsl) { Connection cnct ->
            create_tree_temp_id cnct

            withQ cnct, '''
									insert into tree_temp_id(id)
									select n.id from (
										with recursive nn(id) as (
											select tree_node.id from tree_node where id = ?
										union all
											select tree_node.id
										from
											nn
											join tree_link on nn.id = tree_link.supernode_id
											join tree_node on tree_link.subnode_id = tree_node.id
										where
											tree_node.CHECKED_IN_AT_ID is null
										)
										select id from nn
									) n
									''', { PreparedStatement qry ->
                qry.setLong(1, node_id)
                qry.executeUpdate()
            }

            withQ cnct, 'delete from tree_link where supernode_id in (select id from tree_temp_id)', { PreparedStatement qry -> qry.executeUpdate() }
            withQ cnct, 'delete from tree_link where subnode_id in (select id from tree_temp_id)', { PreparedStatement qry -> qry.executeUpdate() }
            withQ cnct, 'delete from tree_node where id in (select id from tree_temp_id)', { PreparedStatement qry -> qry.executeUpdate() }
        }
    }

/*
 * This code is copy/pasted from tree operations controller and probably should be in a utility class
 */

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
