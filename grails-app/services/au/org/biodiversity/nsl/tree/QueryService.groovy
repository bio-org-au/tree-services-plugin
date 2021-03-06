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

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp

import static au.org.biodiversity.nsl.tree.HibernateSessionUtils.*

@Transactional
class QueryService {
    static datasource = 'nsl'

    SessionFactory sessionFactory_nsl

    static class Statistics {
        public int nodesCt
        public int currentNodesCt
        public int typesCt
        public int currentTypesCt
        public int namesCt
        public int currentNamesCt
        public int taxaCt
        public int currentTaxaCt
        public Set<Arrangement> dependsOn = new HashSet<Arrangement>()
        public Set<Arrangement> dependants = new HashSet<Arrangement>()
    }


    Statistics getStatistics(Arrangement r) {
        return doWork(sessionFactory_nsl, { Connection cnct ->
            final Statistics s = new Statistics()

            s.nodesCt = withQresult(cnct, '''
					select count(*) from tree_node where tree_arrangement_id = ?
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.currentNodesCt = withQresult(cnct, '''
					select count(*) from tree_node where tree_arrangement_id = ?
					and next_node_id is null
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.typesCt = withQresult(cnct, '''
					select count(*) from (
						select distinct type_uri_ns_part_id, type_uri_id_part
						from tree_node where tree_arrangement_id = ?
					) subq
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.currentTypesCt = withQresult(cnct, '''
					select count(*) from (
						select distinct type_uri_ns_part_id, type_uri_id_part
						from tree_node where tree_arrangement_id = ?
						and next_node_id is null
					) subq
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.namesCt = withQresult(cnct, '''
					select count(*) from (
						select distinct name_uri_ns_part_id, name_uri_id_part
						from tree_node where tree_arrangement_id = ?
					) subq
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.currentNamesCt = withQresult(cnct, '''
					select count(*) from (
						select distinct name_uri_ns_part_id, name_uri_id_part
						from tree_node where tree_arrangement_id = ?
						and next_node_id is null
					) subq
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.taxaCt = withQresult(cnct, '''
					select count(*) from (
						select distinct taxon_uri_ns_part_id, taxon_uri_id_part
						from tree_node where tree_arrangement_id = ?
					) subq
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer
            s.currentTaxaCt = withQresult(cnct, '''
					select count(*) from (
						select distinct taxon_uri_ns_part_id, taxon_uri_id_part
						from tree_node where tree_arrangement_id = ?
						and next_node_id is null
					) subq
					''') { PreparedStatement stmt ->
                stmt.setLong(1, r.id)
            } as Integer

            get_dependencies r, s, cnct

            return s
        }) as Statistics
    }

    Statistics getDependencies(Arrangement r) {
        return doWork(sessionFactory_nsl, { Connection cnct ->
            final Statistics s = new Statistics()
            get_dependencies r, s, cnct
            return s
        }) as Statistics
    }

    @SuppressWarnings("ChangeToOperator")
    private static void get_dependencies(final Arrangement r, final Statistics s, final Connection cnct) {
        withQ cnct, '''
					select distinct n2.tree_arrangement_id
					from tree_node n1
						join tree_link l on n1.id = l.supernode_id
						join tree_node n2 on l.subnode_id = n2.id
					where n1.tree_arrangement_id = ?
					and n1.tree_arrangement_id <> n2.tree_arrangement_id
				''', { PreparedStatement stmt ->
            stmt.setLong(1, r.id)
            ResultSet rs = stmt.executeQuery()
            while (rs.next()) {
                s.dependsOn.add(Arrangement.get(rs.getLong(1)))
            }
            rs.close()
        }

        withQ cnct, '''
					select distinct n1.tree_arrangement_id
					from tree_node n2
						join tree_link l on l.subnode_id = n2.id
						join tree_node n1 on n1.id = l.supernode_id
					where
					n2.tree_arrangement_id = ?
					and n1.tree_arrangement_id <> n2.tree_arrangement_id
				''', { PreparedStatement stmt ->
            stmt.setLong(1, r.id)
            ResultSet rs = stmt.executeQuery()
            while (rs.next()) {
                s.dependants.add(Arrangement.get(rs.getLong(1)))
            }
            rs.close()
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    void dumpTrees(Node tree1, Node tree2) {
        log.info dumpNodes([tree1, tree2])
    }

    @SuppressWarnings("ChangeToOperator")
    String dumpNodes(Collection<Node> topNodes) {

        StringWriter out = new StringWriter()
        doWork sessionFactory_nsl, { Connection cnct ->
            Set<Node> nodes = new HashSet<Node>()

            withQ cnct, '''
					with recursive n(id) as (
						select tree_node.id from tree_node where id = ?
					union all
						select subnode_id from n join tree_link on n.id=supernode_id
					)
					select distinct id from n
				''', { PreparedStatement stmt ->
                topNodes.each { Node topNode ->
                    if (topNode) {
                        topNode.refresh()
                        stmt.setLong(1, topNode.id)
                        ResultSet rs = stmt.executeQuery()
                        try {
                            while (rs.next()) {
                                nodes.add Node.get(rs.getLong(1))
                            }
                        }
                        finally {
                            rs.close()
                        }
                    }
                }
            }

            nodes.each { Node n ->
                n.refresh()
                n.root.refresh()
                n.subLink.each { Link l ->
                    l.refresh()
                }
                n.supLink.each { Link l ->
                    l.refresh()
                }
            }


            Map<Node, Node> nodeEquivalenceClass = new HashMap<Node, Node>()
            Map<Node, Collection<Node>> equivalenceClassNodes = new HashMap<Node, Collection<Node>>()

            Closure makeEquivalent = { Node a, Node b ->
                if (nodes.contains(a) && nodes.contains(b) && nodeEquivalenceClass.get(a) != nodeEquivalenceClass.get(b)) {
                    Collection<Node> aclass = equivalenceClassNodes.get(nodeEquivalenceClass.get(a))
                    Collection<Node> bclass = equivalenceClassNodes.get(nodeEquivalenceClass.get(b))
                    aclass.each { Node aa -> nodeEquivalenceClass.put(aa, b) }
                    bclass.addAll(aclass)
                    aclass.clear()
                }
            }

            nodes.each { Node n ->
                nodeEquivalenceClass.put(n, n)
                equivalenceClassNodes.put(n, new HashSet<Node>())
                equivalenceClassNodes.get(n).add(n)
            }

            nodes.each { Node n ->
                if (n.next && !DomainUtils.isEndNode(n.next)) makeEquivalent(n, n.next)
                if (n.prev) makeEquivalent(n, n.prev)
            }


            Closure dumpnode = { Node n ->
                String lbl
                String style = n.next ? DomainUtils.isEndNode(n.next) ? 'dotted' : 'dashed' : 'normal'
                lbl = "${DomainUtils.getNodeUri(n).asQNameD()}"
                if (DomainUtils.getRawNodeTypeUri(n)) lbl = "${lbl}\\n${DomainUtils.getRawNodeTypeUri(n).asQNameD()}"
                if (DomainUtils.hasName(n)) lbl = "${lbl}\\n${DomainUtils.getNameUri(n).asQNameD()}"
                if (DomainUtils.hasTaxon(n)) lbl = "${lbl}\\n${DomainUtils.getTaxonUri(n).asQNameD()}"
                if (DomainUtils.hasResource(n)) lbl = "${lbl}\\n${DomainUtils.getResourceUri(n).asQNameD()}"
                if (n.literal) lbl = "${lbl}\\n<${n.literal}>"

                out.println("""${n.id} [shape=\"${DomainUtils.isCheckedIn(n) ? 'rectangle' : 'oval'}\", label=\"${
                    lbl
                }\", style=\"${style}\"];""")
            }


            out.println "digraph {"

            equivalenceClassNodes.each { Node n, Collection<Node> v ->
                if (!v.isEmpty()) {
                    if (v.size() == 1) {
                        dumpnode(n)
                    } else {
                        out.println "subgraph cluster_${n.id} {"
                        out.println 'rankdir = \"LR\";'
                        v.each { nn -> dumpnode(nn) }
                        out.println "}"
                    }
                }
            }

            nodes.each { Node n ->
                n.subLink.each { Link l ->
                    String colour
                    colour = "black"


                    String label = "${l.id}"
                    String taillabel = "${l.linkSeq}"

                    if (l.typeUriIdPart) {
                        label = "${label}\\n${l.typeUriIdPart}"
                    }

                    out.println """${l.supernode.id} -> ${l.subnode.id} [taillabel=\"${taillabel}\", label=\"${
                        label
                    }\", color=\"${colour}\"];"""
                }
                n.supLink.findAll { !nodes.contains(it.supernode) }.each { Link l ->
                    String colour
                    colour = "black"


                    String label = "${l.id}"
                    String taillabel = "${l.linkSeq}"

                    if (l.typeUriIdPart) {
                        label = "${label}\\n${l.typeUriIdPart}"
                    }

                    out.println """${l.supernode.id} -> ${l.subnode.id} [taillabel=\"${taillabel}\", label=\"${
                        label
                    }\", color=\"${colour}\"];"""
                }
            }
            nodes.each { Node n ->
                if (n.next && nodes.contains(n.next)) {
                    if (n.next.prev == n) {
                        out.println """${n.id} -> ${n.next.id} [constraint=\"true\", color=\"blue\"];"""
                    } else {
                        out.println """${n.id} -> ${
                            n.next.id
                        } [style=\"dashed\", constraint=\"true\", color=\"blue\"];"""
                    }
                }
                if (n.prev && nodes.contains(n.prev)) {
                    if (n.prev.next == n) {
                        // already done
                    } else {
                        out.println """${n.id} -> ${
                            n.prev.id
                        } [style=\"dashed\", constraint=\"true\", color=\"red\"];"""
                    }
                }
            }

            out.println "}"
        }
        out.toString()
    }

    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    List<Link> getPathForNode(Arrangement a, Node n) {
        if (!a) throw new IllegalArgumentException("Arrangement not specified")
        if (!n) throw new IllegalArgumentException("Node not specified")

        Collection<Link> l = new ArrayList<Link>()
        doWork sessionFactory_nsl, { Connection cnct ->
            withQ cnct, '''
				with recursive search_up(link_id, supernode_id, subnode_id) as (
				    select l.id, l.supernode_id, l.subnode_id from tree_link l where l.subnode_id = ?
				union all
				    select l.id, l.supernode_id, l.subnode_id 
					from search_up join tree_link l on search_up.supernode_id = l.subnode_id
				),
				search_down(link_id, subnode_id, depth) as
				(
				    select search_up.link_id, search_up.subnode_id, 0 
					from tree_arrangement a join search_up on a.node_id = search_up.supernode_id
					where a.id = ?
				union all
				    select search_up.link_id, search_up.subnode_id, search_down.depth+1
				    from search_down join search_up on search_down.subnode_id = search_up.supernode_id
				)
				select link_id from search_down order by depth
			''', { PreparedStatement sql ->
                sql.setLong(1, n.id)
                sql.setLong(2, a.id)
                ResultSet rs = sql.executeQuery()
                try {
                    while (rs.next()) {
                        l.add(Link.get(rs.getLong(1)))
                    }
                }
                finally {
                    rs.close()
                }
            }
        }

        return l
    }

    // find the latest parent node for the given node up to the root of
    // the tree that is belongs to

    List<Link> getLatestPathForNode(Node n) {
        Collection<Link> path = new ArrayList<Link>()
        for (; ;) {
            def links = n.supLink.findAll { it.supernode.root == n.root && it.supernode.checkedInAt != null }

            if (links) {
                Link mostrecent = links.first()
                links.each { Link it ->
                    if (it.supernode.checkedInAt.timeStamp > mostrecent.supernode.checkedInAt.timeStamp) {
                        mostrecent = it
                    }
                }

                path.add(mostrecent)
                n = mostrecent.supernode
            } else {
                break
            }
        }
        return path
    }

    Long getNextval() {
        return doWork(sessionFactory_nsl) { Connection cnct ->
            return withQresult(cnct, "select nextval('nsl_global_seq') as v") {}
        } as Long
    }

    @SuppressWarnings("ChangeToOperator")
    Timestamp getTimestamp() {
        return (Timestamp) doWork(sessionFactory_nsl, { Connection cnct ->
            return withQ(cnct, '''select localtimestamp as ts''', { PreparedStatement stmt ->
                ResultSet rs = stmt.executeQuery()
                rs.next()
                Timestamp ts = rs.getTimestamp(1)
                rs.close()
                return ts
            })
        })
    }

    /**
     * Find a List of Current Nodes that match the nameUri in the classification. In a correctly configured tree there
     * should only ever be one Current Node that matches the nameUri
     *
     * @param classification
     * @param nameUri
     * @return Collection of Node
     */
    Collection<Node> findCurrentNslName(Arrangement classification, Name name) {
        Node.executeQuery('''select n from Node n
where root = :arrangement
and name = :name
and checkedInAt is not null
and next is null
''', [arrangement: classification, name: name])
    }

    /**
     * Find a List of Current Nodes that match the nameUri in the classification. In a correctly configured tree there
     * should only ever be one Current Node that matches the nameUri
     *
     * @param classification
     * @param nameUri
     * @return Collection of Node
     */
    Collection<Node> findCurrentName(Arrangement classification, Uri nameUri) {
        Node.executeQuery('''select n from Node n
where root = :arrangement
and nameUriIdPart = :idPart
and nameUriNsPart = :namespace
and checkedInAt is not null
and next is null
''', [arrangement: classification, idPart: nameUri.idPart, namespace: nameUri.nsPart])
    }

    /**
     * Find a List of Current Nodes that match the taxonUri in the classification. In a correctly configured tree there
     * should only ever be one Current Node that matches the taxonUri
     *
     * @param classification
     * @param taxonUri
     * @return Collection of Node
     */
    Collection<Node> findCurrentNslInstance(Arrangement classification, Instance instance) {
        Node.executeQuery('''select n from Node n
where root = :arrangement
and instance = :instance
and checkedInAt is not null
and next is null
''', [arrangement: classification, instance: instance])
    }

    /**
     * Find a List of Current Nodes that match the taxonUri in the classification. In a correctly configured tree there
     * should only ever be one Current Node that matches the taxonUri
     *
     * @param classification
     * @param taxonUri
     * @return Collection of Node
     */
    Collection<Node> findCurrentTaxon(Arrangement classification, Uri taxonUri) {
        Node.executeQuery('''select n from Node n
where root = :arrangement
and taxonUriIdPart = :idPart
and taxonUriNsPart = :namespace
and checkedInAt is not null
and next is null
''', [arrangement: classification, idPart: taxonUri.idPart, namespace: taxonUri.nsPart])
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    Collection<Link> findCurrentNamePlacement(Arrangement classification, Uri nameUri) {
        Link.executeQuery('''select l from Link l
where
    supernode.root = :arrangement
and supernode.checkedInAt is not null
and supernode.next is null
and subnode.root = :arrangement
and subnode.nameUriIdPart = :idPart
and subnode.nameUriNsPart = :namespace
and subnode.checkedInAt is not null
and subnode.next is null
''', [arrangement: classification, idPart: nameUri.idPart, namespace: nameUri.nsPart])
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    Collection<Link> findCurrentTaxonPlacement(Arrangement classification, Uri taxonUri) {
        Link.executeQuery('''select l from Link l
where
    supernode.root = :arrangement
and supernode.checkedInAt is not null
and supernode.next is null
and subnode.root = :arrangement
and subnode.taxonUriIdPart = :idPart
and subnode.taxonUriNsPart = :namespace
and subnode.checkedInAt is not null
and subnode.next is null
''', [arrangement: classification, idPart: taxonUri.idPart, namespace: taxonUri.nsPart])
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    Collection<Link> findCurrentNslNamePlacement(Arrangement classification, Name nslName) {
        Link.executeQuery('''select l from Link l
where
    supernode.root = :arrangement
and supernode.checkedInAt is not null
and supernode.next is null
and subnode.root = :arrangement
and subnode.name = :nslName
and subnode.checkedInAt is not null
and subnode.next is null
''', [arrangement: classification, nslName: nslName])
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    Collection<Link> findCurrentNslInstancePlacement(Arrangement classification, Instance nslInstance) {
        Link.executeQuery('''select l from Link l
where
    supernode.root = :arrangement
and supernode.checkedInAt is not null
and supernode.next is null
and subnode.root = :arrangement
and subnode.instance = :nslInstance
and subnode.checkedInAt is not null
and subnode.next is null
''', [arrangement: classification, nslInstance: nslInstance])
    }

    @SuppressWarnings("ChangeToOperator")
    Link findCurrentNslNameInTreeOrBaseTree(Arrangement tree, Name name) {
        Link l = null

        doWork sessionFactory_nsl, { Connection cnct ->
            withQ cnct, "select find_name_in_tree(?, ?) as link_id", { PreparedStatement qry ->
                qry.setLong(1, name.id)
                qry.setLong(2, tree.id)
                ResultSet rs = qry.executeQuery()
                rs.next()
                l = Link.get(rs.getLong('link_id'))
                rs.close()
            }
        }

        return l
    }

    // this query returns the relationship instances where instance.hasSynonym foo
    @SuppressWarnings("ChangeToOperator")
    List<Instance> findSynonymsOfInstanceInTree(Arrangement tree, Instance instance) {
        List<Instance> l = new ArrayList<Instance>()

        doWork sessionFactory_nsl, { Connection cnct ->
            withQ cnct, '''
with recursive
walk as (
    select instance.id relationship_instance_id, tree_node.id node_id, tree_node.tree_arrangement_id
    from instance
    join tree_node on instance.cites_id = tree_node.instance_id
    where instance.cited_by_id = ?
    and tree_node.internal_type = 'T'
    and tree_node.tree_arrangement_id in (?,?)
    and tree_node.next_node_id is null
    union all
    select walk.relationship_instance_id, tree_node.id node_id, tree_node.tree_arrangement_id
    from walk join tree_link on tree_link.subnode_id = walk.node_id
    join tree_node on tree_link.supernode_id = tree_node.id
    where tree_node.next_node_id is null
    and tree_node.tree_arrangement_id in (?,?)
    and walk.tree_arrangement_id <> ?
)
select distinct relationship_instance_id from walk where tree_arrangement_id = ?

            ''', { PreparedStatement qry ->
                qry.setLong(1, instance.id)
                qry.setLong(2, tree.id)
                qry.setLong(3, tree.baseArrangement == null ? tree.id : tree.baseArrangement.id)
                qry.setLong(4, tree.id)
                qry.setLong(5, tree.baseArrangement == null ? tree.id : tree.baseArrangement.id)
                qry.setLong(6, tree.id)
                qry.setLong(7, tree.id)


                ResultSet rs = qry.executeQuery()
                while (rs.next())
                    l.add(Instance.get(rs.getLong('relationship_instance_id')))
                rs.close()
            }
        }

        return l
    }

    @SuppressWarnings("ChangeToOperator")
    List<Instance> findInstancesHavingSynonymInTree(Arrangement tree, Instance instance) {
        List<Instance> l = new ArrayList<Instance>()

        doWork sessionFactory_nsl, { Connection cnct ->
            withQ cnct, '''
with recursive
walk as (
    select instance.id as relationship_instance_id, tree_node.id node_id, tree_node.tree_arrangement_id
    from instance
    join tree_node on tree_node.instance_id = instance.cited_by_id
    where instance.cites_id = ?
    and tree_node.internal_type = 'T'
    and tree_node.tree_arrangement_id in (?,?)
    and tree_node.next_node_id is null
    union all
    select
    walk.relationship_instance_id, tree_node.id node_id, tree_node.tree_arrangement_id
    from walk join tree_link on tree_link.subnode_id = walk.node_id
    join tree_node on tree_link.supernode_id = tree_node.id
    where tree_node.next_node_id is null
    and tree_node.tree_arrangement_id in (?,?)
    and walk.tree_arrangement_id <> ?
)
select distinct relationship_instance_id from walk where tree_arrangement_id = ?

            ''', { PreparedStatement qry ->
                qry.setLong(1, instance.id)
                qry.setLong(2, tree.id)
                qry.setLong(3, tree.baseArrangement == null ? tree.id : tree.baseArrangement.id)
                qry.setLong(4, tree.id)
                qry.setLong(5, tree.baseArrangement == null ? tree.id : tree.baseArrangement.id)
                qry.setLong(6, tree.id)
                qry.setLong(7, tree.id)


                ResultSet rs = qry.executeQuery()
                while (rs.next())
                    l.add(Instance.get(rs.getLong('relationship_instance_id')))
                rs.close()
            }
        }

        return l
    }


    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    List findNamesInSubtree(Node node, String nameLike) {
        List l = []

        doWork sessionFactory_nsl, { Connection cnct ->

// well, this is rather nasty!
// find the names. find any current nodes whose instances have the name, or whose instances have
// a synonym with the name. then run up the current tree, until we stop at the node we are looking for.


            withQ cnct, '''
with recursive names as (
select id from name where LOWER(simple_name) like LOWER(?)
),
matching_nodes as (
  select tree_node.id tree_node_id, instance.id as instance_id
  from tree_node join instance on tree_node.instance_id = instance.id
  where instance.name_id in (select id from names)
  and tree_node.internal_type = 'T'
  and tree_node.replaced_at_id is null
  and tree_node.tree_arrangement_id ''' + (node.root.baseArrangement ? ' in (?,?)' : ' = ?') + '''
union all
  select tree_node.id tree_node_id, syn.id as instance_id
  from tree_node join instance on tree_node.instance_id = instance.id
    join instance as syn on instance.id = syn.cited_by_id
  where syn.name_id in (select id from names)
  and tree_node.internal_type = 'T'
  and tree_node.replaced_at_id is null
  and tree_node.tree_arrangement_id ''' + (node.root.baseArrangement ? ' in (?,?)' : ' = ?') + '''
)
,
tree_runner as (
  select matching_nodes.tree_node_id as running_node_id, matching_nodes.*
  from matching_nodes
union all
  select tree_link.supernode_id as running_node_id, tree_runner.tree_node_id, tree_runner.instance_id
  from tree_runner join tree_link on tree_link.subnode_id = tree_runner.running_node_id
  join tree_node on tree_link.supernode_id = tree_node.id
  where
  tree_runner.running_node_id <> ? -- clip the search
  and tree_node.replaced_at_id is null
)
select tree_runner.*
from tree_runner
where tree_runner.running_node_id = ?
			''', { PreparedStatement sql ->
                if (node.root.baseArrangement) {
                    sql.setString(1, nameLike)
                    sql.setLong(2, node.root.id)
                    sql.setLong(3, node.root.baseArrangement.id)
                    sql.setLong(4, node.root.id)
                    sql.setLong(5, node.root.baseArrangement.id)
                    sql.setLong(6, node.id)
                    sql.setLong(7, node.id)
                } else {
                    sql.setString(1, nameLike)
                    sql.setLong(2, node.root.id)
                    sql.setLong(3, node.root.id)
                    sql.setLong(4, node.id)
                    sql.setLong(5, node.id)
                }
                ResultSet rs = sql.executeQuery()
                try {
                    while (rs.next()) {
                        l.add([
                                node           : Node.get(rs.getLong('tree_node_id')),
                                matchedInstance: Instance.get(rs.getLong('instance_id'))
                        ])
                    }
                }
                finally {
                    rs.close()
                }
            }
        }

        return l

    }

    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    List findNamesDirectlyInSubtree(Node node, String nameLike) {

        /**
         * For some reason, simply removing second clause from the union in the findNamesInSubtree
         * query slows down this search. Postgres optimises something it shouldn't, and
         * I don't know what.
         * So instead, I have that nasty 'foo' column which seems to trick postgres
         * into doing it the right way.
         */

        List l = []

        doWork sessionFactory_nsl, { Connection cnct ->

// well, this is rather nasty!
// find the names. find any current nodes whose instances have the name, or whose instances have
// a synonym with the name. then run up the current tree, until we stop at the node we are looking for.


            withQ cnct, '''
with recursive names as (
select id from name where LOWER(simple_name) like LOWER(?)
),
matching_nodes as (
  select 'x' as foo, tree_node.id tree_node_id, instance.id as instance_id
  from tree_node join instance on tree_node.instance_id = instance.id
  where instance.name_id in (select id from names)
  and tree_node.internal_type = 'T'
  and tree_node.replaced_at_id is null
  and tree_node.tree_arrangement_id ''' + (node.root.baseArrangement ? ' in (?,?)' : ' = ?') + '''
union all
  select 'y' as foo, tree_node.id tree_node_id, syn.id as instance_id
  from tree_node join instance on tree_node.instance_id = instance.id
    join instance as syn on instance.id = syn.cited_by_id
  where syn.name_id in (select id from names)
  and tree_node.internal_type = 'T'
  and tree_node.replaced_at_id is null
  and tree_node.tree_arrangement_id ''' + (node.root.baseArrangement ? ' in (?,?)' : ' = ?') + '''
)
,
tree_runner as (
  select matching_nodes.tree_node_id as running_node_id, matching_nodes.*
  from matching_nodes where foo = 'x'
union all
  select tree_link.supernode_id as running_node_id, 'z' as foo, tree_runner.tree_node_id, tree_runner.instance_id
  from tree_runner join tree_link on tree_link.subnode_id = tree_runner.running_node_id
  join tree_node on tree_link.supernode_id = tree_node.id
  where
  tree_runner.running_node_id <> ? -- clip the search
  and tree_node.replaced_at_id is null
)
select tree_runner.*
from tree_runner
where tree_runner.running_node_id = ?
			''', { PreparedStatement sql ->
                if (node.root.baseArrangement) {
                    sql.setString(1, nameLike)
                    sql.setLong(2, node.root.id)
                    sql.setLong(3, node.root.baseArrangement.id)
                    sql.setLong(4, node.root.id)
                    sql.setLong(5, node.root.baseArrangement.id)
                    sql.setLong(6, node.id)
                    sql.setLong(7, node.id)
                } else {
                    sql.setString(1, nameLike)
                    sql.setLong(2, node.root.id)
                    sql.setLong(3, node.root.id)
                    sql.setLong(4, node.id)
                    sql.setLong(5, node.id)
                }
                ResultSet rs = sql.executeQuery()
                try {
                    while (rs.next()) {
                        l.add([
                                node           : Node.get(rs.getLong('tree_node_id')),
                                matchedInstance: Instance.get(rs.getLong('instance_id'))
                        ])
                    }
                }
                finally {
                    rs.close()
                }
            }
        }

        return l

    }

/**
 * Looks for a node in a tree, finding either the node itself or a checked-out version of the node. This is a common
 * thing needing to be done.
 */

    @SuppressWarnings("ChangeToOperator")
    Link findNodeCurrentOrCheckedout(Node supernode, Node findNode) {
        if (!supernode) throw new IllegalArgumentException("supernode is null")
        if (!findNode) throw new IllegalArgumentException("findNode is null")

        doWork(sessionFactory_nsl) { Connection cnct ->
            withQ cnct, '''
with recursive
start_nodes(id) as (
    select ?
    union
    select id from tree_node n where n.checked_in_at_id is null and n.prev_node_id = ?
),
ll(start_id, supernode_id) as (
    select tree_link.id as start_id, tree_link.supernode_id
    from start_nodes join tree_link on start_nodes.id = tree_link.subnode_id
union all
    select ll.start_id, tree_link.supernode_id
    from ll join tree_link on ll.supernode_id = tree_link.subnode_id
)
select distinct start_id from ll where supernode_id = ?
				''',
                    { PreparedStatement qry ->
                        qry.setLong(1, findNode.id)
                        qry.setLong(2, findNode.id)
                        qry.setLong(3, supernode.id)
                        ResultSet rs = qry.executeQuery()

                        try {
                            if (!rs.next()) return null

                            long linkId = rs.getLong(1)

                            if (!rs.next()) {
                                // unique result found
                                return Link.get(linkId)
                            } else {
                                // drat - more than one link found
                                Message multipleLinksFound = ServiceException.makeMsg(Msg.NODE_APPEARS_IN_MULTIPLE_LOCATIONS_IN, [findNode, supernode])

                                multipleLinksFound.nested.add Link.get(linkId)
                                multipleLinksFound.nested.add Link.get(rs.getLong(1))
                                while (rs.next()) {
                                    multipleLinksFound.nested.add Link.get(rs.getLong(1))
                                }

                                ServiceException.raise ServiceException.makeMsg(Msg.findNodeCurrentOrCheckedout, [supernode, findNode, multipleLinksFound])
                            }

                        }
                        finally {
                            rs.close()
                        }

                    }
        } as Link
    }

    @SuppressWarnings("ChangeToOperator")
    int countPaths(Node root, Node focus) {
        try {
            if (root == null || focus == null) return 0
            if (root == focus) return 1

            int ct = 0

            doWork(sessionFactory_nsl) { Connection cnct ->
                withQ cnct, '''
        with recursive scan_up as (
                select supernode_id, subnode_id from tree_link where subnode_id = ?
                union all
                select l.supernode_id, l.subnode_id from tree_link l, scan_up where l.subnode_id = scan_up.supernode_id and scan_up.subnode_id <> ?
        )
        select count(*) as ct from scan_up where supernode_id = ?
				''',
                        { PreparedStatement qry ->
                            qry.setLong(1, focus.id)
                            qry.setLong(2, root.id)
                            qry.setLong(3, root.id)
                            ResultSet rs = qry.executeQuery()
                            try {
                                rs.next()
                                ct = rs.getInt(1)
                            }
                            finally {
                                rs.close()
                            }

                        }
            }
            return ct
        }
        catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    int countImmediateSubtaxa(Node n) {
        if (n == null) return 0
        long ct = 0

        doWork(sessionFactory_nsl) { Connection cnct ->
            withQ cnct, '''
        select count(*) ct
        from tree_link l
        join tree_node n on l.subnode_id = n.id
        where l.supernode_id = ?
        and n.internal_type in ('S','T')
				''',
                    { PreparedStatement qry ->
                        qry.setLong(1, n.id)
                        ResultSet rs = qry.executeQuery()
                        try {
                            rs.next()
                            ct = rs.getLong('ct')
                        }
                        finally {
                            rs.close()
                        }

                    }
        }

        return ct
    }

    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    Map<Long, Integer> getSubtaxaCountForAllSubtaxa(Node n) {
        Map m = new HashMap<Long, Integer>()

        doWork(sessionFactory_nsl) { Connection cnct ->
            withQ cnct, '''
        select l1.subnode_id as node_id, count(l2.subnode_id) as subnodes
        from tree_link l1
        join tree_node n1 on l1.subnode_id = n1.id
        join tree_link l2 on l1.subnode_id = l2.supernode_id
        join tree_node n2 on l2.subnode_id = n2.id
        where l1.supernode_id = ?
        and n1.internal_type in ('S','T')
        and n2.internal_type in ('S','T')
        group by l1.subnode_id
				''',
                    { PreparedStatement qry ->
                        qry.setLong(1, n.id)
                        ResultSet rs = qry.executeQuery()
                        try {
                            while (rs.next()) {
                                m.put(rs.getLong('node_id'), rs.getInt('subnodes'))
                            }
                        }
                        finally {
                            rs.close()
                        }

                    }
        }

        return m
    }

    @SuppressWarnings("ChangeToOperator")
    List<Node> findPath(Node root, Node focus) {
        List<Node> l = new ArrayList<Node>()

        doWork(sessionFactory_nsl) { Connection cnct ->
            withQ cnct, '''
        with recursive scan_up as (
                select id as supernode_id, id as subnode_id from tree_node where id = ?
                union all
                select l.supernode_id, l.subnode_id from tree_link l, scan_up where l.subnode_id = scan_up.supernode_id and scan_up.supernode_id <> ?
        ),
        scan_down as (
                select scan_up.* from scan_up where scan_up.supernode_id = ?
        union all
        select scan_up.* from scan_up, scan_down where scan_up.supernode_id = scan_down.subnode_id and scan_down.supernode_id <> ?
        )
        select * from scan_down
				''',
                    { PreparedStatement qry ->
                        qry.setLong(1, focus.id)
                        qry.setLong(2, root.id)
                        qry.setLong(3, root.id)
                        qry.setLong(4, focus.id)
                        ResultSet rs = qry.executeQuery()

                        try {
                            while (rs.next()) {

                                l.add(Node.get(rs.getLong(1)))
                            }
                        }
                        finally {
                            rs.close()
                        }

                    }
        }

        return l
    }

    @SuppressWarnings("ChangeToOperator")
    List<Link> findPathLinks(Node root, Node focus) {
        if (!root) throw new IllegalArgumentException("root is null")
        if (!focus) throw new IllegalArgumentException("focus is null")

        List<Link> l = new ArrayList<Link>()

        doWork(sessionFactory_nsl) { Connection cnct ->
            withQ cnct, '''
                with recursive scan_up as (
                  select l.id, l.supernode_id, l.subnode_id from tree_link l where l.subnode_id = ?
                union all
                  select l.id, l.supernode_id, l.subnode_id from tree_link l, scan_up where l.subnode_id = scan_up.supernode_id and scan_up.supernode_id <> ?
                ),
                scan_down as (
                  select 1 as depth, scan_up.* from scan_up where scan_up.supernode_id = ?
                union all
                  select depth+1 as depth, scan_up.* from scan_up, scan_down where scan_up.supernode_id = scan_down.subnode_id and scan_down.supernode_id <> ?
                )
                select * from scan_down order by depth
        ''',
                    { PreparedStatement qry ->
                        qry.setLong(1, focus.id)
                        qry.setLong(2, root.id)
                        qry.setLong(3, root.id)
                        qry.setLong(4, focus.id)
                        ResultSet rs = qry.executeQuery()

                        try {
                            while (rs.next()) {
                                l.add(Link.get(rs.getLong('id')))
                            }
                        }
                        finally {
                            rs.close()
                        }
                    }
        }

        return l
    }

    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    int countDraftNodes(Node focus) {
        try {
            if (focus == null) return 0

            int ct = 0

            doWork(sessionFactory_nsl) { Connection cnct ->
                withQ cnct, '''
        with recursive scan as (
                select tree_node.id from tree_node where tree_node.id = ? and tree_node.checked_in_at_id is null
                union all
                select tree_node.id from scan join tree_link on scan.id = tree_link.supernode_id
                join tree_node on tree_link.subnode_id = tree_node.id
                where tree_node.checked_in_at_id is null
        )
        select count(*) as ct from scan
				''',
                        { PreparedStatement qry ->
                            qry.setLong(1, focus.id)
                            ResultSet rs = qry.executeQuery()
                            try {
                                rs.next()
                                ct = rs.getInt(1)
                            }
                            finally {
                                rs.close()
                            }

                        }
            }
            return ct
        }
        catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Find all names in n1 or n2, and work out changes. Do this by link id, because we want to report by
     * placement rather than by node composition.
     * @param n1
     * @param n2
     * @return
     */

    @SuppressWarnings(["ChangeToOperator", "GroovyUnusedDeclaration"])
    List findDifferences(Node n1, Node n2) {
        log.debug("differences between ${n1} and ${n2}")

        List l = []

        doWork(sessionFactory_nsl) { Connection cnct ->
            withQ cnct, '''
        with recursive tree_1 as (
                select cast (null as bigint) as link_id,
                       n.id as subnode_id,
                       n.name_id as name_id
                    from tree_node n
                    where n.id = ?
                union all
                select  l.id as link_id,
                l.subnode_id as subnode_id,
                n.name_id as name_id
                from tree_1
                join tree_link l on tree_1.subnode_id = l.supernode_id
                join tree_node n on l.subnode_id = n.id
                where n.internal_type = 'T'
        ),
        tree_2 as (
                select cast (null as bigint) as link_id,
                       n.id as subnode_id,
                       n.name_id as name_id
                    from tree_node n
                    where n.id = ?
                union all
                select  l.id as link_id,
                l.subnode_id as subnode_id,
                n.name_id as name_id
                from tree_2
                join tree_link l on tree_2.subnode_id = l.supernode_id
                join tree_node n on l.subnode_id = n.id
                where n.internal_type = 'T'
        ),
        names as ( select name_id from tree_1 union select name_id from tree_2)
        select
          names.name_id,
          tree_1.link_id as link_1,
          tree_2.link_id as link_2,
          tree_1.subnode_id as subnode_1,
          tree_2.subnode_id as subnode_2
          from
            names
              join name on names.name_id = name.id
                left outer join tree_1 on names.name_id = tree_1.name_id
                left outer join tree_2 on names.name_id = tree_2.name_id
            order by name.full_name
				''',
                    { PreparedStatement qry ->
                        qry.setLong(1, n1.id)
                        qry.setLong(2, n2.id)
                        log.debug("about to execute the big query")
                        ResultSet rs = qry.executeQuery()
                        log.debug("got the resultset")
                        try {
                            while (rs.next()) {
                                Name n = Name.get(rs.getLong('name_id'))
                                Node nn1 = Node.get(rs.getLong('subnode_1'))
                                if (rs.wasNull()) nn1 = null
                                Node nn2 = Node.get(rs.getLong('subnode_2'))
                                if (rs.wasNull()) nn2 = null
                                Link l1 = Link.get(rs.getLong('link_1'))
                                if (rs.wasNull()) l1 = null
                                Link l2 = Link.get(rs.getLong('link_2'))
                                if (rs.wasNull()) l2 = null

                                log.debug("${n.fullName} ${nn1} ${l1} ${nn2} ${l2}")

                                if (l1 != null && l2 != null && l1.id == l2.id) {
                                    log.debug("just part of a common subtree")
                                }

                                assert nn1 != null || nn2 != null

                                boolean placement_changed =
                                        ((nn1 == null) != (nn2 == null)) ||
                                                ((l1 == null) != (l2 == null)) ||
                                                (l1 != null && l2 != null && l1.supernode.name?.id != l2.supernode.name?.id)

                                if (!placement_changed && nn1.id == nn2.id) {
                                    log.debug("same node under same name name. Continuing.")
                                    continue
                                }

                                List<String> changes = new ArrayList<String>()

                                if (placement_changed) {
                                    if (nn1 == null && nn2 != null) {
                                        if (l2 == null)
                                            changes.add("New placement")
                                        else
                                            changes.add("New placement under ${l2.supernode.name.fullName}")
                                    } else if (nn1 != null && nn2 == null) {
                                        if (l2 == null)
                                            changes.add("Name removed")
                                        else
                                            changes.add("Name removed from ${l1.supernode.name.fullName}")
                                    } else if (l1 == null && l2 != null) {
                                        changes.add("Name moved from root to ${l2.supernode.name.fullName}")
                                    } else if (l1 != null && l == null) {
                                        changes.add("Name moved from ${l1.supernode.name.fullName} to root")
                                    } else {
                                        changes.add("Name moved from ${l1.supernode.name.fullName} to ${l2.supernode.name.fullName} ")
                                    }
                                }

                                if (nn1 != null && nn2 != null && nn1.id != nn2.id) {
                                    if (nn1.instance?.id != nn2.instance?.id) {
                                        changes.add("reference changed from ${nn1.instance?.reference?.citation} to ${nn2.instance?.reference?.citation}")
                                    }

                                    if (nn1.typeUriIdPart != nn2.typeUriIdPart) {
                                        changes.add("type changed from ${nn1.typeUriIdPart} to ${nn2.typeUriIdPart}")
                                    }

                                    Map<Uri, Link> pp1 = DomainUtils.getProfileItemsAsMap(nn1)
                                    Map<Uri, Link> pp2 = DomainUtils.getProfileItemsAsMap(nn2)

                                    Set<Uri> items = new HashSet<Uri>()
                                    items.addAll(pp1.keySet())
                                    items.addAll(pp2.keySet())

                                    for (Uri u : items) {
                                        if (pp1.containsKey(u) && pp2.containsKey(u)) {
                                            if (pp1.get(u).subnode.literal != pp2.get(u).subnode.literal) {
                                                changes.add("Profile item ${DomainUtils.vnuForItem(pp1.get(u))?.title ?: u} changed")
                                            }
                                        } else if (!pp1.containsKey(u)) {
                                            changes.add("Profile item ${DomainUtils.vnuForItem(pp2.get(u))?.title ?: u} added")
                                        } else if (!pp2.containsKey(u)) {
                                            changes.add("Profile item ${DomainUtils.vnuForItem(pp2.get(u))?.title ?: u} removed")

                                        }
                                    }


                                }

                                // no filtering yet

                                if (!changes.isEmpty()) {
                                    l.add([name: n.fullName, changes: changes])
                                }
                            }
                            log.debug("done iterating though the resultset")

                        }
                        finally {
                            rs.close()
                        }

                    }
        }

        return l
    }

    static Name resolveName(Node node) {
        if (!node) return null
        if (node.name) return node.name
        if (!node.nameUriNsPart || !node.nameUriIdPart) return null
        if (node.nameUriNsPart.label == 'nsl-name') return Name.get(node.nameUriIdPart as Long)
        if (node.nameUriNsPart.label == 'apni-name') return Name.findByNamespaceAndSourceSystemAndSourceId(node.root.namespace, 'PLANT_NAME', node.nameUriIdPart as Long)
        return null
    }

    static Instance resolveInstance(Node node) {
        if (!node) return null
        if (node.instance) return node.instance
        if (!node.taxonUriNsPart || !node.taxonUriIdPart) return null
        if (node.taxonUriNsPart.label == 'nsl-instance') return Instance.get(node.taxonUriIdPart as Long)
        if (node.taxonUriNsPart.label == 'apni-taxon') return Instance.findByNamespaceAndSourceSystemAndSourceId(node.root.namespace, 'PLANT_NAME_REFERENCE', node.taxonUriIdPart as Long)
        return null
    }

    static class PlacementSpan {
        Link from
        Link to

        PlacementSpan(Link from, Link to) {
            this.from = from
            this.to = to
        }

        String toString() {
            return "${from.supernode.id}-${to.supernode.id}->${from.subnode.id}-${to.subnode.id}"
        }
    }

    // this is a rather nasty recursive routine whose job it is to boil down
    // the potentially thousands of placements of a node (owing to the way our tree works) into their tarry essence
    // we need to ignore placements that are the result of one of our sibling nodes causing a
    // synthetic move.

    static Collection<ArrayList<PlacementSpan>> getPlacementPaths(Node n) {

        Collection<ArrayList<PlacementSpan>> paths = new HashSet<ArrayList<PlacementSpan>>()

        // TODO: arrange something more formal with respect to the fact that we don't go higher than the root
        if (n == null || n.supLink.isEmpty() || n.internalType == NodeInternalType.S || n.typeUriIdPart == 'classification-root') {
            return paths
        }

        for (Link l : n.supLink) {
            Collection<ArrayList<PlacementSpan>> linkspans = getPlacementPaths(l.supernode)

            if (linkspans.isEmpty()) {
                linkspans.add(new ArrayList<PlacementSpan>())
            }

            for (ArrayList<PlacementSpan> linkspan_path : linkspans) {
                linkspan_path.add(new PlacementSpan(l, l))
            }
            paths.addAll(linkspans)
        }

        mergePaths(paths)

        return paths
    }

    static void mergePaths(Collection<ArrayList<PlacementSpan>> paths) {
        look_for_more_merges:
        for (; ;) {
            for (ArrayList<PlacementSpan> a : paths) {
                look_for_next_pair:
                for (ArrayList<PlacementSpan> b : paths) {
                    if (a == b) continue
                    if (a.size() != b.size()) continue
                    for (int i = 0; i < a.size(); i++) {
                        PlacementSpan aa = a.get(i)
                        PlacementSpan bb = b.get(i)

                        /* We only look at the supernode. This is ok, because the subnode of the final link of all paths will always be node n */

                        if (aa.to.supernode.id != bb.from.supernode.prev?.id) continue look_for_next_pair
                        if (!nodesLookTheSame(aa.to.supernode, bb.from.supernode)) continue look_for_next_pair
                        if (!linksLookTheSame(aa.to, bb.from)) continue look_for_next_pair
                    }
                    // right! we need to do a merge of b into a.

                    for (int i = 0; i < a.size(); i++) {
                        PlacementSpan aa = a.get(i)
                        PlacementSpan bb = b.get(i)
                        aa.to = bb.to
                    }
                    paths.remove(b)

                    /* and this will mess up the iterators, so restart the whole thing
                    yes, it's a load. but getting it right is a lot of code. Consider what happens when
                    we merge paths in the middle of a big sequence of paths needing to be merged.
                    */
                    continue look_for_more_merges
                }
            }

            break look_for_more_merges
        }
    }

    private static boolean nodesLookTheSame(Node n1, Node n2) {
        if (n1 == null && n2 == null) return true
        if (n1 == null || n2 == null) return false

        return uriSame(n1.typeUriNsPart, n1.typeUriIdPart, n2.typeUriNsPart, n2.typeUriIdPart) &&
                uriSame(n1.nameUriNsPart, n1.nameUriIdPart, n2.nameUriNsPart, n2.nameUriIdPart) &&
                uriSame(n1.taxonUriNsPart, n1.taxonUriIdPart, n2.taxonUriNsPart, n2.taxonUriIdPart) &&
                uriSame(n1.resourceUriNsPart, n1.resourceUriIdPart, n2.resourceUriNsPart, n2.resourceUriIdPart)
    }

    private static boolean linksLookTheSame(Link l1, Link l2) {
        if (l1 == null && l2 == null) return true
        if (l1 == null || l2 == null) return false

        return /* l1.linkSeq == l2.linkSeq && */ uriSame(l1.typeUriNsPart, l1.typeUriIdPart, l2.typeUriNsPart, l2.typeUriIdPart)
    }

    private static boolean uriSame(UriNs nsPart1, String idPart1, UriNs nsPart2, String idPart2) {
        return (nsPart1 == nsPart2) && ((idPart1 ?: '') == (idPart2 ?: ''))
    }
}
