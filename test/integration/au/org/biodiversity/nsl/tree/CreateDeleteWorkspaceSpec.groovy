package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.ArrangementType
import au.org.biodiversity.nsl.NodeInternalType
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.Event;
import au.org.biodiversity.nsl.Link;

import javax.sql.DataSource

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
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

        when:
        Link wsRootLink = t.node.subLink.first()
        Node wsRoot = wsRootLink.subnode
        Node wsWorkingRoot = t.workingRoot

        then:

        wsRootLink.versioningMethod == VersioningMethod.T
        wsRootLink.typeUriIdPart == 'workspace-root-link'

        // which should be a workspace root
        wsRoot.internalType == NodeInternalType.S
        wsRoot.typeUriIdPart == 'workspace-root'

        // and it should not be a draft node
        wsRoot.checkedInAt

        //it should have a working root

        wsWorkingRoot

        // which should be a workspace root
        wsWorkingRoot.internalType == NodeInternalType.S
        wsWorkingRoot.typeUriIdPart == 'workspace-root'

        // and it should be a draft node
        !wsWorkingRoot.checkedInAt

        // that is a branch off the root

        wsWorkingRoot.prev == wsRoot

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