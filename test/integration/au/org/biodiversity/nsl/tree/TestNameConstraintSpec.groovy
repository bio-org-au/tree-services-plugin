package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Instance
import au.org.biodiversity.nsl.Link
import au.org.biodiversity.nsl.Name
import au.org.biodiversity.nsl.Namespace
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.VersioningMethod
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.sql.DataSource

/**
 * The addition of a database constraint - a name may only be current once in a tree - is causing problems.
 * The other tests dont exercise this because they don't use name ids.
 */
class TestNameConstraintSpec extends Specification {

    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl
    static final Log log = LogFactory.getLog(TestNameConstraintSpec.class)

    // fields
    ClassificationManagerService classificationManagerService
    TreeOperationsService treeOperationsService
    BasicOperationsService basicOperationsService
    VersioningService versioningService
    UserWorkspaceManagerService userWorkspaceManagerService


    def setup() {
    }

    def cleanup() {
    }

    def setupSpec() {
    }

    def cleanupSpec() {
    }

    void "test put new name in apni"() {
       // Node placeNameInAPNI(Namespace namespace, String nameTreeLabel, Name supername, Name name) {


    when:

        /*

2017-01-04 12:17:45.839 [ INFO] grails.app.services.au.org.biodiversity.nsl.NameService:152  - name Name 8305380: Citrullus lanatus var. citroides (L.H.Bailey) Mansf. created.
2017-01-04 12:17:45.841 [ INFO] grails.app.services.au.org.biodiversity.nsl.NameService:152  - Adding Name 8305380: Citrullus lanatus var. citroides (L.H.Bailey) Mansf. to name tree.
2017-01-04 12:17:45.852 [ INFO] grails.app.services.au.org.biodiversity.nsl.NameService:152  - Name 8305380: Citrullus lanatus var. citroides (L.H.Bailey) Mansf. isn't in the tree or it's parent Name 69938: Citrullus lanatus (Thunb.) Matsum. & Nakai has changed. Placing it in the name tree.
2017-01-04 12:17:45.871 [DEBUG] grails.app.services.au.org.biodiversity.nsl.tree.TreeOperationsService:128  - addName(au.org.biodiversity.nsl.Arrangement : 3029293, Name 8305380: Citrullus lanatus var. citroides (L.H.Bailey) Mansf., Name 69938: Citrullus lanatus (Thunb.) Matsum. & Nakai, null, [:], [:])
2017-01-04 12:17:45.911 [DEBUG] grails.app.services.au.org.biodiversity.nsl.tree.TreeOperationsService:128  - checks
2017-01-04 12:17:45.919 [DEBUG] grails.app.services.au.org.biodiversity.nsl.tree.TreeOperationsService:128  - supername
2017-01-04 12:17:45.921 [DEBUG] grails.app.services.au.org.biodiversity.nsl.tree.TreeOperationsService:128  - temp arrangement
2017-01-04 12:17:45.937 [DEBUG] grails.app.services.au.org.biodiversity.nsl.tree.TreeOperationsService:128  - adopt
2017-01-04 12:17:45.956 [DEBUG] grails.app.services.au.org.biodiversity.nsl.tree.TreeOperationsService:128  - checkout
2017-01-04 12:17:45.967

         */

        Name sp_lanatus = Name.findByFullName('Citrullus lanatus (Thunb.) Matsum. & Nakai')
        Name var_citroides = Name.findByFullName('Citrullus lanatus var. citroides (L.H.Bailey) Mansf.')

        then:
        sp_lanatus
        var_citroides

        when:

        Arrangement apni =  Arrangement.get(3029293)

        then:
        apni
        apni.label == 'APNI'

        when: "we try to add a name in the current tree"

        treeOperationsService.addName(
            Arrangement.findByLabel('APNI'),
                DomainUtils.u(treeOperationsService.nslNameNs(), var_citroides.id),
                DomainUtils.u(treeOperationsService.nslNameNs(), sp_lanatus.id),
                (Uri) null
        )


        then: "A service exception is thrown"
            thrown ServiceException
    }

}
