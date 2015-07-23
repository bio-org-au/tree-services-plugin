package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Link
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.VersioningMethod
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Timestamp

@Mixin(BuildSampleTreeMixin)
class CheckoutSpec extends Specification {
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl
    static final Log log = LogFactory.getLog(CheckoutSpec.class)

    // fields
    BasicOperationsService basicOperationsService

    // fixture methods

    def setup() {
    }

    def cleanup() {
    }

    def setupSpec() {
    }

    def cleanupSpec() {
    }

    // feature methods

    void "test simple node checkout"() {
        when:
        Event e = basicOperationsService.newEventTs(new Timestamp(System.currentTimeMillis()), 'TEST', 'test simple node checkout')

        SomeStuff s1 = makeSampleTree()
        SomeStuff s2 = makeSampleTree()

        basicOperationsService.persistNode(e, s2.t.node)

        sessionFactory_nsl.currentSession.clear()

        s1.reloadWithoutClear()
        s2.reloadWithoutClear()


        basicOperationsService.adoptNode(s1.a, s2.a, VersioningMethod.V, seq: 5)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        Node newAA = basicOperationsService.checkoutNode(s1.t.node, s2.aa)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        newAA = Node.get(newAA.id)
        Node newA = DomainUtils.getDraftNodeSupernode(newAA)

        // check that the new nodes have been inserted into the draft tree.

        then:
        newAA
        newA
        newAA != s2.aa
        newA != s2.a

        newA.prev == s2.a
        newAA.prev == s2.aa

        when:
        Link[] l
        l = DomainUtils.getSublinksAsArray(s1.a)

        then:
        l[1].subnode == s1.aa
        l[2].subnode == s1.ab
        l[5].subnode == newA

        when:
        l = DomainUtils.getSublinksAsArray(newA)

        then:
        l[1].subnode == newAA
        l[2].subnode == s2.ab
    }

    void "test a checkout that would result in a draft node attached more than once"() {
        when:
        Event e = basicOperationsService.newEventTs(new Timestamp(System.currentTimeMillis()), 'TEST', 'test a checkout that would result in a draft node attached more than once')
        SomeStuff s1 = makeSampleTree()
        SomeStuff s2 = makeSampleTree()

        basicOperationsService.persistNode(e, s2.t.node)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        basicOperationsService.adoptNode s1.a, s2.a, VersioningMethod.V, seq: 5
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        basicOperationsService.adoptNode s1.b, s2.aa, VersioningMethod.V, seq: 6
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        Node newAA = basicOperationsService.checkoutNode(s1.t.node, s2.aa)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        then:
        thrown ServiceException
    }

    void "test a checkout and undo"() {
        when:
        Event e = basicOperationsService.newEventTs(new Timestamp(System.currentTimeMillis()), 'TEST', 'test a checkout and undo')
        SomeStuff s1 = makeSampleTree()
        SomeStuff s2 = makeSampleTree()
        basicOperationsService.persistNode(e, s2.t.node)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        basicOperationsService.adoptNode s1.a, s2.a, VersioningMethod.V, seq: 5
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        Node newAA = basicOperationsService.checkoutNode(s1.t.node, s2.aa)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        newAA = Node.get(newAA.id)
        Node newA = DomainUtils.getDraftNodeSupernode(newAA)

        then:
        newAA
        newA
        newAA != s2.aa
        newA != s2.a

        newA.prev == s2.a
        newAA.prev == s2.aa

        DomainUtils.getSublinksAsArray(s1.a)[5].subnode == newA

        when:
        basicOperationsService.undoCheckout(newA)
        sessionFactory_nsl.currentSession.clear()
        s1.reloadWithoutClear()
        s2.reloadWithoutClear()

        then:
        DomainUtils.getSublinksAsArray(s1.a)[5].subnode == s2.a
        Node.get(newA.id) == null
        Node.get(newAA.id) == null

    }
}