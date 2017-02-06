package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.ArrangementType
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Namespace
import au.org.biodiversity.nsl.NodeInternalType
import au.org.biodiversity.nsl.Node

import javax.sql.DataSource

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
import spock.lang.*

import java.sql.Timestamp

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
        Namespace testNamespace = TreeTestUtil.getTestNamespace()
        Event event = new Event(timeStamp: new Timestamp(System.currentTimeMillis()), authUser: 'fred', namespace: testNamespace)
        event.save()
        Arrangement baseClassification = basicOperationsService.createClassification(event, 'base-classification', 'a description', false)

        long treeid
        sessionFactory_nsl.currentSession.flush()
        sessionFactory_nsl.currentSession.clear()

        treeid = userWorkspaceManagerService.createWorkspace(
                TreeTestUtil.getTestNamespace(),
                baseClassification,
                'TEST',
                false,
                'test workspace',
                '<b>test</b> workspace').id

        sessionFactory_nsl.currentSession.flush()
        sessionFactory_nsl.currentSession.clear()

        Arrangement workspace = Arrangement.get(treeid)

        then:
        workspace
        workspace.arrangementType == ArrangementType.U
        workspace.title == 'test workspace'
        workspace.description

        // workspace node should be a workspace-node

        workspace.node.typeUriIdPart == 'workspace-root'

        // it should have one subnode

        workspace.node.subLink.size() == 1

        when:
        Node wsWorkingRoot = workspace.node

        then:

        //it should have a working root

        wsWorkingRoot

        // which should be a workspace root
        wsWorkingRoot.internalType == NodeInternalType.S
        wsWorkingRoot.typeUriIdPart == 'workspace-root'

        // and it should be a draft node
        !wsWorkingRoot.checkedInAt

        // that is a branch off the root

        wsWorkingRoot.prev == null

        when:
        sessionFactory_nsl.currentSession.flush()
        sessionFactory_nsl.currentSession.clear()

        userWorkspaceManagerService.updateWorkspace(workspace, false, 'renamed workspace', null)

        sessionFactory_nsl.currentSession.flush()
        sessionFactory_nsl.currentSession.clear()

        workspace = Arrangement.get(treeid)

        then:
        workspace
        workspace.title == 'renamed workspace'
        !workspace.description

        when:
        sessionFactory_nsl.currentSession.flush()
        sessionFactory_nsl.currentSession.clear()

        userWorkspaceManagerService.deleteWorkspace(workspace)

        sessionFactory_nsl.currentSession.flush()
        sessionFactory_nsl.currentSession.clear()

        workspace = Arrangement.get(treeid)

        then:
        !workspace
    }
}