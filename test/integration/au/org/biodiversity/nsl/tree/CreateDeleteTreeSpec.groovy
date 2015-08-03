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
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.jdbc.Work
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/**
 *
 */
class CreateDeleteTreeSpec extends Specification {
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl
    static final Log log = LogFactory.getLog(CreateDeleteTreeSpec.class)
    def queryService
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

    void "test creating and deleting a system temp tree"() {
        when:
        long treeid
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        treeid = basicOperationsService.createTemporaryArrangement().id
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        int ct

        doWork { Connection cnct ->
            ResultSet rs = cnct.createStatement().executeQuery("select count(*) from tree_arrangement where id=${treeid}")
            rs.next()
            ct = rs.getInt(1)
            rs.close()
        }

        Arrangement t = Arrangement.get(treeid)

        then:
        ct == 1
        t
        t.arrangementType == ArrangementType.Z
        t.node
        t.node.root == t
        !t.node.prev
        !t.node.next
        t.node.root == t
        t.node.subLink.empty
        t.node.supLink.empty
        t.node.branches.empty
        t.node.merges.empty

        t.node.synthetic
        !DomainUtils.isReplaced(t.node)
        !DomainUtils.isCheckedIn(t.node)
        !DomainUtils.isCurrent(t.node)

        when:
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();
        basicOperationsService.deleteArrangement(Arrangement.get(treeid))
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        doWork { Connection cnct ->
            ResultSet rs = cnct.createStatement().executeQuery("select count(*) from tree_arrangement where id=${treeid}")
            rs.next()
            ct = rs.getInt(1)
            rs.close()
        }

        t = Arrangement.get(treeid)

        then:
        ct == 0
        !t
    }

    void "test using session.clear to reset hibernate"() {
        when:
        long treeid
        treeid = basicOperationsService.createTemporaryArrangement().id
        sessionFactory_nsl.currentSession.clear();

        int ct

        doWork { Connection cnct ->
            ResultSet rs = cnct.createStatement().executeQuery("select count(*) from tree_arrangement where id=${treeid}")
            rs.next()
            ct = rs.getInt(1)
            rs.close()
        }

        then:
        ct == 1

        when:
        Arrangement t = Arrangement.get(treeid)

        then:
        t

        when:
        basicOperationsService.deleteArrangement(Arrangement.get(treeid))
        sessionFactory_nsl.currentSession.clear();

        doWork { Connection cnct ->
            ResultSet rs = cnct.createStatement().executeQuery("select count(*) from tree_arrangement where id=${treeid}")
            rs.next()
            ct = rs.getInt(1)
            rs.close()
        }


        then:
        ct == 0

        when:
        sessionFactory_nsl.currentSession.clear();
        t = Arrangement.get(treeid)

        then:
        !t
    }

    void "test that refresh fails"() {
        when:
        long treeid
        treeid = basicOperationsService.createTemporaryArrangement().id
        sessionFactory_nsl.currentSession.clear();

        int ct

        doWork { Connection cnct ->
            ResultSet rs = cnct.createStatement().executeQuery("select count(*) from tree_arrangement where id=${treeid}")
            rs.next()
            ct = rs.getInt(1)
            rs.close()
        }

        then:
        ct == 1

        when:
        Arrangement t = Arrangement.get(treeid)

        then:
        t

        when:
        basicOperationsService.deleteArrangement(Arrangement.get(treeid))

        t.refresh()

        then:
        thrown org.springframework.dao.DataAccessException
    }

    void "test using session.evict to reset hibernate"() {
        when:
        long treeid
        treeid = basicOperationsService.createTemporaryArrangement().id
        sessionFactory_nsl.currentSession.clear();

        Arrangement t = Arrangement.get(treeid)

        then:
        t

        when:
        basicOperationsService.deleteArrangement(Arrangement.get(treeid))
        sessionFactory_nsl.currentSession.evict(t)
        t = Arrangement.get(treeid)

        then:
        !t
    }

    void "test create and delete an arrangement with subnodes"() {
        when:
        Arrangement t = basicOperationsService.createTemporaryArrangement()

        then:
        t.id
        t.node.id
        t.node.supLink.empty
        t.node.subLink.empty
        t.node.version == 0

        when:
        Node node1 = basicOperationsService.createDraftNode(t.node, VersioningMethod.V, NodeInternalType.T, seq: 1)
        t = DomainUtils.refetchArrangement(t)

        then:
        node1.id
        t.node.supLink.empty
        t.node.subLink.size() == 1
        node1.supLink.size() == 1
        node1.subLink.empty
        node1.version == 0

        first(t.node.subLink) == first(node1.supLink)
        t.node.version == 1

        when:
        Node node2 = basicOperationsService.createDraftNode node1, VersioningMethod.V, NodeInternalType.T, seq: 1
        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)

        then:
        node1.version == 1
        node2.id
        node1.supLink.size() == 1
        node1.subLink.size() == 1
        node2.supLink.size() == 1
        node2.subLink.empty

        first(node1.subLink) == first(node2.supLink)
        t.node.version == 1

        when:
        basicOperationsService.deleteArrangement(t)
        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)

        then:
        !t
        !node1
        !node2

    }

    void "test delete single node"() {
        when:
        Arrangement t = basicOperationsService.createTemporaryArrangement()
        Node node1 = basicOperationsService.createDraftNode t.node, VersioningMethod.V, NodeInternalType.T, seq: 1
        Node node2 = basicOperationsService.createDraftNode node1, VersioningMethod.V, NodeInternalType.T, seq: 1

        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)

        then:
        t.id
        node1.id
        node2.id
        t.node.supLink.size() == 0
        t.node.subLink.size() == 1
        node1.supLink.size() == 1
        node1.subLink.size() == 1
        node2.supLink.size() == 1
        node2.subLink.size() == 0

        when:
        basicOperationsService.deleteDraftNode(node2)
        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)

        then:
        !node2
        node1
        node1.subLink.size() == 0

        cleanup:
        basicOperationsService.deleteArrangement(t)
    }


    void "test delete single node with a subnode"() {
        when:
        Arrangement t = basicOperationsService.createTemporaryArrangement()
        Node node1 = basicOperationsService.createDraftNode t.node, VersioningMethod.V, NodeInternalType.T, seq: 1, foo: 'bar'
        Node node2 = basicOperationsService.createDraftNode node1, VersioningMethod.V, NodeInternalType.T, seq: 1, foo: 'bar'
        Node node3 = basicOperationsService.createDraftNode node2, VersioningMethod.V, NodeInternalType.T, seq: 1, foo: 'bar'

        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)
        node3 = DomainUtils.refetchNode(node3)

        then:
        t.id
        node1.id
        node2.id
        node3.id
        t.node.supLink.empty
        t.node.subLink.size() == 1
        node1.supLink.size() == 1
        node1.subLink.size() == 1
        node2.supLink.size() == 1
        node2.subLink.size() == 1
        node3.supLink.size() == 1
        node3.subLink.empty

        when:
        basicOperationsService.deleteDraftNode(node2)
        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)
        node3 = DomainUtils.refetchNode(node3)

        then:
        thrown ServiceException

        cleanup:
        basicOperationsService.deleteArrangement(t)
    }


    void "test delete node subtree"() {
        when:
        Arrangement t = basicOperationsService.createTemporaryArrangement()
        Node node1 = basicOperationsService.createDraftNode t.node, VersioningMethod.V, NodeInternalType.T, seq: 1
        Node node2 = basicOperationsService.createDraftNode node1, VersioningMethod.V, NodeInternalType.T, seq: 1
        Node node3 = basicOperationsService.createDraftNode node2, VersioningMethod.V, NodeInternalType.T, seq: 1

        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)
        node3 = DomainUtils.refetchNode(node3)

        then:
        t.id
        node1.id
        node2.id
        node3.id
        t.node.supLink.empty
        t.node.subLink.size() == 1
        node1.supLink.size() == 1
        node1.subLink.size() == 1
        node2.supLink.size() == 1
        node2.subLink.size() == 1
        node3.supLink.size() == 1
        node3.subLink.empty

        when:
        basicOperationsService.deleteDraftTree(node2)
        t = DomainUtils.refetchArrangement(t)
        node1 = DomainUtils.refetchNode(node1)
        node2 = DomainUtils.refetchNode(node2)
        node3 = DomainUtils.refetchNode(node3)

        then:
        node1.subLink.size() == 0
        !node2
        !node3

        cleanup:
        basicOperationsService.deleteArrangement(t)
    }

    // helper methods

    static <T> T first(Collection<T> s) {
        if (s.empty) {
            return null
        }
        return s.iterator().next()
    }

    private static Object doWork(final Closure c) throws SQLException {
        Node.withSession { Session s ->
            s.flush()
            TestWorkResultHolder w = new TestWorkResultHolder(c)
            s.doWork(w)
            return w.result
        }
    }

    private static Object withQ(Connection cnct, String sql, Closure c) throws SQLException {
        PreparedStatement stmt = cnct.prepareStatement(sql)
        try {
            return c(stmt)
        }
        finally {
            stmt.close()
        }
    }

}

class TestWorkResultHolder implements Work {
    Object result = null
    Closure exec

    TestWorkResultHolder(Closure exec) {
        this.exec = exec
    }

    public void execute(Connection connection) throws SQLException {
        result = exec(connection)
    }

}
