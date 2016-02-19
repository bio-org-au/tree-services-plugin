package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.ArrangementType
import au.org.biodiversity.nsl.NodeInternalType

import javax.sql.DataSource

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
import au.org.biodiversity.nsl.Event;
import au.org.biodiversity.nsl.Link;
import au.org.biodiversity.nsl.VersioningMethod;
import spock.lang.*

import java.sql.Connection
import java.sql.ResultSet

@Mixin(BuildSampleTreeMixin)
class CreateDeleteWorkspaceSpec extends Specification {
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl
    static final Log log = LogFactory.getLog(AdoptNodeSpec.class)

    // fields
    BasicOperationsService basicOperationsService
    UserWorkspaceManagerService userWorkspaceManagerService

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

    def "test CRUD workspace"() {
        when:
        long treeid
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        treeid = userWorkspaceManagerService.createWorkspace(TreeTestUtil.getTestNamespace(), 'TEST', 'test workspace', '<b>test</b> workspace').id

        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        Arrangement t = Arrangement.get(treeid)

        then:
        t
        t.arrangementType == ArrangementType.U
        t.title == 'test workspace'
        t.description

        // workspace node should be a workspace-node

        t.node.typeUriIdPart == 'workspace-node'

        // it should have one subnode

        t.node.subLink.size() == 1
        t.node.subLink.first().versioningMethod == VersioningMethod.T
        t.node.subLink.first().typeUriIdPart == 'workspace-root-link'

        // which should be a workspace root
        t.node.subLink.first().subnode.internalType == NodeInternalType.S
        t.node.subLink.first().subnode.typeUriIdPart == 'workspace-root'

        // and it shouold be a draft node

        !t.node.subLink.first().subnode.checkedInAt

        when:
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        userWorkspaceManagerService.updateWorkspace(t, 'renamed workspace', null)

        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        t = Arrangement.get(treeid)

        then:
        t
        t.title == 'renamed workspace'
        !t.description

        when:
        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        userWorkspaceManagerService.deleteWorkspace(t)

        sessionFactory_nsl.currentSession.flush();
        sessionFactory_nsl.currentSession.clear();

        t = Arrangement.get(treeid)

        then:
        !t
    }
}