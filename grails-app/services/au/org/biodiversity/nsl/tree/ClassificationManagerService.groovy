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
class ClassificationManagerService {
    QueryService queryService;
    TreeOperationsService treeOperationsService;
    BasicOperationsService basicOperationsService;
    VersioningService versioningService;
    DataSource dataSource_nsl;

    /**
     * Possible resultEnums returned by the validation result.
     */
    public static enum ValidationResult {
        /** param bundle will be [namespace, classification, nodeId] */
        BAD_REPLACEDAT,
        /** param bundle will be [namespace, classification, nodeId] */
                CURRENT_NO_PARENT,
        /** param bundle will be [namespace, classification, nameId] */
                NAME_APPEARS_TWICE,
        /** param bundle will be [namespace, classification, instanceId] */
                INSTANCE_APPEARS_TWICE,
        /** param bundle will be [namespace, classification, nodeId] */
                NODE_HAS_MULTIPLE_SUPERNODES
    }

    void createClassification(Map params = [:], Namespace namespace) throws ServiceException {
        // todo - use Peter's "must have" thing
        if (!namespace) throw new IllegalArgumentException("namespace must be specified");
        if (!params.label) throw new IllegalArgumentException("label must be specified");
        if (!params.description) throw new IllegalArgumentException("description must be specified");

        if (Arrangement.findByNamespaceAndLabel(namespace, params.label)) {
            ServiceException.raise(Message.makeMsg(Msg.createClassification, [params.label, Message.makeMsg(Msg.LABEL_ALREADY_EXISTS, [params.label])]));
        }

        List copyNodes;

        if (params.copyName) {
            if (!params.copyNameIn) throw new IllegalArgumentException("if copyName is specified, then copyNAmeIn must be specified");

            String copyName = params.copyName as String;
            Arrangement copyNameIn = params.copyNameIn as Arrangement;

            copyNodes = Node.findAll {
                root == copyNameIn && (name.simpleName == copyName || name.fullName == copyName) && checkedInAt != null && replacedAt == null
            }
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
            copyNodes = [DomainUtils.getSingleSubnode(copyNameIn.node)];
        } else {
            copyNodes = null;
        }

        Event e = basicOperationsService.newEvent(namespace, "Creating classification ${params.label}")
        Arrangement newClass = basicOperationsService.createClassification(e, params.label, params.description, params.shared)

        if (copyNodes) {
            log.debug "temp arrangement"
            Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(namespace)
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

        if (params['label'] != a.label && Arrangement.findByNamespaceAndLabel(a.namespace, params.label)) {
            ServiceException.raise(Message.makeMsg(Msg.updateClassification, [a, Message.makeMsg(Msg.LABEL_ALREADY_EXISTS, [params.label])]));
        }

        if (params['label']) a.label = params.label;
        if (params.containsKey('description')) a.description = params.description;

        if(params.containsKey('shared')) a.shared = params.shared;
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

        Arrangement.findAll { arrangementType == ArrangementType.P }.each {
            if (!validationResults.c[it.namespace.name]) validationResults.c[it.namespace.name] = [:]
            validationResults.c[it.namespace.name][it.label] = validate(it)
        }

        return validationResults
    }

    private validate(Arrangement classification) {
        def results = [];

        results.addAll(validate_replacedat_matches_nextnode(classification));
        results.addAll(validate_current_nodes_child_of_current_node(classification));
        results.addAll(validate_names_appear_once(classification));
        results.addAll(validate_instances_appear_once(classification));
        results.addAll(validate_current_nodes_have_one_curent_parent_node(classification));

        return results ?: [[msg: "No errors", level: 'success', nested: []]];
    }

    // TODO: namespace checks

    private validate_replacedat_matches_nextnode(Arrangement classification) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
SELECT count(*) AS ct
FROM
  tree_arrangement a
  JOIN tree_node n ON a.id = n.tree_arrangement_id
WHERE
  a.id = ?
  AND (
    (n.next_node_id IS NULL AND n.replaced_at_id IS NOT NULL)
    OR
    (n.next_node_id IS NOT NULL AND n.replaced_at_id IS NULL)
  )
            """, [classification.id]).ct

            if (ct > 0) {
                def msg = [msg: "There are ${ct} nodes where replaced_at does not match next_node", level: 'danger', nested: []]
                result << msg

                sql.eachRow("""
SELECT n.id, n.next_node_id, n.replaced_at_id
FROM
  tree_arrangement a
  JOIN tree_node n ON a.id = n.tree_arrangement_id
WHERE
  a.id = ?
  AND (
    (n.next_node_id IS NULL AND n.replaced_at_id IS NOT NULL)
    OR
    (n.next_node_id IS NOT NULL AND n.replaced_at_id IS NULL)
  )
LIMIT 5
            """, [classification.id]) {
                    msg.nested << [
                            msg   : "Node ${it.id} has a next node ${it.next_node_id ?: 'null'} and a replaced at of ${it.replaced_at_id ?: 'null'}",
                            level : 'danger',
                            type  : ValidationResult.BAD_REPLACEDAT,
                            params: [namespace: classification.namespace.name, classification: classification.label, nodeId: it.id]
                    ]
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }

    private validate_current_nodes_child_of_current_node(Arrangement classification) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
SELECT count(*) AS ct
FROM
  tree_arrangement a
  JOIN tree_node n ON a.id = n.tree_arrangement_id
WHERE
  a.id = ?
  AND n.next_node_id IS NULL
  AND n.internal_type IN ('T','D')
  AND NOT exists (
    SELECT pn.id
    FROM tree_link l
    JOIN tree_node pn ON l.supernode_id = pn.id
    WHERE l.subnode_id = n.id
      AND pn.tree_arrangement_id=n.tree_arrangement_id
      AND pn.next_node_id IS NULL
  )
LIMIT 5
            """, [classification.id]).ct

            if (ct > 0) {
                def msg = [msg: "There are ${ct} nodes which are current but have no current parent node", level: 'danger', nested: []]
                result << msg

                sql.eachRow("""
SELECT n.id
FROM
  tree_arrangement a
  JOIN tree_node n ON a.id = n.tree_arrangement_id
WHERE
  a.id = ?
  AND n.next_node_id IS NULL
  AND n.internal_type IN ('T','D')
  AND NOT exists (
    SELECT pn.id
    FROM tree_link l
    JOIN tree_node pn ON l.supernode_id = pn.id
    WHERE l.subnode_id = n.id
      AND pn.tree_arrangement_id=n.tree_arrangement_id
      AND pn.next_node_id IS NULL
  )
LIMIT 5
            """, [classification.id]) {
                    Node n = Node.get(it.id)
                    msg.nested << [
                            msg   : "Node ${it.id} ${n?.name?.fullName} is current but has no current parent node",
                            level : 'danger',
                            type  : ValidationResult.CURRENT_NO_PARENT,
                            params: [namespace: classification.namespace.name, classification: classification.label, nodeId: it.id]
                    ]
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }

    private validate_names_appear_once(Arrangement classification) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
SELECT count(*) AS ct FROM (
  SELECT n.name_id, count(*) AS ct
  FROM
    tree_arrangement a
    JOIN tree_node n ON a.id = n.tree_arrangement_id
  WHERE
    a.id = ?
    AND n.name_id IS NOT NULL
    AND n.next_node_id IS NULL
  GROUP BY n.name_id
  HAVING count(*) > 1
) AS multiname
            """, [classification.id]).ct

            if (ct > 0) {
                def msg = [msg: "There are ${ct} names appearing multiple times", level: 'warning', nested: []]
                result << msg

                sql.eachRow("""
  SELECT n.name_id, count(*) AS ct
  FROM
    tree_arrangement a
    JOIN tree_node n ON a.id = n.tree_arrangement_id
  WHERE
    a.id = ?
    AND n.name_id IS NOT NULL
    AND n.next_node_id IS NULL
  GROUP BY n.name_id
  HAVING count(*) > 1
  LIMIT 5
            """, [classification.id]) {
                    msg.nested << [msg   : "${Name.get(it.name_id).fullName} appears ${it.ct} times (name id: ${it.name_id})",
                                   level : 'warning',
                                   type  : ValidationResult.NAME_APPEARS_TWICE,
                                   params: [namespace: classification.namespace.name, classification: classification.label, nameId: it.name_id]
                    ]
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }

    private validate_instances_appear_once(Arrangement classification) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
SELECT count(*) AS ct FROM (
  SELECT n.instance_id, count(*) AS ct
  FROM
    tree_arrangement a
    JOIN tree_node n ON a.id = n.tree_arrangement_id
  WHERE
    a.id = ?
    AND n.instance_id IS NOT NULL
    AND n.next_node_id IS NULL
  GROUP BY n.instance_id
  HAVING count(*) > 1
) AS multiname
            """, [classification.id]).ct

            if (ct > 0) {
                def msg = [msg: "There are ${ct} instances appearing multiple times", level: 'warning', nested: []]
                result << msg

                sql.eachRow("""
  SELECT n.instance_id, count(*) AS ct
  FROM
    tree_arrangement a
    JOIN tree_node n ON a.id = n.tree_arrangement_id
  WHERE
    a.id = ?
    AND n.instance_id IS NOT NULL
    AND n.next_node_id IS NULL
  GROUP BY n.instance_id
  HAVING count(*) > 1
  LIMIT 5
            """, [classification.id]) {
                    Instance i = Instance.get(it.instance_id)
                    msg.nested << [
                            msg   : "${i.name.fullName} in ${i.reference.title} ${i.reference?.author?.name} appears ${it.ct} times (instance id: ${it.instance_id})",
                            level : 'warning',
                            type  : ValidationResult.INSTANCE_APPEARS_TWICE,
                            params: [namespace: classification.namespace.name, classification: classification.label, instanceId: it.instance_id]
                    ]
                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }


    private validate_current_nodes_have_one_curent_parent_node(Arrangement classification) {
        def result = []

        Sql sql = Sql.newInstance(dataSource_nsl);
        try {
            def ct = sql.firstRow("""
SELECT count(*) AS ct
FROM (
SELECT n.id AS node_id
  FROM
    tree_arrangement a
    JOIN tree_node n ON a.id = n.tree_arrangement_id
    JOIN tree_link l ON n.id = l.subnode_id
    JOIN tree_node nsup ON nsup.id =  l.supernode_id AND a.id = nsup.tree_arrangement_id
  WHERE
    a.id = ?
    AND n.next_node_id IS NULL
    AND nsup.next_node_id IS NULL
    AND n.internal_type ='T'
  GROUP BY a.label, n.id
  HAVING count(nsup.id) > 1
)
subq
        """, [classification.id]).ct

            if (ct > 0) {
                def msg = [msg: "There are ${ct} current nodes with multiple current supernodes", level: 'danger', nested: []]
                result << msg

                sql.eachRow("""
SELECT n.id AS node_id, count(nsup.id) AS ct
  FROM
    tree_arrangement a
    JOIN tree_node n ON a.id = n.tree_arrangement_id
    JOIN tree_link l ON n.id = l.subnode_id
    JOIN tree_node nsup ON nsup.id =  l.supernode_id AND a.id = nsup.tree_arrangement_id
  WHERE
    a.id = ?
    AND n.next_node_id IS NULL
    AND nsup.next_node_id IS NULL
    AND n.internal_type ='T'
  GROUP BY a.label, n.id
  HAVING count(nsup.id) > 1
  LIMIT 20
            """, [classification.id]) {
                    Node n = Node.get(it.node_id)
                    def msg1 = [
                            msg   : "Node ${it.node_id} ${n.name?.fullName} has ${it.ct} current supernodes",
                            level : 'danger',
                            type  : ValidationResult.NODE_HAS_MULTIPLE_SUPERNODES,
                            params: [namespace: classification.namespace.name, classification: classification.label, nodeId: it.node_id],
                            nested: []
                    ]

                    msg.nested << msg1

                    n.supLink.collect { it.supernode }.findAll { it.next == null }.each {
                        msg1.nested << [
                                msg  : "Node ${it.id} ${it.name?.fullName}",
                                level: 'info'
                        ]
                    }

                }

            }
        }
        finally {
            sql.close();
        }

        return result
    }


    void fixClassificationUseNodeForName(Arrangement classification, Name name, Node node) {
        if (!classification) throw new IllegalArgumentException('classification cannot be null');
        if (!name) throw new IllegalArgumentException('name cannot be null');
        if (!node) throw new IllegalArgumentException('node cannot be null');
        if (node.root != classification) throw new IllegalArgumentException('node root must match classification');
        if (node.name != name) throw new IllegalArgumentException('node name must match name');

        // right! Find all the other nodes in this classification for this name
        // I'll store the ID because grails and hibernate and native sql play together so badly

        Collection<Long> nodesToZap = new HashSet<Long>(Node.findAllByRootAndNameAndReplacedAt(classification, name, null).findAll {
            it.id != node.id
        }*.id);

        log.debug "temp arrangement"
        Arrangement tempSpace = basicOperationsService.createTemporaryArrangement(classification.namespace)

        Link rootLink = basicOperationsService.adoptNode(Node.get(tempSpace.node.id), DomainUtils.getSingleSubnode(Arrangement.get(classification.id).node), VersioningMethod.F);
        basicOperationsService.checkoutLink(Link.get(rootLink.id));
        rootLink = DomainUtils.refetchLink(rootLink);

        // ok. checkout the target node
        log.debug "checkout ${node}"
        Node checkedOutNode = basicOperationsService.checkoutNode(Node.get(tempSpace.node.id), Node.get(node.id));

        nodesToZap.each {
            log.debug "zapping node ${Node.get(it)}"

            Link currentLink = DomainUtils.getSingleCurrentSuperlinkInTree(Node.get(it));
            Link parentLink = queryService.findNodeCurrentOrCheckedout(Node.get(tempSpace.node.id), Node.get(currentLink.supernode.id));
            Node parentNode = parentLink.subnode;
            if (parentNode.root.id != tempSpace.id) {
                parentNode = basicOperationsService.checkoutNode(Node.get(tempSpace.node.id), Node.get(parentNode.id));
            }

            // remove node from the checked out parent
            basicOperationsService.deleteLink(Node.get(parentNode.id), currentLink.linkSeq)

            // adopt all taxonomic child nodes onto new chosen node
            Node.get(it).subLink.findAll { it.subnode.internalType == NodeInternalType.T }.each {
                basicOperationsService.adoptNode(Node.get(checkedOutNode.id), Node.get(it.subnode.id), it.versioningMethod, linkType: DomainUtils.getLinkTypeUri(it))
            }
        }

        Event e = basicOperationsService.newEvent(classification.namespace, "fixClassificationUseNodeForName(${classification.id}, ${name.id}, ${node.id})")

        log.debug "persist"
        basicOperationsService.persistNode(e, Arrangement.get(tempSpace.id).node)

        log.debug "version"
        tempSpace = DomainUtils.refetchArrangement(tempSpace)
        classification = DomainUtils.refetchArrangement(classification)
        Map<Node, Node> versioning = versioningService.getStandardVersioningMap(tempSpace, classification);
        nodesToZap.each { Long it ->
            versioning.put(Node.get(it), Node.get(0))
        }

        versioningService.performVersioning(e, versioning, tempSpace)

        log.debug "cleanup"
        tempSpace = DomainUtils.refetchArrangement(tempSpace);
        classification = DomainUtils.refetchArrangement(classification);
        basicOperationsService.moveFinalNodesFromTreeToTree(tempSpace, classification)

        tempSpace = DomainUtils.refetchArrangement(tempSpace);
        basicOperationsService.deleteArrangement(tempSpace)

        log.debug "done"
    }

    def fixClassificationEndDates(Arrangement classification, boolean dry_run = true) {
        def result = [:]
        result.nodes = []

        Sql sql = Sql.newInstance(dataSource_nsl);

        String subq = """
WITH RECURSIVE
nnodes AS (
  SELECT node_id AS id FROM tree_arrangement a WHERE a.id = ?
UNION ALL
  SELECT l.subnode_id AS id FROM nnodes
    JOIN tree_link l ON nnodes.id = l.supernode_id
    JOIN tree_node n ON l.subnode_id = n.id
  WHERE n.internal_type <> 'V'
),
nodes AS ( SELECT DISTINCT id FROM nnodes)
SELECT id FROM tree_node n
WHERE n.internal_type <> 'V'
AND n.tree_arrangement_id = ?
AND n.replaced_at_id IS NULL
AND n.id NOT IN (SELECT id FROM nodes)
            """

        try {
            if (dry_run) {
                sql.eachRow(subq, [classification.id, classification.id]) {
                    result.nodes << Node.get(it.id)
                }
            }
            else {
                Event e = basicOperationsService.newEvent(classification.namespace, "fixClassificationEndDates(${classification.id})")
                sql.execute("""
  update tree_node set replaced_at_id = ?, next_node_id = 0
  where id in (
    ${subq}
  )
""", [e.id, classification.id, classification.id]
                )
            }
        }
        finally {
            sql.close();
        }

        return result
    }

    Arrangement rebuildNameTree(Namespace namespace, String label, String description) {
        if(!label) throw IllegalArgumentException("label cannot be null");

        Arrangement tree = Arrangement.findByNamespaceAndLabel(namespace, label)

        if(tree) {
            basicOperationsService.deleteArrangement(tree);
            tree = null;
        }

        Event e = basicOperationsService.newEvent(namespace, "rebuild name tree ${label}")
        tree = basicOperationsService.createClassification(e, label, description, true)

        Node topNode = DomainUtils.getSingleSubnode(tree.node);

        UriNs nslName = UriNs.findByLabel('nsl-name')

        Sql sql = Sql.newInstance(dataSource_nsl);

        sql.execute """
insert into tree_node(
  id, lock_version,
  tree_arrangement_id, checked_in_at_id, internal_type,
  is_synthetic, type_uri_ns_part_id, type_uri_id_part,
  name_id, name_uri_ns_part_id, name_uri_id_part
)
select
nextval('nsl_global_seq'),  -- id,
1,   -- lock_version,
?,  -- tree_arrangement_id,
?,   -- checked_in_at_id,
'T',  -- internal_type,
'Y',  -- is_synthetic,
1,  -- type_uri_ns_part_id,
'placement',  -- type_uri_id_part
n.id,  -- name_id,
?,  -- name_uri_ns_part_id,
n.id  -- name_uri_id_part
from name n join name_type nt on n.name_type_id = nt.id and nt.scientific = true
where n.namespace_id = ?
""", [
                tree.id,        // tree_arrangement_id,
                e.id,           // checked_in_at_id,
                nslName.id,     // name_uri_ns_part_id,
                namespace.id    // where n.namespace_id = ?
    ]


        sql.execute """
insert into tree_link (id, lock_version, link_seq, supernode_id, subnode_id, is_synthetic, type_uri_ns_part_id, type_uri_id_part, versioning_method)
SELECT
nextval('nsl_global_seq'), -- id,
1, -- lock_version,
n.id, -- link_seq,
coalesce(n2.id, ?),-- supernode_id,
n.id, -- subnode_id,
'Y', -- is_synthetic,
0, -- type_uri_ns_part_id,
NULL, -- type_uri_id_part,
'V' -- versioning_method
from tree_node n
join name on n.name_id = name.id
left outer join tree_node n2 on n2.name_id = name.parent_id and n2.tree_arrangement_id = ?
where n.tree_arrangement_id = ?
""", [
        topNode.id,        // coalesce(n2.id, ?),-- supernode_id,
        tree.id,         // n2.tree_arrangement_id = ?
        tree.id        // n.tree_arrangement_id = ?
     ]

        return tree
    }


    void dump(Node n, int d = 0, Arrangement tree = null) {
        n = Node.get(n.id);
        if (tree == null) tree = n.root;
        String indent = '*   *   *   *   *   *   *   *   *   *   *   *   *   *   '.substring(0, d * 4);
        log.debug "${indent} ${n} ${n.internalType} P:${n.prev?.id} N:${n.next?.id} R:${n.root?.id} NAME:${n.name} INST:${n.instance?.id} ${n.typeUriIdPart} ${n.resourceUriIdPart} ${n.literal}"

        if (n.root == tree) {
            n.subLink.each { Link l ->
                if (l.subnode.internalType != NodeInternalType.V)
                    dump(l.subnode, d + 1, tree);
            }
        }
    }

    String ll(Link l) {
        l = DomainUtils.refetchLink(l)
        return "LINK ${l.id} aka ${l.supernode.id}/${l.linkSeq}: ${l.supernode.id} -${l.typeUriIdPart}> ${l.subnode.id}"
    }
}
