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
import groovy.sql.Sql

import javax.sql.DataSource

@Transactional(rollbackFor = [ServiceException])
class ClassificationManagerService {
    TreeOperationsService treeOperationsService;
    BasicOperationsService basicOperationsService;
    VersioningService versioningService;
    DataSource dataSource_nsl;

    void createClassification(Map params = [:]) throws ServiceException {
        // todo - use Peter's "must have" thing
        if (!params.label) throw new IllegalArgumentException("label must be specified");
        if (!params.description) throw new IllegalArgumentException("description must be specified");

        if (Arrangement.findByLabel(params.label)) {
            ServiceException.raise(Message.makeMsg(Msg.createClassification, [params.label, Message.makeMsg(Msg.LABEL_ALREADY_EXISTS, [params.label])]));
        }

        List copyNodes;

        if (params.copyName) {
            if (!params.copyNameIn) throw new IllegalArgumentException("if copyName is specified, then copyNAmeIn must be specified");

            String copyName = params.copyName as String;
            Arrangement copyNameIn = params.copyNameIn as Arrangement;

            copyNodes = Node.findAll { root == copyNameIn && (name.simpleName == copyName || name.fullName == copyName) && checkedInAt != null && replacedAt == null }
            int count = copyNodes.size();

            if (count == 0) {
                ServiceException.raise(Message.makeMsg(Msg.createClassification, [
                        params.label,
                        Message.makeMsg(Msg.THING_NOT_FOUND_IN_ARRANGEMENT, [
                                copyNameIn, copyName, 'Name'])]));
            }

            // ok. Although it should never happen, we need to handle the case where a name matches two nodes and one is a subnode of another

            Set<Node> mightHaveOtherNamesBelowIt = new HashSet<Node>();
            mightHaveOtherNamesBelowIt.addAll(copyNodes)

            rescan_copy_nodes:
            for (; ;) {
                for (Iterator<Node> n1_it = mightHaveOtherNamesBelowIt.iterator(); n1_it.hasNext();) {
                    Node n1 = n1_it.next();
                    for (Node n2 : copyNodes) {
                        if (n1 != n2 && higherThan(n1, n2)) {
                            copyNodes.remove(n2);
                            // this gets rid of "collection has changed wile I was iterating through it" issues.
                            continue rescan_copy_nodes;
                        }
                    }
                    n1_it.remove();

                }
                break rescan_copy_nodes;
            }

        } else if (params.copyNameIn) {
            Arrangement copyNameIn = params.copyNameIn as Arrangement;
            copyNodes = [ DomainUtils.getSingleSubnode(copyNameIn.node) ];
        } else {
            copyNodes = null;
        }

        Event e = basicOperationsService.newEvent("Creating classification ${params.label}")
        Arrangement newClass = basicOperationsService.createClassification(e, params.label, params.description)

        if (copyNodes) {
            log.debug "temp arrangement"
            Arrangement tempSpace = basicOperationsService.createTemporaryArrangement()
            newClass = DomainUtils.refetchArrangement(newClass)
            tempSpace = DomainUtils.refetchArrangement(tempSpace)
            Node oldRootNode = DomainUtils.getSingleSubnode(newClass.node);
            Link newRootLink = basicOperationsService.adoptNode(tempSpace.node, oldRootNode, VersioningMethod.F);
            basicOperationsService.checkoutLink(newRootLink);
            newRootLink = DomainUtils.refetchLink(newRootLink);
            Node newRootNode = newRootLink.subnode;

            copyNodes.each { Node copyNode ->
                log.debug "adopt"
                newRootNode = DomainUtils.refetchNode(newRootNode)
                copyNode = DomainUtils.refetchNode(copyNode)
                Link link = basicOperationsService.adoptNode(newRootNode, copyNode, VersioningMethod.V,
                        linkType: DomainUtils.getBoatreeUri('classification-top-node')
                )
            }

            log.debug "checkout"
            basicOperationsService.massCheckoutWithSubnodes(newRootNode, copyNodes);

            log.debug "persist"
            newRootNode = DomainUtils.refetchNode(newRootNode)
            basicOperationsService.persistNode(e, newRootNode)

            log.debug "version"
            newClass = DomainUtils.refetchArrangement(newClass);
            oldRootNode = DomainUtils.refetchNode(oldRootNode)
            newRootNode = DomainUtils.refetchNode(newRootNode)
            Map<Node, Node> replacementMap = new HashMap<Node, Node>()
            replacementMap.put(oldRootNode, newRootNode);
            versioningService.performVersioning(e, replacementMap, newClass)

            log.debug "cleanup"
            tempSpace = DomainUtils.refetchArrangement(tempSpace);
            newClass = DomainUtils.refetchArrangement(newClass);
            basicOperationsService.moveFinalNodesFromTreeToTree(tempSpace, newClass)

            tempSpace = DomainUtils.refetchArrangement(tempSpace);
            basicOperationsService.deleteArrangement(tempSpace)
        }
    }

    void updateClassification(Map params = [:], Arrangement a) throws ServiceException {
        // todo - use Peter's "must have" thing
        if (!a) throw new IllegalArgumentException("Arrangement must be specified")
        if (!params.label) throw new IllegalArgumentException("label must be specified");
        if (!params.description) throw new IllegalArgumentException("description must be specified");

        if (params['label'] != a.label && Arrangement.findByLabel(params.label)) {
            ServiceException.raise(Message.makeMsg(Msg.updateClassification, [a, Message.makeMsg(Msg.LABEL_ALREADY_EXISTS, [params.label])]));
        }

        if (params['label']) a.label = params.label;
        if (params.containsKey('description')) a.description = params.description;
        a.save();
    }

    void deleteClassification(Arrangement a) throws ServiceException {
        // todo - use Peter's "must have" thing
        if (!a) throw new IllegalArgumentException("Arrangement must be specified")
        basicOperationsService.deleteArrangement(a)
    }

    /**
     * Is Node A higher in the tree than Node B.
     * This method assumes that it is operating on well-formed classification trees, and it will throw errors if it is not.
     * @param a
     * @param b
     * @return true if a higher than b (a>b)
     */
    private static boolean higherThan(Node a, Node b) {
        Node pointer = b
        while (pointer && pointer != a) {
            // getSingleSupernode only sees *current* nodes. There should only be one.
            // this will start failing when we have multiple trees, dammit.
            pointer = DomainUtils.getSingleSupernode(pointer)
        }
        return pointer != null
    }

    def validateClassifications() {
        def validationResults = [:];
        validationResults.time = new Date();

        validationResults.c = [:];

        Arrangement.findAll { arrangementType == ArrangementType.P} .each {
            validationResults.c[it.label] = validate(it.label)
        }

        return validationResults
    }

    private validate(String label) {
        def results = [];

        results.addAll(validate_replacedat_matches_nextnode(label));
        results.addAll(validate_current_nodes_child_of_current_node(label));
        results.addAll(validate_names_appear_once(label));
        results.addAll(validate_instances_appear_once(label));

        return results;
    }


    private validate_replacedat_matches_nextnode(String label) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
select count(*) as ct
from
  tree_arrangement a
  join tree_node n on a.id = n.tree_arrangement_id
where
  a.label = '${label}'
  and (
    (n.next_node_id is null and n.replaced_at_id is not null)
    or
    (n.next_node_id is not null and n.replaced_at_id is null)
  )
            """).ct

            if(ct > 0) {
                result << "There are ${ct} nodes where replaced_at does not match next_node"

                sql.eachRow("""
select n.id, n.next_node_id, n.replaced_at_id
from
  tree_arrangement a
  join tree_node n on a.id = n.tree_arrangement_id
where
  a.label = '${label}'
  and (
    (n.next_node_id is null and n.replaced_at_id is not null)
    or
    (n.next_node_id is not null and n.replaced_at_id is null)
  )
LIMIT 5
            """) {
                    result << "Node ${it.id} has a next node ${it.next_node_id?:'null'} and a replaced at of ${it.replaced_at_id?:'null'}"
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }

    private validate_current_nodes_child_of_current_node(String label) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
select count(*) as ct
from
  tree_arrangement a
  join tree_node n on a.id = n.tree_arrangement_id
where
  a.label = '${label}'
  and n.next_node_id is null
  and n.internal_type in ('T','D')
  and not exists (
    select pn.id
    from tree_link l
    join tree_node pn on l.supernode_id = pn.id
    join tree_arrangement pa on pn.tree_arrangement_id = pa.id
    where l.subnode_id = n.id
      and pa.label='${label}'
      and pn.next_node_id is null
  )
LIMIT 5
            """).ct

            if(ct > 0) {
                result << "There are ${ct} nodes which are current but have no current parent node"

                sql.eachRow("""
select n.id
from
  tree_arrangement a
  join tree_node n on a.id = n.tree_arrangement_id
where
  a.label = '${label}'
  and n.next_node_id is null
  and n.internal_type in ('T','D')
  and not exists (
    select pn.id
    from tree_link l
    join tree_node pn on l.supernode_id = pn.id
    join tree_arrangement pa on pn.tree_arrangement_id = pa.id
    where l.subnode_id = n.id
      and pa.label='${label}'
      and pn.next_node_id is null
  )
LIMIT 5
            """) {
                    result << "Node ${it.id} is current but has no current parent node"
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }

    private validate_names_appear_once(String label) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
select count(*) as ct from (
  select n.name_id, count(*) as ct
  from
    tree_arrangement a
    join tree_node n on a.id = n.tree_arrangement_id
  where
    a.label = '${label}'
    and n.name_id is not null
    and n.next_node_id is null
  group by n.name_id
  having count(*) > 1
) as multiname
            """).ct

            if(ct > 0) {
                result << "There are ${ct} names appearing multiple times"

                sql.eachRow("""
  select n.name_id, count(*) as ct
  from
    tree_arrangement a
    join tree_node n on a.id = n.tree_arrangement_id
  where
    a.label = '${label}'
    and n.name_id is not null
    and n.next_node_id is null
  group by n.name_id
  having count(*) > 1
  LIMIT 5
            """) {
                    result << "${Name.get(it.name_id).fullName} appears ${it.ct} times (name id: ${it.name_id})"
                }

            }
        }
        finally {
            sql.close();
        }

         return result
    }

    private validate_instances_appear_once(String label) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
select count(*) as ct from (
  select n.instance_id, count(*) as ct
  from
    tree_arrangement a
    join tree_node n on a.id = n.tree_arrangement_id
  where
    a.label = '${label}'
    and n.instance_id is not null
    and n.next_node_id is null
  group by n.instance_id
  having count(*) > 1
) as multiname
            """).ct

            if(ct > 0) {
                result << "There are ${ct} instances appearing multiple times"

                sql.eachRow("""
  select n.instance_id, count(*) as ct
  from
    tree_arrangement a
    join tree_node n on a.id = n.tree_arrangement_id
  where
    a.label = '${label}'
    and n.instance_id is not null
    and n.next_node_id is null
  group by n.instance_id
  having count(*) > 1
  LIMIT 5
            """) {
                    Instance i = Instance.get(it.instance_id)
                    result << "${i.name.fullName} in ${i.reference.title} ${i.reference?.author?.name} appears ${it.ct} times (instance id: ${it.instance_id})"
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }
}
