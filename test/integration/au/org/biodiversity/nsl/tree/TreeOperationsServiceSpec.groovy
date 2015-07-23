package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.UriNs
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.security.Principal
import java.sql.Timestamp

/**
 *
 */
@Mixin(BuildSampleTreeMixin)
class TreeOperationsServiceSpec extends Specification {

    def treeOperationsService
    def basicOperationsService
    def queryService
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl

    def cleanup() {
    }

    void "test standard namespaces"() {
        when:
        UriNs nameNs = treeOperationsService.nslNameNs();
        UriNs instanceNs = treeOperationsService.nslInstanceNs();

        then:
        nameNs
        instanceNs
    }

    void "test find a current node"() {
        when:
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test find a current node')
        SomeStuff tree = makeSampleTree()

        Arrangement arrangement = tree.t
        Node ab = tree.ab
        basicOperationsService.persistNode(event, tree.root)
        tree.reloadWithoutClear()
        ab = DomainUtils.refetchNode(ab)


        Uri uri = DomainUtils.uri('afd-name', 'AB')
        List<Node> nodes = queryService.findCurrentName(arrangement, uri)

        then:
        nodes.size() == 1
        nodes.first() == ab

    }

    void "test find a current taxon"() {
        when:
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test find a current taxon')
        SomeStuff tree = makeSampleTree()

        Arrangement arrangement = tree.t
        Node aa = tree.aa
        basicOperationsService.persistNode(event, tree.root)
        tree.reloadWithoutClear()
        aa = DomainUtils.refetchNode(aa)

        Uri uri = DomainUtils.uri('afd-taxon', 'AA')
        List<Node> nodes = queryService.findCurrentTaxon(arrangement, uri)

        then:
        nodes.size() == 1
        nodes.first() == aa
    }

    void "test add node"() {
        when:
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test add node')
        Arrangement arrangement = basicOperationsService.createClassification(event, 'test', 'test add node')

        if (!DomainUtils.getSingleSubnode(arrangement.node)) {
            queryService.dumpNodes([arrangement.node])
            throw new IllegalArgumentException("Classification ${arrangement} is not a properly-formed tree")
        }

        Uri nameUri = DomainUtils.uri('afd-name', 'add-node-test')
        Uri taxonUri = DomainUtils.uri('afd-taxon', 'add-node-test')
        Uri defaultNodeType = DomainUtils.getDefaultNodeTypeUri()
        Uri defaultLinkType = DomainUtils.getDefaultLinkTypeUri()

        Node newName = treeOperationsService.addName(
                arrangement,
                nameUri,
                null,
                taxonUri,
                nodeTypeUri: defaultNodeType,
                linkTypeUri: defaultLinkType,
                event.authUser
        )

        arrangement = DomainUtils.refetchArrangement(arrangement)

        then:
        newName != null
        queryService.findCurrentName(arrangement, nameUri)?.first() == newName
    }

    void "test add node with bad name id"() {
        when:
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test add node with bad name id')
        Arrangement arrangement = basicOperationsService.createClassification(event, 'test', 'test add node with bad name id')

        if (!DomainUtils.getSingleSubnode(arrangement.node)) {
            queryService.dumpNodes([arrangement.node])
            throw new IllegalArgumentException("Classification ${arrangement} is not a properly-formed tree")
        }

        Uri nameUri = DomainUtils.u(treeOperationsService.nslNameNs(), '-123')
        Uri taxonUri = DomainUtils.uri('afd-taxon', 'add-node-test')
        Uri defaultNodeType = DomainUtils.getDefaultNodeTypeUri()
        Uri defaultLinkType = DomainUtils.getDefaultLinkTypeUri()

        Node newName = treeOperationsService.addName(
                arrangement,
                nameUri,
                null,
                taxonUri,
                nodeTypeUri: defaultNodeType,
                linkTypeUri: defaultLinkType,
                event.authUser
        )

        then:
        thrown IllegalArgumentException
    }

    void "test add node with bad taxon id"() {
        when:
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test add node with bad name id')
        Arrangement arrangement = basicOperationsService.createClassification(event, 'test', 'test add node with bad name id')

        if (!DomainUtils.getSingleSubnode(arrangement.node)) {
            queryService.dumpNodes([arrangement.node])
            throw new IllegalArgumentException("Classification ${arrangement} is not a properly-formed tree")
        }

        Uri nameUri = DomainUtils.uri('afd-name', 'add-node-test')
        Uri taxonUri = DomainUtils.u(treeOperationsService.nslInstanceNs(), '-123')
        Uri defaultNodeType = DomainUtils.getDefaultNodeTypeUri()
        Uri defaultLinkType = DomainUtils.getDefaultLinkTypeUri()

        Node newName = treeOperationsService.addName(
                arrangement,
                nameUri,
                null,
                taxonUri,
                nodeTypeUri: defaultNodeType,
                linkTypeUri: defaultLinkType,
                event.authUser
        )

        then:
        thrown IllegalArgumentException
    }

    void "test changing a node"() {
        when: "we create a new tree and add a couple of nodes"
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test changing a node')
        Arrangement arrangement = basicOperationsService.createClassification(event, 'test', 'test add node')

        Uri name1Uri = DomainUtils.uri('afd-name', '1')
        Uri name2Uri = DomainUtils.uri('afd-name', '2')

        Node name1 = treeOperationsService.addName(
                arrangement,
                name1Uri,
                null,
                DomainUtils.uri('afd-taxon', '1'),
                event.authUser
        )


        arrangement = DomainUtils.refetchArrangement(arrangement)
        name1Uri = DomainUtils.uri('afd-name', '1')
        name2Uri = DomainUtils.uri('afd-name', '2')

        Node name2 = treeOperationsService.addName(
                arrangement,
                name2Uri,
                null,
                DomainUtils.uri('afd-taxon', '2'),
                event.authUser
        )

        arrangement = DomainUtils.refetchArrangement(arrangement)
        name1Uri = DomainUtils.refetchUri(name1Uri)
        name2Uri = DomainUtils.refetchUri(name2Uri)
        name1 = DomainUtils.refetchNode(name1)
        name2 = DomainUtils.refetchNode(name2)



        then: "those nodes exist and are siblings"

        name1 != null
        name2 != null
        name1.supLink.size() == 2
        name2.supLink.size() == 1

        DomainUtils.isCurrent(name1)
        DomainUtils.isCurrent(name2)

        when: "we move node 2 under node 1"

        treeOperationsService.updateName(arrangement, DomainUtils.getNameUri(name2), DomainUtils.getNameUri(name1), DomainUtils.getTaxonUri(name2), event.authUser)
        name1Uri = DomainUtils.refetchUri(name1Uri)
        name2Uri = DomainUtils.refetchUri(name2Uri)
        name1 = DomainUtils.refetchNode(name1)
        name2 = DomainUtils.refetchNode(name2)

        Node name1afterMove = queryService.findCurrentName(arrangement, name1Uri).first()
        Node name2afterMove = queryService.findCurrentName(arrangement, name2Uri).first()

        then: "Current Name1 and Name2 should exist and be linked"

        name1afterMove != null
        name2afterMove != null

        name1 != name1afterMove
        name2 == name2afterMove

        name1afterMove.supLink.size() == 1
        name2afterMove.supLink.size() == 2

    }

    def "test deleting a node"() {
        when: "we create a new tree and add a couple of nodes"
        Event event = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test deleting a node')
        Arrangement arrangement = basicOperationsService.createClassification(event, 'test', 'test add node')

        Uri name1Uri = DomainUtils.uri('afd-name', '1')

        Node name1 = treeOperationsService.addName(
                arrangement,
                name1Uri,
                null,
                DomainUtils.uri('afd-taxon', '1'),
                event.authUser
        )

        arrangement = DomainUtils.refetchArrangement(arrangement)
        name1Uri = DomainUtils.refetchUri(name1Uri)
        name1 = DomainUtils.refetchNode(name1)

        Collection namePlacement = queryService.findCurrentName(arrangement, name1Uri)

        then: "name1 is placed"
        namePlacement.size() == 1

        DomainUtils.isCurrent(name1)

        when: "we delete name1"
        treeOperationsService.deleteName(arrangement, DomainUtils.getNameUri(name1), null, event.authUser)
        name1Uri = DomainUtils.refetchUri(name1Uri)
        name1 = DomainUtils.refetchNode(name1)
        namePlacement = queryService.findCurrentName(arrangement, name1Uri)

        then: "name1 should be gone"
        namePlacement.size() == 0
    }
}

class FakePrincipal implements Principal {

    @Override
    String getName() {
        return 'fred'
    }
}