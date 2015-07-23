package au.org.biodiversity.nsl.tree

import javax.sql.DataSource

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory

import spock.lang.*
import au.org.biodiversity.nsl.Arrangement

@Mixin(BuildSampleTreeMixin)
class DomainSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(DomainSpec.class)


	// fields

	// fixture methods

	def setup() {
	}

	def cleanup() {
	}

	def setupSpec() {
	}

	def cleanupSpec() {
	}

	def 'test end tree exists'() {
		when:
		Arrangement a = DomainUtils.getEndTree()

		then:
		a != null
		a.id == 0

	}

	def 'test end tree is end tree'() {
		when:
		Arrangement a = DomainUtils.getEndTree()

		then:
		DomainUtils.isEndTree(a)

	}

	def 'test TMP tree is not end tree'() {
		when:
		Arrangement a = Arrangement.findByLabel('TMP')

		then:
		!DomainUtils.isEndTree(a)

	}

}
