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

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Node
import grails.transaction.Transactional
import org.hibernate.SessionFactory

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

import static ServiceException.makeMsg
import static ServiceException.raise
import static au.org.biodiversity.nsl.tree.HibernateSessionUtils.*

/**
 * This service provides the versioning operations that manipulate the tree. It sits directly on top of the database and 
 * calls native SQL.
 * Most methods on this service do a flush() and then a refresh() of the various entities they work with,
 * so before calling anything here all hibernate entities should be in good order.
 * Most of the methods here leave hibernate in an incorrect state. Be sure to evict and refresh everything. After
 * you are done working with the data using these methods.
 * @author ibis
 */

/*
 * Basically, these methods could happily go into BasicOperationsService. However, they are likely to be bug and cumbersome.
 *
 */

@Transactional(rollbackFor = [ServiceException])
class VersioningService {
    static datasource = 'nsl'

    //private static final  Log log = LogFactory.getLog(VersioningService.class)

    SessionFactory sessionFactory_nsl
    BasicOperationsService basicOperationsService

    /**
     * Execute a versioning operation
     * @param params ignoreOrphans: boolean - skip the check for orphans
     * @param replace - replace each of the key nodes with each of the values, as a single operation
     *
     */

    void performVersioning(Map params = [:], Event e, Map<Node, Node> replace, Arrangement homeArrangement) {
        if(!replace) return;

        mustHave(event: e, homeArrangement: homeArrangement) {
            clearAndFlush {
                e = DomainUtils.refetchEvent(e)
                homeArrangement = DomainUtils.refetchArrangement(homeArrangement)
                replace = DomainUtils.refetchMap(replace)
                params = DomainUtils.refetchMap(params)

                if (DomainUtils.isEndTree(homeArrangement)) {
                    throw new IllegalArgumentException('cannot use end tree as home arrangment')
                }

                if (homeArrangement.namespace != e.namespace) {
                    throw new IllegalArgumentException('home arranagement namespace must match event namespace')
                }

                replace.keySet.each { Node n ->
                    if (!n) throw new IllegalArgumentException('cannot replace a null node')
                    if (DomainUtils.isEndNode(n)) {
                        throw new IllegalArgumentException('cannot replace the end node')
                    }
                    if (n == n.root.node) {
                        throw new IllegalArgumentException('cannot replace the root node of a classification')
                    }
                    if (!DomainUtils.isCheckedIn(n)) {
                        throw new IllegalArgumentException('cannot replace a draft node')
                    }
                    if(e.namespace != n.root.namespace) {
                        throw new IllegalArgumentException('event namespace must match replaced node namespace')
                    }
                }

                replace.values.each { Node n ->
                    if (!n) throw new IllegalArgumentException('cannot use null as a replacement')
                    if (n == n.root.node) {
                        throw new IllegalArgumentException('cannot use the root node of a classification as a replacement')
                    }
                    if (!DomainUtils.isCheckedIn(n)) {
                        throw new IllegalArgumentException('cannot use a draft node as a replacement')
                    }
                    if (replace.containsKey(n)) {
                        throw new IllegalArgumentException('cannot replace a node with a node that is itself being replaced')
                    }
                    if(n.root.namespace != null && e.namespace != n.root.namespace) {
                        throw new IllegalArgumentException('event namespace must match replaced node namespace')
                    }
                }

                // and now, things become interesting.

                doWork sessionFactory_nsl, { Connection cnct ->
                    create_tree_replacements cnct
                    create_tree_syn_replacements cnct

                    log.debug "putting ${replace.size} keys into tree_replacements"

                    withQ cnct, 'insert into tree_replacements values (?,?)',
                            { PreparedStatement qry ->
                                replace.each { k, v ->
                                    qry.setLong(1, k.id)
                                    qry.setLong(2, v.id)
                                    qry.executeUpdate()
                                }
                            }

                    if (log.isDebugEnabled()) {
                        log.debug('tree replacements ---------------------')
                        withQ cnct, "select id, id2 from tree_replacements",
                                { PreparedStatement qry ->
                                    ResultSet rs = qry.executeQuery()
                                    while (rs.next()) {
                                        log.debug("replacing ${rs.getObject(1)} with ${rs.getObject(2)}")
                                    }
                                    rs.close()
                                }

                        log.debug('---------------------------------------')
                    }

                    // basic argument checks.

                    boolean versioningValidityCheckFailed = false // just because I don't trust you
                    List<Message> versioningValidityCheckFailures = []

                    log.debug "looking for non-current nodes being replaced"

                    withQ cnct, '''select r.id, r.id2 from tree_replacements r, tree_node n
					where r.id = n.id
					and (n.next_node_id is not null or n.CHECKED_IN_AT_ID is null) ''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                try {
                                    while (rs.next()) {
                                        versioningValidityCheckFailed = true
                                        Node r = Node.get(rs.getLong('id')).refresh()
                                        Node r2 = Node.get(rs.getLong('id2')).refresh()
                                        if (r.next) {
                                            versioningValidityCheckFailures << makeMsg(Msg.CANNOT_REPLACE_NODE, [r, r2, makeMsg(Msg.OLD_NODE_NOT_PERMITTED, [r])])
                                        }
                                        if (!DomainUtils.isCheckedIn(r)) {
                                            versioningValidityCheckFailures << makeMsg(Msg.CANNOT_REPLACE_NODE, [r, r2, makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, [r])])
                                        }
                                    }
                                }
                                finally {
                                    rs.close()
                                }
                            }

                    log.debug "looking for non-current replacement nodes"

                    withQ cnct, '''select r.id, r.id2 from tree_replacements r, tree_node n where r.id2 = n.id
					and (n.next_node_id is not null or n.CHECKED_IN_AT_ID is null) ''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                try {
                                    while (rs.next()) {
                                        versioningValidityCheckFailed = true
                                        Node r = Node.get(rs.getLong('id')).refresh()
                                        Node r2 = Node.get(rs.getLong('id2')).refresh()
                                        if (r2.next) {
                                            versioningValidityCheckFailures << makeMsg(Msg.CANNOT_USE_NODE_AS_A_REPLACEMENT, [r, r2, makeMsg(Msg.OLD_NODE_NOT_PERMITTED, [r2])])
                                        }
                                        if (!DomainUtils.isCheckedIn(r2)) {
                                            versioningValidityCheckFailures << makeMsg(Msg.CANNOT_USE_NODE_AS_A_REPLACEMENT, [r, r2, makeMsg(Msg.DRAFT_NODE_NOT_PERMITTED, [r2])])
                                        }
                                    }
                                }
                                finally {
                                    rs.close()
                                }
                            }

                    // first check - you cannot replace a node with a node being replaced

                    log.debug "looking for replacement nodes being replaced"

                    withQ cnct, 'select a.id, a.id2 from tree_replacements a, tree_replacements b where a.id2 = b.id',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                try {
                                    while (rs.next()) {
                                        versioningValidityCheckFailed = true
                                        Node a1 = Node.get(rs.getLong(1))
                                        Node a2 = Node.get(rs.getLong(2))
                                        versioningValidityCheckFailures << makeMsg(Msg.CANNOT_REPLACE_NODE_WITH_NODE_BEING_REPLACED, [a1, a2])
                                    }
                                }
                                finally {
                                    rs.close()
                                }
                            }

                    // we exit at this point before doing other checks, because the work that we have to do to do the other checks makes
                    // no sense if there is a problem at this layer.

                    if (versioningValidityCheckFailed) {
                        Message m = makeMsg(Msg.performVersioning)
                        m.nested << versioningValidityCheckFailures
                        raise m
                    }

                    // ok tree walk to get all the nodes that we will need to replace

                    log.debug "finding synthetic replacements"

                    // this recursive query is converted into an interative one

//            withQ cnct, '''
//				insert into tree_syn_replacements(id)
//				select id
//				from (
//					with recursive all_current_supernodes(id) as (
//						select id from tree_replacements
//					union all
//						select n.id
//						from all_current_supernodes cc
//							join tree_link l on cc.id = l.subnode_id
//							join tree_node n on l.supernode_id = n.id
//						where 
//						    l.versioning_method = 'V'
//						and n.next_node_id is null
//						and n.CHECKED_IN_AT_ID is not null
//					)
//					select distinct id from all_current_supernodes
//				) distinct_current_supernodes
//				''',
//			                    { PreparedStatement qry -> qry.executeUpdate() }

                    int numberOfNewSyntheticNodesFound

                    numberOfNewSyntheticNodesFound = withQ(cnct, '''
				insert into tree_syn_replacements(id, pass_indicator)
				select id, 'P' from tree_replacements
				''',
                            { PreparedStatement qry -> qry.executeUpdate() }
                    ) as Integer

                    log.debug("starting with ${numberOfNewSyntheticNodesFound} nodes")

                    while (numberOfNewSyntheticNodesFound > 0) {
                        numberOfNewSyntheticNodesFound = withQ(cnct, '''
					insert into tree_syn_replacements(id, pass_indicator)
					select distinct n.id, 'N'
					from tree_syn_replacements cc
						join tree_link l on cc.id = l.subnode_id
						join tree_node n on l.supernode_id = n.id
					where
					cc.pass_indicator = 'P'
					and l.versioning_method = 'V'
					and n.id not in (select id from tree_syn_replacements)
					and n.next_node_id is null
					and n.CHECKED_IN_AT_ID is not null
				''', { PreparedStatement qry -> qry.executeUpdate() }) as Integer

                        log.debug("found ${numberOfNewSyntheticNodesFound} possible synthetic nodes")

                        withQ(cnct, '''
					update tree_syn_replacements set pass_indicator = 
						case 
							when pass_indicator = 'P' then null
							when pass_indicator = 'N' then 'P'
						end
				''', { PreparedStatement qry -> qry.executeUpdate() })

                    }

                    // zap all supernodes that we have an explicit replacement for

                    log.debug "filtering synthetic replacements"

                    withQ cnct, """
				delete from tree_syn_replacements where id in (
					select id from tree_replacements
				)
				""",
                            { PreparedStatement qry -> qry.executeUpdate() }

                    // second check - you cannot replace a node with a node being synthetically replaced

                    log.debug "looking for replacement nodes being synthetically replaced"

                    withQ cnct, 'select a.id, a.id2 from tree_replacements a join tree_syn_replacements b on a.id2 = b.id',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                try {
                                    while (rs.next()) {
                                        versioningValidityCheckFailed = true
                                        Node a1 = Node.get(rs.getLong(1))
                                        Node a2 = Node.get(rs.getLong(2))
                                        versioningValidityCheckFailures << makeMsg(Msg.CANNOT_REPLACE_NODE_WITH_NODE_BEING_SYNTHETICALLY_REPLACED, [a1, a2])
                                    }
                                }
                                finally {
                                    rs.close()
                                }
                            }

                    // third check - you cannot replace a node with any node that is above a tracking link that has a subnode being replaced,
                    // ether directly or synthetically
                    // note that this check does not ignore replaced nodes. This is the safest option, but it potentially will cause issues.
                    // I say potentially, because I do not anticipate tracking links to be used much except at the top level.

                    // This check seems more restrictive than it needs to be, because if the node sees the tracking link via a versioning link
                    // to a node being replaced, then it doesn't matter. But I'm not able to clearly enough express these cases, and I suspect they
                    // never happen. The possibility is that this check may prohibit perfectly valid versioning operations.

                    // edit - tracking links will potentially be low down in the tree pointing at profile data. I am not at the moment attempting to
                    // get my head around what this means in terms of versioning. Essentially, versioning operations wil(should) never mix up
                    // taxon and profile nodes, so this ought never be a problem.

                    // TODO: get my head around what this means in terms of versioning

                    log.debug "checking for possibly entangled tracking links"

                    withQ cnct, '''
				with recursive all_being_replaced as (
				    select id from tree_replacements
				union all
				    select id from tree_syn_replacements
				),
				all_that_can_see_em(id) as (
				    select supernode_id as id from tree_link
				    where subnode_id in (select id from all_being_replaced)
				    and versioning_method = 'T'
				union all
				    select supernode_id as id from all_that_can_see_em
					join tree_link on all_that_can_see_em.id = tree_link.subnode_id
				)
				select distinct tree_replacements.id, tree_replacements.id2 
				from tree_replacements 
					join all_that_can_see_em on tree_replacements.id2 = all_that_can_see_em.id
				''',
                            { PreparedStatement qry ->
                                ResultSet rs = qry.executeQuery()
                                try {
                                    while (rs.next()) {
                                        versioningValidityCheckFailed = true
                                        Node a1 = Node.get(rs.getLong(1))
                                        Node a2 = Node.get(rs.getLong(2))
                                        versioningValidityCheckFailures << makeMsg(Msg.CANNOT_REPLACE_NODE_WITH_NODE_ABOVE_A_TRACKING_LINK_ETC, [a1, a2])
                                    }
                                }
                                finally {
                                    rs.close()
                                }
                            }

                    // we skip this check for the APC import
                    if (!params['ignoreOrphans']) {

                        // final check - you cannot perform a versioning that would create an orphan. An orphan
                        // 1 - is a current node
                        // 2 - is not being replaced
                        // 3 - has parent nodes that are being replaced (necessary because otherwise the whole system stops if there is even one integrity problem in the data)
                        // 4 - has no parent nodes that are not being replaced
                        // 5 - and none of the replacement nodes have the orphan nodes as a subnode
                        // are able to perform this check now, because even though we don't know what the link
                        // fixups are going to be, none of those fixups will affect the orphaned nodes because
                        // orphaned nodes are not being replaced. Likewise, synthetically generating the supernodes
                        // does not crate orphans.
                        // we don't need to treewalk - just checking the top layer alone is enough to detect the existence
                        // of orphans
                        // in other words - all we really need to do is look at the immediate sublinks of the nodes
                        // being replaced.
                        // note that checking the links of the replacment nodes is just part of checking for any live links,
                        // because replacement nodes are always current nodes.
                        // PS - I am concerned that this algorithm might miss orphans that are created as a result of the action
                        // of a tracking link. The reason I worry about it is that I haven't bothered to think it through.

                        // TODO: think it through

                        log.debug "finding orphans"

                        withQ cnct, '''
					select all_subnodes.id from (
							-- all immediate subnodes of the nodes being replaced
							select distinct tree_node.id as id
							from tree_replacements
								join tree_link on tree_replacements.id = tree_link.supernode_id
								join tree_node on tree_link.subnode_id = tree_node.id
							where 
								tree_node.next_node_id is null
								-- I don't care about literals being orphaned. this is not a problem.
								and tree_node.internal_type <> 'V'
						) all_subnodes
					where
						-- not themselves being replaced
						all_subnodes.id not in (select tree_replacements.id from tree_replacements)
						-- and without any other links to them
						and not exists (
							select tree_link.id
							from tree_link join tree_node on tree_link.supernode_id = tree_node.id
							where tree_link.subnode_id = all_subnodes.id
							and tree_node.next_node_id is null
							and tree_node.id not in (select id from tree_replacements)
						)
					''',
                                { PreparedStatement qry ->
                                    ResultSet rs = qry.executeQuery()
                                    try {
                                        while (rs.next()) {
                                            versioningValidityCheckFailed = true
                                            Node n = Node.get(rs.getLong(1))
                                            versioningValidityCheckFailures << makeMsg(Msg.NODE_WOULD_BE_ORPHANED, [n])
                                        }
                                    }
                                    finally {
                                        rs.close()
                                    }
                                }
                    }

                    // We have now done some rather complex and subtle validity checks. exit now if eany of them failed.

                    if (versioningValidityCheckFailed) {
                        Message m = makeMsg(Msg.performVersioning, [ versioningValidityCheckFailures.size() ])
                        m.nested.addAll(versioningValidityCheckFailures)
                        raise m
                    }

                    // OK! Now for the fun bit. Actually making the changes.


                    log.debug "assigning new node ids"

                    withQ cnct, '''
				update tree_syn_replacements set id2 = nextval('nsl_global_seq')
				''',
                            { PreparedStatement qry -> qry.executeUpdate() }


                    if (log.isDebugEnabled()) {
                        log.debug('tree synthetic replacements -----------')
                        withQ cnct, "select id, id2 from tree_syn_replacements",
                                { PreparedStatement qry ->
                                    ResultSet rs = qry.executeQuery()
                                    while (rs.next()) {
                                        log.debug("replacing ${rs.getObject(1)} with ${rs.getObject(2)}")
                                    }
                                    rs.close()
                                }

                        log.debug('---------------------------------------')
                    }

                    log.debug "marking all outdating nodes as being updated"

                    // may as well do this now, before it gets complicated
                    withQ cnct, """
				update tree_node set lock_version=lock_version+1
				where id in (
					select l.supernode_id from tree_link l, (
						    select id from tree_replacements
						union all
						    select id from tree_syn_replacements
						) all_replacements
					where l.subnode_id = all_replacements.id
					and l.versioning_method <> 'F'
				)
				""",
                            { PreparedStatement qry -> qry.executeUpdate() }

                    log.debug "marking all tracking links to outdating nodes as being updated"
                    withQ cnct, '''
				update tree_link set lock_version=lock_version+1
				where subnode_id in 
						(
						    select id from tree_replacements
						union all
						    select id from tree_syn_replacements
						)
				and versioning_method = 'T'
				''',
                            { PreparedStatement qry -> qry.executeUpdate() }


                    // NSL-2137
                    // Before creating the synthetic nodes we need to replace the existing nodes, because if
                    // ths isn't done then when the new synthetic nodes are inserted, they will be current and
                    // have the same name_id as the existing node

                    // unfortunately, the nodes that these nodes will be replaced *with* are not in the table yet.

                    // So I will replace these nodes with the end node. After this, the 'replacing synthetic node'
                    // code block below will put the correct replacement node id on these nodes.

                    log.debug "temporarily replacing synthetic nodes with the end_node"
                    withQ cnct, '''
				update tree_node n
				set lock_version = lock_version+1,
				next_node_id = 0,
				replaced_at_id = ?
				where id in (select r.id from tree_syn_replacements r where r.id = n.id)
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, e.id)
                                qry.executeUpdate()
                            }

                    // OK. Create the new synthetic nodes. tree_node, and tree_link.

                    log.debug "copy nodes"
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
				 IS_SYNTHETIC,
				 CHECKED_IN_AT_ID
				)
				select 
				 tree_syn_replacements.id2, 	-- ID
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
				 'Y', 				-- IS_SYNTHETIC
				? 					-- CHECKED_IN_AT_ID
				from 
				tree_syn_replacements
					join tree_node n on tree_syn_replacements.id = n.id 
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, homeArrangement.id)
                                qry.setLong(2, e.id)
                                qry.executeUpdate()
                            }

                    // OK! Now to copy across the links for the new, synthetic nodes.

                    log.debug "copy links"
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
				 r.id2, 				--SUPERNODE_ID
				 l.SUBNODE_ID,
				 l.TYPE_URI_NS_PART_ID,
				 l.TYPE_URI_ID_PART,
				 l.LINK_SEQ,
				 l.VERSIONING_METHOD,
				 'Y' 							--IS_SYNTHETIC		  
				from tree_syn_replacements r
					join tree_link l on r.id = l.supernode_id
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }

                    // replace the nodes

                    log.debug "replacing nodes"
                    withQ cnct, '''
				update tree_node n
				set lock_version = lock_version+1, 
				next_node_id = (select r.id2 from tree_replacements r where r.id = n.id),
				replaced_at_id = ?
				where id in (select r.id from tree_replacements r where r.id = n.id)
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, e.id)
                                qry.executeUpdate()
                            }

                    log.debug "replacing synthetic nodes"
                    withQ cnct, '''
				update tree_node n
				set lock_version = lock_version+1, 
				next_node_id = (select r.id2 from tree_syn_replacements r where r.id = n.id),
				replaced_at_id = ?
				where id in (select r.id from tree_syn_replacements r where r.id = n.id)
				''',
                            { PreparedStatement qry ->
                                qry.setLong(1, e.id)
                                qry.executeUpdate()
                            }

                    // ok. Update all tracking links to any of the replaced nodes, and update any versioning links on *current* nodes
                    // note that we have already set the previous nodes to be no longer current.

                    log.debug "updating tracking and versioning links"
                    withQ cnct, '''
				update tree_link
				set 
				    lock_version = lock_version+1, 
				    subnode_id = (  
				        select r.id2 from  tree_replacements  r where r.id = tree_link.subnode_id
						union all
				        select r.id2 from tree_syn_replacements r where r.id = tree_link.subnode_id
				       )
				where (
				    subnode_id in (select id from tree_replacements)
				or
				    subnode_id in (select id from tree_syn_replacements)
				)
				and (
				    versioning_method = 'T'
				    or (
				        versioning_method = 'V'
				        AND
				        (select next_node_id from tree_node sup where sup.id=tree_link.supernode_id) is null
				    )
				)
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }


                    log.debug "updating all draft nodes marked as being copied from a versioned node"

                    withQ cnct, '''
				update tree_node
				set
				    lock_version = lock_version+1,
				    prev_node_id = (
				        select case when r.id2 = 0 then null else r.id2 end from tree_replacements r where r.id = prev_node_id
						union all
				        select case when r.id2 = 0 then null else r.id2 end from tree_syn_replacements r where r.id = prev_node_id
				    )
				where
				    prev_node_id in (
                        select r.id from tree_replacements  r
                    union all
                        select r.id from tree_syn_replacements r
                    )
				    and tree_node.checked_in_at_id is null
				''',
                            { PreparedStatement qry ->
                                qry.executeUpdate()
                            }


                    log.debug "versioning complete."

                }
            }
        }
    }

    /**
     * Delete a slice of history.
     * This operation deletes replaced nodes that are not used elsewhere, recursively deleting any nodes beneath it that would be
     * orphaned by the deletion.
     * It can only be used on replaced nodes that have no references to them. That is - top level classification roots
     * that no longer have the classification node tracking link pointing at them.
     */

    void deleteHistory(Node node) {
        mustHave(node: node) {
            clearAndFlush {
                node = DomainUtils.refetchNode(node)

                if (DomainUtils.isEndNode(node)) {
                    throw new IllegalArgumentException("cannot delete the end node")
                }

                if (!node.next) {
                    throw new IllegalArgumentException("cannot delete a current node")
                }

                if (!DomainUtils.isCheckedIn(node)) {
                    throw new IllegalArgumentException("cannot delete a draft node")
                }

                if (node.root.node == node) {
                    throw new IllegalArgumentException("cannot delete an arrangment root node")
                }

                if (node.supLink.size() != 0) {
                    throw new IllegalArgumentException("cannot delete a node with supernodes")
                }

                doWork sessionFactory_nsl, { Connection cnct ->

                    withQ cnct,

                            'delete from tree_temp_id', { PreparedStatement qry -> qry.executeUpdate() }
                    /*--
                     *	To find all orphans that result from deleting the top node, the algorithm is:
                     * "find nodes that become orphan if the nodes found so far are removed (limited to subnodes, because we don't
                     *  want to zap all orphans everywhere)".
                     *
                     *  The important bit is: the inner orphan check:
                     *    and not exists (
                     *      select o2.id from orphans o2 where o2.id = tree_link.supernode_id
                     *	  )
                     *
                     * Must check against all nodes found so far, not merely nodes found in the previous pass. Hopefully, the guys
                     * at oracle have got this one covered. If not, then given the graph
                     *   A->B->C; A->C
                     * then if A is deleted, the SQL will incorrectly conclude that C is not an orphan.
                     *
                     * -- EDIT --
                     *
                     * Although this *would* work better, Oracle won't do it, and the problem is precisely in that check. It seems that
                     * only the most recent pass is visible to the UNION ALL - there is no accumulator.
                     *
                     * This means that the best way to do this is with a loop in code. It shouldn't be too bad. The loop will execute once for each layer
                     * of nodes. Our tree is no more than 20 or so deep.
                     */
                    //	withQ cnct, '''insert into tree_temp_id(id)
                    //				select id from (
                    //					with orphans(id) as (
                    //					    select id from tree_node
                    //					    where tree_node.id = ?
                    //					    and tree_node.next_node_id is not null
                    //					    and not exists (select node_id from tree_arrangement where node_id = tree_node.id)
                    //					    and not exists (select tree_link.id from tree_link where tree_link.subnode_id = tree_node.id)
                    //					union all
                    //					    select tree_link.subnode_id
                    //					    from orphans, tree_link, tree_node
                    //					    where orphans.id = tree_link.supernode_id
                    //					    and tree_link.subnode_id = tree_node.id
                    //					    and tree_node.next_node_id is not null
                    //					    and not exists (select node_id from tree_arrangement where node_id = tree_node.id)
                    //					    -- check finds any links that are not parts of already known orpahns
                    //					    and not exists (
                    //					        select tree_link.id
                    //					        from tree_link
                    //					        where tree_link.subnode_id = tree_node.id
                    //					        and not exists (
                    //					            select o2.id from orphans o2 where o2.id = tree_link.supernode_id
                    //					        )
                    //					    )
                    //					)
                    //					select id from orphans
                    //				) recursive_wrapper
                    //				''', {PreparedStatement qry ->
                    //				qry.setLong(1, node.id)
                    //				qry.executeUpdate()}

                    int new_orphans_discovered = 0

                    withQ cnct,
                            '''
				insert into tree_temp_id(id)
			    select id from tree_node
			    where tree_node.id = ?
			    and tree_node.next_node_id is not null
			    and not exists (select node_id from tree_arrangement where node_id = tree_node.id)
			    and not exists (select tree_link.id from tree_link where tree_link.subnode_id = tree_node.id)
			''', { PreparedStatement qry ->
                        qry.setLong(1, node.id)
                        new_orphans_discovered = qry.executeUpdate()
                    }

                    while (new_orphans_discovered > 0) {
                        withQ cnct,
                                '''
					insert into tree_temp_id(id)
				    select tree_link.subnode_id
				    from tree_temp_id
						join tree_link on tree_temp_id.id = tree_link.supernode_id
						join tree_node on tree_link.subnode_id = tree_node.id
				    where 
				    -- not a current node
				    tree_node.next_node_id is not null
					-- not one we have already done
				    and not exists (select id from tree_temp_id t2 where tree_link.subnode_id = t2.id)
					-- not a tree root
				    and not exists (select tree_arrangement.node_id from tree_arrangement where tree_link.subnode_id = tree_arrangement.node_id)
				    -- an orphan. Has no links other than the ones we are already deleting
				    and not exists (
				        select tree_link.id
				        from tree_link l2
				        where l2.subnode_id = tree_link.subnode_id
				        and not exists (
				            select o2.id from tree_temp_id o2 where o2.id = l2.supernode_id
				        )
				    )
				''', {

                            PreparedStatement qry ->
                                new_orphans_discovered = qry.executeUpdate()
                        }
                    }

                    withQ cnct,
                            '''
					update tree_node 
					set lock_version=lock_version+1 
					where id in (
						select supernode_id 
						from tree_temp_id
							join tree_link on tree_link.subnode_id = tree_temp_id.id
					)
				''', { PreparedStatement qry -> qry.executeUpdate() }
                    withQ cnct,
                            '''update tree_node
				set lock_version=lock_version+1,
				next_node_id = (select next_node_id from tree_node n2 where n2.id = tree_node.next_node_id)
				where next_node_id in (select id from tree_temp_id)''', { PreparedStatement qry -> qry.executeUpdate() }
                    withQ cnct,

                            '''update tree_node
				set lock_version=lock_version+1,
				prev_node_id = (select prev_node_id from tree_node n2 where n2.id = tree_node.prev_node_id)
				where prev_node_id in (select id from tree_temp_id)''', { PreparedStatement qry -> qry.executeUpdate() }

                    withQ cnct, 'delete from tree_link where supernode_id in (select id from tree_temp_id)', { PreparedStatement qry ->
                        qry.
                                executeUpdate()
                    }
                    withQ cnct, 'delete from tree_node where id in (select id from tree_temp_id)', { PreparedStatement qry -> qry.executeUpdate() }

                }
            }
        }
    }

    Map<Node, Node> getStandardVersioningMap(Arrangement tempSpace, Arrangement classification) {
        mustHave(tempSpace: tempSpace, classification: classification) {
            if (tempSpace == classification) {
                throw new IllegalArgumentException('tempSpace == classification')
            }

            Map<Node, Node> v = new HashMap<Node, Node>()

            doWork sessionFactory_nsl, { Connection cnct ->
                withQ cnct, '''
				select  n_cls.id, n_tmp.id
				from tree_node n_tmp join tree_node n_cls on n_tmp.prev_node_id = n_cls.id
					where n_tmp.tree_arrangement_id = ?
					and n_cls.tree_arrangement_id = ?
			''', { PreparedStatement qry ->
                    qry.setLong(1, tempSpace.id)
                    qry.setLong(2, classification.id)
                    ResultSet rs = qry.executeQuery()
                    try {
                        while (rs.next()) {
                            v.put(Node.get(rs.getLong(1)), Node.get(rs.getLong(2)))
                        }
                    }
                    finally {
                        rs.close()
                    }

                }
            }

            return v
        } as Map<Node, Node>
    }

    Map<Node, Node> getCheckinVersioningMap(Arrangement from, Arrangement to, Node node) {
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

                if (!node.prev) {
                    throw new IllegalArgumentException("!node.prev")
                }

                if (!node.prev.root.equals(to)) {
                    throw new IllegalArgumentException("node.prev.root != to")
                }

                Map<Node, Node> v = new HashMap<Node, Node>()

                doWork sessionFactory_nsl, { Connection cnct ->
                    withQ cnct, '''
-- OK! This is going to be complicated
-- first, find all nodes that we are going to be moving
--   this means anything below the node node that is in the same tree
-- next, find all nodes that will be replaced
--  this means anything beneath the prev of the node being checked in
-- next, disregard any replaced nodes that are visible from the node being
--  checked in, because they are not going away anyway
--  this becomdes part of the search query - might not need another step
-- all the remaining nodes are either being replaced eith a node being checked in
--   or with the end node
with recursive
replacement_nodes as (
    select nn.id from tree_node nn where nn.id = ? and nn.tree_arrangement_id = ? and nn.internal_type != 'V'
    union all
    select nn.id
      from replacement_nodes
      join tree_link l on replacement_nodes.id = l.supernode_id
      join tree_node nn on l.subnode_id = nn.id
      where nn.tree_arrangement_id = ? and nn.internal_type != 'V'
),
visible_branches as (
  select nn.id
    from replacement_nodes
      join tree_link l on replacement_nodes.id = l.supernode_id
      join tree_node nn on l.subnode_id = nn.id
      where nn.tree_arrangement_id <> ? and nn.internal_type != 'V'
 ),
nodes_being_replaced as (
  select nn.id from tree_node nnxx join tree_node nn on nnxx.prev_node_id = nn.id
    where nnxx.id = ? and nn.tree_arrangement_id = ? and nn.internal_type != 'V'
    and nn.id not in (select id from visible_branches)
  union all
  select nn.id
    from nodes_being_replaced
      join tree_link l on nodes_being_replaced.id = l.supernode_id
      join tree_node nn on l.subnode_id = nn.id
      where nn.tree_arrangement_id = ? and nn.internal_type != 'V'
      and nn.id not in (select id from visible_branches)
)
select id, coalesce((select n.id
  from tree_node n join replacement_nodes nn on n.id = nn.id
  where n.prev_node_id = nodes_being_replaced.id
), 0) as id2
 from nodes_being_replaced
    		    	''', { PreparedStatement qry ->
                        qry.setLong(1, node.id)
                        qry.setLong(2, from.id)
                        qry.setLong(3, from.id)
                        qry.setLong(4, from.id)
                        qry.setLong(5, node.id)
                        qry.setLong(6, to.id)
                        qry.setLong(7, to.id)
                        ResultSet rs = qry.executeQuery()
                        try {
                            while (rs.next()) {
                                v.put(Node.get(rs.getLong(1)), Node.get(rs.getLong(2)))
                            }
                        }
                        finally {
                            rs.close()
                        }
                    }
                }
                return v
            } as Map<Node, Node>
        } as Map<Node, Node>
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