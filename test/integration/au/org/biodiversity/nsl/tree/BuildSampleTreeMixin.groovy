package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.NodeInternalType
import au.org.biodiversity.nsl.VersioningMethod
import org.apache.commons.logging.Log
import org.hibernate.SessionFactory

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class BuildSampleTreeMixin {
    SomeStuff makeSampleTree() {
        SomeStuff s = new SomeStuff(sessionFactory_nsl, basicOperationsService)
        s.makeTree()
        return s
    }

    SomeStuffWithHistory makeSampleTreeWithHistory() {
        SomeStuffWithHistory s = new SomeStuffWithHistory(sessionFactory_nsl, basicOperationsService)
        s.makeTree()
        return s
    }

    SomeStuffEmptyTree makeSampleEmptyTree() {
        SomeStuffEmptyTree s = new SomeStuffEmptyTree(sessionFactory_nsl, basicOperationsService)
        s.makeTree()
        return s
    }

    void dumpStuff(List stuff) {
        BuildSampleTreeUtil.dumpStuff(sessionFactory_nsl, log, stuff)
    }
}

class BuildSampleTreeUtil {
    static void dumpStuff(final SessionFactory sessionFactory_nsl, final Log log, final List stuff) {
        HibernateSessionUtils.doWork sessionFactory_nsl, { Connection cnct ->
            log.debug("----")
            log.debug("digraph {")
            stuff.each { SomeStuffEmptyTree s ->
                Long arrId = s.tid
                log.debug("subgraph cluster${arrId} {")
                log.debug("label=\"${arrId}\";")

                HibernateSessionUtils.withQ cnct, """select
									n.id, 
									n.tree_arrangement_id, 
									n.name_uri_id_part, 
									n.PREV_NODE_ID, 
									n.checked_in_at_id, 
									n.IS_SYNTHETIC, 
									n.replaced_at_id
							from tree_node n where n.tree_arrangement_id = ${arrId}
							order by n.tree_arrangement_id""", { PreparedStatement qry ->
                    ResultSet rs = qry.executeQuery()
                    while (rs.next()) {
                        log.debug("\t${rs.getLong(1)} [" //
                                + (rs.getObject(7) == null ? "" : " style=\"dotted\"")
                                + " label=\"${rs.getLong(1)} ${rs.getString(3)}${rs.getString(6) == 'Y' ? '*' : ''}\"" //
                                + " shape=\"${rs.getString(5) == 'Y' ? 'rectangle' : 'oval'}\"];")
                    }
                }
                log.debug("}")
            }

            log.debug('0 [label="[END]", shape="hexagon"];')

            stuff.each { ss ->

                SomeStuffEmptyTree s = (SomeStuffEmptyTree) ss

                Long arrId = s.tid

                HibernateSessionUtils.withQ cnct, "select l.supernode_id, l.subnode_id, l.link_seq, l.id from tree_link l, tree_node n where n.id = l.supernode_id and n.tree_arrangement_id = ${arrId}", { PreparedStatement qry ->
                    ResultSet rs = qry.executeQuery()
                    while (rs.next()) {
                        log.debug("${rs.getLong(1)} -> ${rs.getLong(2)} [taillabel=\"${rs.getInt(3)}\", label=\"${rs.getLong(4)}\", ];")
                    }
                }

                HibernateSessionUtils.withQ cnct, """select n.id, n.PREV_NODE_ID from tree_node n where n.tree_arrangement_id = ${
                    arrId
                } and n.PREV_NODE_ID is not null""", { PreparedStatement qry ->
                    ResultSet rs = qry.executeQuery()
                    while (rs.next()) {
                        log.debug("${rs.getLong(2)} -> ${rs.getLong(1)} [style=\"dotted\", constraint=\"false\"];")
                    }
                }
                HibernateSessionUtils.withQ cnct, """
						select n.id, n.NEXT_NODE_ID
						from tree_node n
						where n.tree_arrangement_id = ${arrId}
						and n.NEXT_NODE_ID is not null
						""", { PreparedStatement qry ->
                    ResultSet rs = qry.executeQuery()
                    while (rs.next()) {
                        log.debug("${rs.getLong(1)} -> ${rs.getLong(2)} [style=\"dashed\" ${rs.getLong(2) == 0 ? '' : ', constraint=\"false\"'}];")
                    }
                }
            }
            log.debug("}")
            log.debug("----")
        }
    }
}

class SomeStuffEmptyTree {
    long tid
    Arrangement t
    long rootid
    Node root

    final SessionFactory sessionFactory_nsl
    final BasicOperationsService basicOperationsService

    SomeStuffEmptyTree(SessionFactory sessionFactory_nsl, BasicOperationsService basicOperationsService) {
        this.sessionFactory_nsl = sessionFactory_nsl
        this.basicOperationsService = basicOperationsService
    }

    void makeTree() {
        t = basicOperationsService.createTemporaryArrangement()
        tid = t.id
        root = t.node
        rootid = root.id
    }

    void reload() {
        sessionFactory_nsl.currentSession.clear()
        reloadWithoutClear()
    }

    void reloadWithoutClear() {
        t = Arrangement.get(tid)
        root = Node.get(rootid)
    }
}

class SomeStuff extends SomeStuffEmptyTree {
    long aid
    long aaid
    long abid
    long bid
    long baid
    long bbid

    Node a
    Node aa
    Node ab
    Node b
    Node ba
    Node bb


    SomeStuff(SessionFactory sessionFactory_nsl, BasicOperationsService basicOperationsService) {
        super(sessionFactory_nsl, basicOperationsService)
    }

    void makeTree() {
        t = basicOperationsService.createTemporaryArrangement()
        tid = t.id
        root = t.node
        rootid = root.id

        a = basicOperationsService.createDraftNode(t.node, VersioningMethod.V, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'A'), taxon: DomainUtils.uri('afd-taxon', 'A'))
        aa = basicOperationsService.createDraftNode(a, VersioningMethod.V, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'AA'), taxon: DomainUtils.uri('afd-taxon', 'AA'))
        ab = basicOperationsService.createDraftNode(a, VersioningMethod.V, NodeInternalType.T, seq: 2, name: DomainUtils.uri('afd-name', 'AB'), taxon: DomainUtils.uri('afd-taxon', 'AB'))
        b = basicOperationsService.createDraftNode(t.node, VersioningMethod.V, NodeInternalType.T, Seq: 2, name: DomainUtils.uri('afd-name', 'B'), taxon: DomainUtils.uri('afd-taxon', 'B'))
        ba = basicOperationsService.createDraftNode(b, VersioningMethod.V, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'BA'), taxon: DomainUtils.uri('afd-taxon', 'BA'))
        bb = basicOperationsService.createDraftNode(b, VersioningMethod.V, NodeInternalType.T, seq: 2, name: DomainUtils.uri('afd-name', 'BB'), taxon: DomainUtils.uri('afd-taxon', 'BB'))

        t.refresh()     //needed because these are loaded by createTemp once createDraft is hibernate based remove
        root.refresh()

        aid = a.id
        aaid = aa.id
        abid = ab.id
        bid = b.id
        baid = ba.id
        bbid = bb.id

        reloadWithoutClear()
    }


    void reloadWithoutClear() {
        super.reloadWithoutClear()
        a = Node.get(aid)
        aa = Node.get(aaid)
        ab = Node.get(abid)
        b = Node.get(bid)
        ba = Node.get(baid)
        bb = Node.get(bbid)

//		// this should not be necessary - the clear session should do the job
//		[a,aa,ab,b,ba,bb].each {
//			it.refresh();
//		}
    }
}

class SomeStuffWithHistory extends SomeStuff {
    Node startRoot
    long startRootId

    SomeStuffWithHistory(SessionFactory sessionFactory_nsl, BasicOperationsService basicOperationsService) {
        super(sessionFactory_nsl, basicOperationsService)
    }

    void makeTree() {
        t = basicOperationsService.createTemporaryArrangement()
        tid = t.id
        root = t.node
        rootid = root.id

        startRoot = basicOperationsService.createDraftNode t.node, VersioningMethod.T, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'ROOT')
        startRootId = startRoot.id

        a = basicOperationsService.createDraftNode startRoot, VersioningMethod.V, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'A')
        aa = basicOperationsService.createDraftNode a, VersioningMethod.V, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'AA')
        ab = basicOperationsService.createDraftNode a, VersioningMethod.V, NodeInternalType.T, seq: 2, name: DomainUtils.uri('afd-name', 'AB')
        b = basicOperationsService.createDraftNode startRoot, VersioningMethod.V, NodeInternalType.T, seq: 2, name: DomainUtils.uri('afd-name', 'B')
        ba = basicOperationsService.createDraftNode b, VersioningMethod.V, NodeInternalType.T, seq: 1, name: DomainUtils.uri('afd-name', 'BA')
        bb = basicOperationsService.createDraftNode b, VersioningMethod.V, NodeInternalType.T, seq: 2, name: DomainUtils.uri('afd-name', 'BB')

        aid = a.id
        aaid = aa.id
        abid = ab.id
        bid = b.id
        baid = ba.id
        bbid = bb.id

        reloadWithoutClear()
    }

    void reloadWithoutClear() {
        super.reloadWithoutClear()
        startRoot = Node.get(startRootId)
        startRoot.refresh();
    }
}
